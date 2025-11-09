using Microsoft.Extensions.Logging;
using SyncOne.Core.Models;
using SyncOne.Core.Services;

namespace SyncOne.Core.Agents;

/// <summary>
/// Handles facility referrals and emergency escalations
/// </summary>
public class ReferralAgent : IAgent
{
    private readonly IMedGemmaService _medgemma;
    private readonly IAlertService _alertService;
    private readonly ILogger<ReferralAgent> _logger;

    public string Name => "Referral";
    public string Description => "Healthcare facility referrals and emergency triage";

    public ReferralAgent(
        IMedGemmaService medgemma,
        IAlertService alertService,
        ILogger<ReferralAgent> logger)
    {
        _medgemma = medgemma;
        _alertService = alertService;
        _logger = logger;
    }

    public async Task<AgentResponse> InvokeAsync(
        InferenceRequest request,
        CancellationToken cancellationToken = default)
    {
        // Determine urgency level
        var urgency = DetermineUrgency(request.Message);

        // Dispatch alert if critical
        if (urgency == "CRITICAL")
        {
            await _alertService.DispatchAlertAsync(
                phoneNumber: request.Metadata.PhoneNumber,
                message: request.Message,
                urgency: urgency,
                cancellationToken: cancellationToken);
        }

        var response = BuildReferralResponse(urgency, request.Message);

        return new AgentResponse(
            Message: response,
            Confidence: 1.0f, // High confidence for referrals
            Sources: new List<string> { "WHO Emergency Triage" },
            Metadata: new Dictionary<string, object>
            {
                ["agent"] = Name,
                ["urgency"] = urgency,
                ["alert_dispatched"] = urgency == "CRITICAL"
            }
        );
    }

    private string DetermineUrgency(string message)
    {
        var critical = new[] {
            "unconscious", "not breathing", "severe bleeding",
            "chest pain", "stroke", "seizure"
        };

        var urgent = new[] {
            "high fever", "broken bone", "severe pain",
            "difficulty breathing", "burns"
        };

        if (critical.Any(k => message.Contains(k, StringComparison.OrdinalIgnoreCase)))
            return "CRITICAL";

        if (urgent.Any(k => message.Contains(k, StringComparison.OrdinalIgnoreCase)))
            return "URGENT";

        return "ROUTINE";
    }

    private string BuildReferralResponse(string urgency, string message)
    {
        return urgency switch
        {
            "CRITICAL" => "🚨 EMERGENCY: Go to the nearest hospital IMMEDIATELY. Call emergency services if available. Do not wait.",
            "URGENT" => "⚠️ Seek medical care within 24 hours at the nearest clinic or hospital. Monitor symptoms closely.",
            _ => "Schedule an appointment with a healthcare provider to discuss your concerns. Track your symptoms in the meantime."
        };
    }
}
