using System.Net.Http.Json;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;

namespace SyncOne.Infrastructure.Services;

/// <summary>
/// Firebase Cloud Messaging service for sending push notifications to CHW app
/// Configure in appsettings.json:
/// "FCM": {
///   "ServerKey": "your-fcm-server-key",
///   "SenderID": "your-sender-id"
/// }
/// </summary>
public class FirebaseCloudMessagingService
{
    private readonly HttpClient _httpClient;
    private readonly IConfiguration _config;
    private readonly ILogger<FirebaseCloudMessagingService> _logger;
    private readonly string _serverKey;

    public FirebaseCloudMessagingService(
        IHttpClientFactory httpClientFactory,
        IConfiguration config,
        ILogger<FirebaseCloudMessagingService> logger)
    {
        _httpClient = httpClientFactory.CreateClient("FCM");
        _config = config;
        _logger = logger;

        _serverKey = _config["FCM:ServerKey"]
            ?? throw new InvalidOperationException("FCM ServerKey not configured");

        _httpClient.BaseAddress = new Uri("https://fcm.googleapis.com/");
        _httpClient.DefaultRequestHeaders.Add("Authorization", $"key={_serverKey}");
    }

    public async Task<bool> SendPushNotificationAsync(
        string deviceToken,
        string title,
        string body,
        Dictionary<string, string>? data = null,
        CancellationToken cancellationToken = default)
    {
        try
        {
            var message = new
            {
                to = deviceToken,
                notification = new
                {
                    title,
                    body,
                    sound = "default",
                    priority = "high"
                },
                data = data ?? new Dictionary<string, string>(),
                priority = "high"
            };

            var response = await _httpClient.PostAsJsonAsync(
                "fcm/send",
                message,
                cancellationToken);

            if (response.IsSuccessStatusCode)
            {
                _logger.LogInformation("Push notification sent successfully to device {DeviceToken}", deviceToken);
                return true;
            }

            var errorContent = await response.Content.ReadAsStringAsync(cancellationToken);
            _logger.LogError(
                "Failed to send push notification: {StatusCode} - {Error}",
                response.StatusCode,
                errorContent);
            return false;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error sending push notification");
            return false;
        }
    }

    public async Task<bool> SendTopicNotificationAsync(
        string topic,
        string title,
        string body,
        Dictionary<string, string>? data = null,
        CancellationToken cancellationToken = default)
    {
        try
        {
            var message = new
            {
                to = $"/topics/{topic}",
                notification = new
                {
                    title,
                    body,
                    sound = "default",
                    priority = "high"
                },
                data = data ?? new Dictionary<string, string>(),
                priority = "high"
            };

            var response = await _httpClient.PostAsJsonAsync(
                "fcm/send",
                message,
                cancellationToken);

            if (response.IsSuccessStatusCode)
            {
                _logger.LogInformation("Topic notification sent successfully to topic {Topic}", topic);
                return true;
            }

            var errorContent = await response.Content.ReadAsStringAsync(cancellationToken);
            _logger.LogError(
                "Failed to send topic notification: {StatusCode} - {Error}",
                response.StatusCode,
                errorContent);
            return false;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error sending topic notification to {Topic}", topic);
            return false;
        }
    }

    public async Task<bool> SendAlertNotificationAsync(
        string deviceToken,
        string urgency,
        string patientPhone,
        string message,
        string alertId,
        CancellationToken cancellationToken = default)
    {
        var title = $"{urgency} ALERT";
        var body = $"Patient {patientPhone}: {message}";

        var data = new Dictionary<string, string>
        {
            { "alert_id", alertId },
            { "urgency", urgency },
            { "patient_phone", patientPhone },
            { "message", message },
            { "timestamp", DateTimeOffset.UtcNow.ToUnixTimeMilliseconds().ToString() },
            { "action", "ALERT" }
        };

        return await SendPushNotificationAsync(deviceToken, title, body, data, cancellationToken);
    }
}
