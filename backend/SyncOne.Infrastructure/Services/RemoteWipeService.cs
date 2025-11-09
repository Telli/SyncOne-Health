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

        var timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        var payload = $"{deviceId}:{adminId}:{timestamp}";

        using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(secret));
        var hash = hmac.ComputeHash(Encoding.UTF8.GetBytes(payload));
        var signature = Convert.ToBase64String(hash);

        // Return token in format: base64(payload):base64(signature)
        // This allows the device to extract and validate the timestamp
        var payloadBase64 = Convert.ToBase64String(Encoding.UTF8.GetBytes(payload));
        return $"{payloadBase64}:{signature}";
    }

    /// <summary>
    /// Validates a remote wipe token for authenticity and expiration
    /// Should be called by the device receiving the wipe command
    /// </summary>
    public bool ValidateWipeToken(string token, string deviceId, string adminId)
    {
        try
        {
            var secret = _config["RemoteWipe:Secret"]
                ?? throw new InvalidOperationException("RemoteWipe secret not configured");

            // Token format: base64(payload):base64(signature)
            var parts = token.Split(':');
            if (parts.Length != 2)
            {
                _logger.LogWarning("Invalid token format");
                return false;
            }

            var payloadBase64 = parts[0];
            var providedSignature = parts[1];

            // Decode payload
            var payload = Encoding.UTF8.GetString(Convert.FromBase64String(payloadBase64));
            var payloadParts = payload.Split(':');
            
            if (payloadParts.Length != 3)
            {
                _logger.LogWarning("Invalid payload format");
                return false;
            }

            var tokenDeviceId = payloadParts[0];
            var tokenAdminId = payloadParts[1];
            var tokenTimestamp = long.Parse(payloadParts[2]);

            // Validate device ID and admin ID match
            if (tokenDeviceId != deviceId || tokenAdminId != adminId)
            {
                _logger.LogWarning("Device ID or Admin ID mismatch in token");
                return false;
            }

            // Verify HMAC signature
            using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(secret));
            var expectedHash = hmac.ComputeHash(Encoding.UTF8.GetBytes(payload));
            var expectedSignature = Convert.ToBase64String(expectedHash);

            if (providedSignature != expectedSignature)
            {
                _logger.LogWarning("Token signature verification failed");
                return false;
            }

            // Check token expiration (default: 5 minutes)
            var expirationMinutes = _config.GetValue<int>("RemoteWipe:TokenExpirationMinutes", 5);
            var tokenAge = DateTimeOffset.UtcNow.ToUnixTimeSeconds() - tokenTimestamp;
            
            if (tokenAge > expirationMinutes * 60)
            {
                _logger.LogWarning("Token expired. Age: {TokenAge} seconds", tokenAge);
                return false;
            }

            if (tokenAge < 0)
            {
                _logger.LogWarning("Token timestamp is in the future");
                return false;
            }

            _logger.LogInformation("Token validated successfully for device {DeviceId}", deviceId);
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error validating wipe token");
            return false;
        }
    }
}
