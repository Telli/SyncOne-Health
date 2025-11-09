using Microsoft.Extensions.Logging;
using SyncOne.Core.Models;
using SyncOne.Core.Services;

namespace SyncOne.Core.Agents;

/// <summary>
/// Specialized agent for pregnancy, prenatal, and postnatal care
/// </summary>
public class MaternalHealthAgent : IAgent
{
    private readonly IMedGemmaService _medgemma;
    private readonly IRagService _rag;
    private readonly ILogger<MaternalHealthAgent> _logger;

    public string Name => "MaternalHealth";
    public string Description => "Pregnancy and maternal care guidance";

    public MaternalHealthAgent(
        IMedGemmaService medgemma,
        IRagService rag,
        ILogger<MaternalHealthAgent> logger)
    {
        _medgemma = medgemma;
        _rag = rag;
        _logger = logger;
    }

    public async Task<AgentResponse> InvokeAsync(
        InferenceRequest request,
        CancellationToken cancellationToken = default)
    {
        // Retrieve maternal-specific guidelines
        var guidelines = await _rag.SearchGuidelinesAsync(
            query: request.Message,
            category: "maternal_health",
            topK: 3,
            cancellationToken: cancellationToken);

        var prompt = BuildMaternalPrompt(request, guidelines);

        var response = await _medgemma.GenerateAsync(
            prompt: prompt,
            maxTokens: 200,
            temperature: 0.6f, // Lower temp for safety
            cancellationToken: cancellationToken);

        // Always add urgency check for maternal queries
        var urgencyAdded = AddMaternalUrgencyCheck(response, request.Message);

        return new AgentResponse(
            Message: urgencyAdded,
            Confidence: CalculateMaternalConfidence(response, guidelines),
            Sources: guidelines.Select(g => g.Source).ToList(),
            Metadata: new Dictionary<string, object>
            {
                ["agent"] = Name,
                ["category"] = "maternal_health"
            }
        );
    }

    private string BuildMaternalPrompt(InferenceRequest request, List<RagChunk> guidelines)
    {
        return $"""
        You are a maternal health specialist assisting community health workers.

        CRITICAL: Pregnancy and childbirth complications can be life-threatening.
        Always err on the side of caution and recommend professional care for:
        - Bleeding during pregnancy
        - Severe abdominal pain
        - Reduced fetal movement
        - Signs of labor complications
        - High blood pressure symptoms

        GUIDELINES:
        {string.Join("\n\n", guidelines.Select(g => g.Content))}

        USER QUESTION: {request.Message}

        Provide clear guidance in simple language. Keep under 450 characters.

        RESPONSE:
        """;
    }

    private string AddMaternalUrgencyCheck(string response, string query)
    {
        var urgentKeywords = new[] {
            "bleeding", "contractions", "water broke", "severe pain",
            "baby not moving", "headache", "vision"
        };

        if (urgentKeywords.Any(k => query.Contains(k, StringComparison.OrdinalIgnoreCase)))
        {
            return $"⚠️ {response}\n\nGo to a health facility immediately if symptoms worsen.";
        }

        return response;
    }

    private float CalculateMaternalConfidence(string response, List<RagChunk> guidelines)
    {
        // Lower confidence threshold for maternal health (safety-critical)
        return Math.Clamp(guidelines.Average(g => g.Score) * 0.9f, 0f, 0.85f);
    }
}
