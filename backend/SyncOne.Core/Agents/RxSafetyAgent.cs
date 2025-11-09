using Microsoft.Extensions.Logging;
using SyncOne.Core.Models;
using SyncOne.Core.Services;

namespace SyncOne.Core.Agents;

/// <summary>
/// Handles medication safety, drug interactions, and dosing questions
/// </summary>
public class RxSafetyAgent : IAgent
{
    private readonly IMedGemmaService _medgemma;
    private readonly IRagService _rag;
    private readonly ILogger<RxSafetyAgent> _logger;

    public string Name => "RxSafety";
    public string Description => "Medication safety and drug interaction guidance";

    public RxSafetyAgent(
        IMedGemmaService medgemma,
        IRagService rag,
        ILogger<RxSafetyAgent> logger)
    {
        _medgemma = medgemma;
        _rag = rag;
        _logger = logger;
    }

    public async Task<AgentResponse> InvokeAsync(
        InferenceRequest request,
        CancellationToken cancellationToken = default)
    {
        var guidelines = await _rag.SearchGuidelinesAsync(
            query: request.Message,
            category: "pharmacology",
            topK: 3,
            cancellationToken: cancellationToken);

        var prompt = BuildRxPrompt(request, guidelines);

        var response = await _medgemma.GenerateAsync(
            prompt: prompt,
            maxTokens: 200,
            temperature: 0.5f, // Lower for medication safety
            cancellationToken: cancellationToken);

        // Always add pharmacist consultation disclaimer
        response += "\n\nConsult a pharmacist or doctor before starting any medication.";

        return new AgentResponse(
            Message: response,
            Confidence: 0.75f, // Always moderate confidence for medication advice
            Sources: guidelines.Select(g => g.Source).ToList(),
            Metadata: new Dictionary<string, object>
            {
                ["agent"] = Name,
                ["requires_professional"] = true
            }
        );
    }

    private string BuildRxPrompt(InferenceRequest request, List<RagChunk> guidelines)
    {
        return $"""
        You are a medication safety specialist for community health workers.

        CRITICAL RULES:
        - NEVER provide specific dosages without professional consultation
        - Always warn about potential drug interactions
        - Emphasize the importance of following prescribed instructions
        - Do NOT recommend over-the-counter medications without caveats

        DRUG INFORMATION:
        {string.Join("\n\n", guidelines.Select(g => g.Content))}

        USER QUESTION: {request.Message}

        Provide general safety guidance only. Keep under 420 characters.

        RESPONSE:
        """;
    }
}
