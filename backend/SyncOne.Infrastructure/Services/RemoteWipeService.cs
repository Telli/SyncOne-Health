using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;

namespace SyncOne.Infrastructure.Services;

/// <summary>
/// Handles remote wipe commands for lost/stolen devices
/// </summary>
public class RemoteWipeService
{
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly IConfiguration _config;
    private readonly ILogger<RemoteWipeService> _logger;

    public RemoteWipeService(
        IHttpClientFactory httpClientFactory,
        IConfiguration config,
        ILogger<RemoteWipeService> logger)
    {
        _httpClientFactory = httpClientFactory;
        _config = config;
        _logger = logger;
    }

    public async Task InitiateWipeAsync(
        string deviceId,
        string adminId,
        string reason,
        CancellationToken cancellationToken = default)
    {
        try
        {
            _logger.LogWarning(
                "REMOTE WIPE INITIATED: Device {DeviceId}, Admin: {AdminId}, Reason: {Reason}",
                deviceId,
                adminId,
                reason);

            // Send wipe command via FCM (Firebase Cloud Messaging)
            var client = _httpClientFactory.CreateClient("FCM");

            var message = new
            {
                to = $"/topics/device_{deviceId}",
                data = new
                {
                    action = "REMOTE_WIPE",
                    auth_token = GenerateWipeToken(deviceId, adminId),
                    timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
                }
            };

            var response = await client.PostAsJsonAsync(
                "/fcm/send",
                message,
                cancellationToken);

            response.EnsureSuccessStatusCode();

            _logger.LogInformation("Wipe command sent successfully to device {DeviceId}", deviceId);

            // TODO: Store in audit log database
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to initiate remote wipe for device {DeviceId}", deviceId);
            throw;
        }
    }

    private string GenerateWipeToken(string deviceId, string adminId)
    {
        var secret = _config["RemoteWipe:Secret"]
            ?? throw new InvalidOperationException("RemoteWipe secret not configured");

        var payload = $"{deviceId}:{adminId}:{DateTimeOffset.UtcNow.ToUnixTimeSeconds()}";

        using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(secret));
        var hash = hmac.ComputeHash(Encoding.UTF8.GetBytes(payload));
        return Convert.ToBase64String(hash);
    }
}
