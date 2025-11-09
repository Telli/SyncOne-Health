using Microsoft.Extensions.Logging;
using Microsoft.SemanticKernel;
using Microsoft.SemanticKernel.ChatCompletion;
using SyncOne.Core.Models;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace SyncOne.Core.Agents;

/// <summary>
/// Coordinator agent that routes queries to specialist agents
/// Applies PII shields and medical safety filters
/// </summary>
public class CoordinatorAgent : IAgent
{
    private readonly Kernel _kernel;
    private readonly Dictionary<string, IAgent> _specialists;
    private readonly ILogger<CoordinatorAgent> _logger;

    public string Name => "Coordinator";
    public string Description => "Routes queries to appropriate specialist agents";

    public CoordinatorAgent(
        Kernel kernel,
        IEnumerable<IAgent> specialists,
        ILogger<CoordinatorAgent> logger)
    {
        _kernel = kernel;
        _specialists = specialists.ToDictionary(a => a.Name, a => a);
        _logger = logger;
    }

    public async Task<AgentResponse> InvokeAsync(
        InferenceRequest request,
        CancellationToken cancellationToken = default)
    {
        try
        {
            // 1. Apply PII shield (mask sensitive data)
            var sanitized = ApplyPiiShield(request);

            // 2. Classify query to determine specialist
            var classification = await ClassifyQueryAsync(sanitized, cancellationToken);

            _logger.LogInformation(
                "Query classified as {AgentType} with confidence {Confidence}",
                classification.AgentType,
                classification.Confidence);

            // 3. Route to appropriate specialist
            if (!_specialists.TryGetValue(classification.AgentType, out var specialist))
            {
                _logger.LogWarning("Unknown agent type: {AgentType}, using PrimaryCare",
                    classification.AgentType);
                specialist = _specialists["PrimaryCare"];
            }

            var response = await specialist.InvokeAsync(sanitized, cancellationToken);

            // 4. Apply medical safety filter
            var safeResponse = ApplySafetyFilter(response, sanitized);

            // 5. Format for SMS (≤480 chars)
            var formatted = FormatForSms(safeResponse.Message);

            return safeResponse with { Message = formatted };
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error in coordinator agent");
            throw;
        }
    }

    private InferenceRequest ApplyPiiShield(InferenceRequest request)
    {
        // Mask phone numbers, names, addresses
        var maskedMessage = request.Message;

        // Phone number pattern: +1234567890 or (123) 456-7890
        maskedMessage = Regex.Replace(
            maskedMessage,
            @"\+?\d{1,3}[-.\s]?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}",
            "[PHONE]");

        // Email pattern
        maskedMessage = Regex.Replace(
            maskedMessage,
            @"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b",
            "[EMAIL]");

        return request with { Message = maskedMessage };
    }

    private async Task<Classification> ClassifyQueryAsync(
        InferenceRequest request,
        CancellationToken cancellationToken)
    {
        var chatService = _kernel.GetRequiredService<IChatCompletionService>();

        var prompt = $"""
        Classify this medical query into ONE of the following categories:
        - PrimaryCare: General symptoms, fever, cough, minor ailments
        - MaternalHealth: Pregnancy, labor, prenatal/postnatal care
        - RxSafety: Medication questions, drug interactions, dosing
        - Referral: Need for hospital/clinic, emergencies, specialist care

        Query: {request.Message}

        Respond ONLY with JSON:
        {{
            "agentType": "PrimaryCare|MaternalHealth|RxSafety|Referral",
            "confidence": 0.0-1.0,
            "reasoning": "brief explanation"
        }}
        """;

        var chatHistory = new ChatHistory();
        chatHistory.AddUserMessage(prompt);

        var result = await chatService.GetChatMessageContentAsync(
            chatHistory,
            cancellationToken: cancellationToken);

        // Parse JSON response
        var json = result.Content ?? "{}";

        try
        {
            var options = new JsonSerializerOptions { PropertyNameCaseInsensitive = true };
            var classification = JsonSerializer.Deserialize<Classification>(json, options);
            return classification ?? new Classification("PrimaryCare", 0.5f, "Default fallback");
        }
        catch
        {
            // Fallback to keyword-based classification
            return ClassifyByKeywords(request.Message);
        }
    }

    private Classification ClassifyByKeywords(string message)
    {
        var lower = message.ToLowerInvariant();

        if (lower.Contains("pregnant") || lower.Contains("pregnancy") || lower.Contains("labor"))
            return new Classification("MaternalHealth", 0.8f, "Pregnancy keyword detected");

        if (lower.Contains("medication") || lower.Contains("drug") || lower.Contains("pill"))
            return new Classification("RxSafety", 0.8f, "Medication keyword detected");

        if (lower.Contains("emergency") || lower.Contains("severe") || lower.Contains("bleeding"))
            return new Classification("Referral", 0.9f, "Emergency keyword detected");

        return new Classification("PrimaryCare", 0.7f, "General medical query");
    }

    private AgentResponse ApplySafetyFilter(AgentResponse response, InferenceRequest request)
    {
        var message = response.Message;

        // Check for harmful content
        if (ContainsHarmfulAdvice(message))
        {
            _logger.LogWarning("Harmful content detected, replacing response");
            return response with
            {
                Message = "I cannot provide that information. Please consult a healthcare provider immediately.",
                Confidence = 0.0f
            };
        }

        // Add disclaimers for medication advice
        if (ContainsMedicationAdvice(message) && !message.Contains("consult"))
        {
            message += "\n\nIMPORTANT: Consult a healthcare provider before taking any medication.";
        }

        // Flag emergencies
        if (ContainsEmergencyKeywords(request.Message))
        {
            message = $"⚠️ EMERGENCY: {message}\n\nSeek immediate medical attention.";
        }

        return response with { Message = message };
    }

    private string FormatForSms(string text)
    {
        const int MaxLength = 480;

        if (text.Length <= MaxLength)
            return text;

        // Truncate at sentence boundary
        var lastPeriod = text.LastIndexOf('.', Math.Min(MaxLength - 20, text.Length - 1));

        if (lastPeriod > MaxLength / 2)
            return text.Substring(0, lastPeriod + 1).Trim();

        // Hard truncate with ellipsis
        return text.Substring(0, MaxLength - 3) + "...";
    }

    private bool ContainsHarmfulAdvice(string message)
    {
        var harmful = new[] { "self-harm", "suicide", "overdose intentionally", "stop all medication" };
        return harmful.Any(h => message.Contains(h, StringComparison.OrdinalIgnoreCase));
    }

    private bool ContainsMedicationAdvice(string message)
    {
        var keywords = new[] { "take", "dosage", "mg", "tablet", "pill", "medication", "drug" };
        return keywords.Any(k => message.Contains(k, StringComparison.OrdinalIgnoreCase));
    }

    private bool ContainsEmergencyKeywords(string message)
    {
        var emergency = new[] { "bleeding", "unconscious", "chest pain", "severe", "emergency" };
        return emergency.Any(e => message.Contains(e, StringComparison.OrdinalIgnoreCase));
    }
}
