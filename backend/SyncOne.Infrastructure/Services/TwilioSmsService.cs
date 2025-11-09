using System.Net.Http.Json;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;

namespace SyncOne.Infrastructure.Services;

/// <summary>
/// Twilio SMS service for sending alerts to CHWs
/// Configure in appsettings.json:
/// "Twilio": {
///   "AccountSid": "your-account-sid",
///   "AuthToken": "your-auth-token",
///   "FromPhoneNumber": "+1234567890"
/// }
/// </summary>
public class TwilioSmsService
{
    private readonly HttpClient _httpClient;
    private readonly IConfiguration _config;
    private readonly ILogger<TwilioSmsService> _logger;
    private readonly string _accountSid;
    private readonly string _authToken;
    private readonly string _fromPhoneNumber;

    public TwilioSmsService(
        IHttpClientFactory httpClientFactory,
        IConfiguration config,
        ILogger<TwilioSmsService> logger)
    {
        _httpClient = httpClientFactory.CreateClient("Twilio");
        _config = config;
        _logger = logger;

        _accountSid = _config["Twilio:AccountSid"]
            ?? throw new InvalidOperationException("Twilio AccountSid not configured");
        _authToken = _config["Twilio:AuthToken"]
            ?? throw new InvalidOperationException("Twilio AuthToken not configured");
        _fromPhoneNumber = _config["Twilio:FromPhoneNumber"]
            ?? throw new InvalidOperationException("Twilio FromPhoneNumber not configured");

        // Configure HTTP client with Basic Auth
        var credentials = Convert.ToBase64String(
            System.Text.Encoding.ASCII.GetBytes($"{_accountSid}:{_authToken}"));
        _httpClient.DefaultRequestHeaders.Authorization =
            new System.Net.Http.Headers.AuthenticationHeaderValue("Basic", credentials);
        _httpClient.BaseAddress = new Uri($"https://api.twilio.com/2010-04-01/Accounts/{_accountSid}/");
    }

    public async Task<bool> SendSmsAsync(
        string toPhoneNumber,
        string message,
        CancellationToken cancellationToken = default)
    {
        try
        {
            var formData = new Dictionary<string, string>
            {
                { "To", toPhoneNumber },
                { "From", _fromPhoneNumber },
                { "Body", message }
            };

            var response = await _httpClient.PostAsync(
                "Messages.json",
                new FormUrlEncodedContent(formData),
                cancellationToken);

            if (response.IsSuccessStatusCode)
            {
                _logger.LogInformation(
                    "SMS sent successfully to {PhoneNumber} via Twilio",
                    toPhoneNumber);
                return true;
            }

            var errorContent = await response.Content.ReadAsStringAsync(cancellationToken);
            _logger.LogError(
                "Failed to send SMS via Twilio: {StatusCode} - {Error}",
                response.StatusCode,
                errorContent);
            return false;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error sending SMS via Twilio to {PhoneNumber}", toPhoneNumber);
            return false;
        }
    }

    public async Task<bool> SendAlertSmsAsync(
        string toPhoneNumber,
        string patientPhone,
        string urgency,
        string message,
        CancellationToken cancellationToken = default)
    {
        var alertMessage = $"[{urgency}] ALERT from patient {patientPhone}\n\n{message}\n\nPlease respond immediately.";

        // Truncate if too long (Twilio max is 1600 chars)
        if (alertMessage.Length > 1600)
        {
            alertMessage = alertMessage.Substring(0, 1597) + "...";
        }

        return await SendSmsAsync(toPhoneNumber, alertMessage, cancellationToken);
    }
}
