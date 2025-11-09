using System.Net.Http.Json;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using SyncOne.Core.Services;
using SyncOne.Infrastructure.Data;
using SyncOne.Infrastructure.Data.Entities;

namespace SyncOne.Infrastructure.Services;

/// <summary>
/// Dispatches alerts to CHWs for critical cases
/// Supports SMS, push notifications, and webhooks
/// </summary>
public class AlertDispatchService : IAlertService
{
    private readonly ApplicationDbContext _context;
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly IConfiguration _config;
    private readonly TwilioSmsService? _twilioService;
    private readonly FirebaseCloudMessagingService? _fcmService;
    private readonly ILogger<AlertDispatchService> _logger;

    public AlertDispatchService(
        ApplicationDbContext context,
        IHttpClientFactory httpClientFactory,
        IConfiguration config,
        ILogger<AlertDispatchService> logger,
        TwilioSmsService? twilioService = null,
        FirebaseCloudMessagingService? fcmService = null)
    {
        _context = context;
        _httpClientFactory = httpClientFactory;
        _config = config;
        _twilioService = twilioService;
        _fcmService = fcmService;
        _logger = logger;
    }

    public async Task DispatchAlertAsync(
        string? phoneNumber,
        string message,
        string urgency,
        CancellationToken cancellationToken = default)
    {
        try
        {
            // 1. Store alert in database
            var alert = new Alert
            {
                Id = Guid.NewGuid(),
                PhoneNumber = phoneNumber,
                Message = message,
                Urgency = urgency,
                Status = "PENDING",
                CreatedAt = DateTime.UtcNow
            };

            _context.Alerts.Add(alert);
            await _context.SaveChangesAsync(cancellationToken);

            _logger.LogWarning(
                "ALERT DISPATCHED: {Urgency} - Phone: {Phone}, Message: {Message}",
                urgency,
                phoneNumber ?? "Unknown",
                message);

            // 2. Send push notification to CHW app
            await SendPushNotificationAsync(alert, cancellationToken);

            // 3. Send SMS to assigned CHW
            await SendSmsToChwAsync(alert, cancellationToken);

            // 4. Call webhook if configured
            await CallWebhookAsync(alert, cancellationToken);

            // 5. Update alert status
            alert.Status = "DISPATCHED";
            await _context.SaveChangesAsync(cancellationToken);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to dispatch alert");
            throw;
        }
    }

    private async Task SendPushNotificationAsync(Alert alert, CancellationToken cancellationToken)
    {
        if (_fcmService == null)
        {
            _logger.LogWarning("FCM service not configured, skipping push notification for alert {AlertId}", alert.Id);
            return;
        }

        // Get CHW device token from configuration (in production, fetch from database)
        var chwDeviceToken = _config["Alerts:CHW:DeviceToken"];
        if (string.IsNullOrEmpty(chwDeviceToken))
        {
            _logger.LogWarning("CHW device token not configured, skipping push notification");
            return;
        }

        await _fcmService.SendAlertNotificationAsync(
            chwDeviceToken,
            alert.Urgency,
            alert.PhoneNumber ?? "Unknown",
            alert.Message,
            alert.Id.ToString(),
            cancellationToken);
    }

    private async Task SendSmsToChwAsync(Alert alert, CancellationToken cancellationToken)
    {
        if (_twilioService == null)
        {
            _logger.LogWarning("Twilio service not configured, skipping SMS for alert {AlertId}", alert.Id);
            return;
        }

        // Get CHW phone number from configuration (in production, fetch from database)
        var chwPhoneNumber = _config["Alerts:CHW:PhoneNumber"];
        if (string.IsNullOrEmpty(chwPhoneNumber))
        {
            _logger.LogWarning("CHW phone number not configured, skipping SMS");
            return;
        }

        await _twilioService.SendAlertSmsAsync(
            chwPhoneNumber,
            alert.PhoneNumber ?? "Unknown",
            alert.Urgency,
            alert.Message,
            cancellationToken);
    }

    private async Task CallWebhookAsync(Alert alert, CancellationToken cancellationToken)
    {
        var webhookUrl = Environment.GetEnvironmentVariable("ALERT_WEBHOOK_URL");
        if (string.IsNullOrEmpty(webhookUrl))
            return;

        var client = _httpClientFactory.CreateClient();
        var payload = new
        {
            alert_id = alert.Id,
            phone_number = alert.PhoneNumber,
            message = alert.Message,
            urgency = alert.Urgency,
            timestamp = alert.CreatedAt
        };

        await client.PostAsJsonAsync(webhookUrl, payload, cancellationToken);
    }
}
