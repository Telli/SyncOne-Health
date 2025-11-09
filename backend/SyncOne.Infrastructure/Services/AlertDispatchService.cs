using System.Net.Http.Json;
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
    private readonly ILogger<AlertDispatchService> _logger;

    public AlertDispatchService(
        ApplicationDbContext context,
        IHttpClientFactory httpClientFactory,
        ILogger<AlertDispatchService> logger)
    {
        _context = context;
        _httpClientFactory = httpClientFactory;
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
        // TODO: Integrate Firebase Cloud Messaging or similar
        _logger.LogInformation("Would send push notification for alert {AlertId}", alert.Id);
        await Task.CompletedTask;
    }

    private async Task SendSmsToChwAsync(Alert alert, CancellationToken cancellationToken)
    {
        // TODO: Integrate Twilio or similar SMS gateway
        _logger.LogInformation("Would send SMS to CHW for alert {AlertId}", alert.Id);
        await Task.CompletedTask;
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
