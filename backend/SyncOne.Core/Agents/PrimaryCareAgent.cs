using Microsoft.Extensions.Logging;
using SyncOne.Core.Models;
using SyncOne.Core.Services;

namespace SyncOne.Core.Agents;

/// <summary>
/// Handles general primary care queries: symptoms, minor ailments, preventive care
/// </summary>
public class PrimaryCareAgent : IAgent
{
    private readonly IMedGemmaService _medgemma;
    private readonly IRagService _rag;
    private readonly ILogger<PrimaryCareAgent> _logger;

    public string Name => "PrimaryCare";
    public string Description => "General symptoms and primary care guidance";

    public PrimaryCareAgent(
        IMedGemmaService medgemma,
        IRagService rag,
        ILogger<PrimaryCareAgent> logger)
    {
        _medgemma = medgemma;
        _rag = rag;
        _logger = logger;
    }

    public async Task<AgentResponse> InvokeAsync(
        InferenceRequest request,
        CancellationToken cancellationToken = default)
    {
        try
        {
            // 1. Retrieve relevant medical guidelines from RAG
            var guidelines = await _rag.SearchGuidelinesAsync(
                query: request.Message,
                topK: 3,
                cancellationToken: cancellationToken);

            _logger.LogInformation("Retrieved {Count} guidelines for query", guidelines.Count);

            // 2. Build prompt with context
            var prompt = BuildPrompt(request, guidelines);

            // 3. Call MedGemma-27B
            var response = await _medgemma.GenerateAsync(
                prompt: prompt,
                maxTokens: 200,
                temperature: 0.7f,
                cancellationToken: cancellationToken);

            // 4. Extract confidence
            var confidence = CalculateConfidence(response, guidelines);

            return new AgentResponse(
                Message: response,
                Confidence: confidence,
                Sources: guidelines.Select(g => g.Source).ToList(),
                Metadata: new Dictionary<string, object>
                {
                    ["agent"] = Name,
                    ["guidelineCount"] = guidelines.Count
                }
            );
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error in PrimaryCareAgent");
            throw;
        }
    }

    private string BuildPrompt(InferenceRequest request, List<RagChunk> guidelines)
    {
        var guidelinesText = string.Join("\n\n", guidelines.Select((g, i) =>
            $"GUIDELINE {i + 1} (from {g.Source}):\n{g.Content}"));

        var historyText = string.Join("\n", request.ConversationHistory.Select(h =>
            $"{h.Role.ToUpper()}: {h.Content}"));

        return $"""
        You are a primary care medical assistant for rural health workers in low-resource settings.

        MEDICAL GUIDELINES:
        {guidelinesText}

        CONVERSATION HISTORY:
        {historyText}

        USER QUESTION: {request.Message}

        Instructions:
        - Use the guidelines above to inform your response
        - Provide clear, actionable advice in simple language (6th grade reading level)
        - If symptoms are serious, recommend seeking professional care
        - Keep response under 450 characters
        - Do NOT diagnose conditions - provide informational guidance only

        RESPONSE:
        """;
    }

    private float CalculateConfidence(string response, List<RagChunk> guidelines)
    {
        // Higher confidence if guidelines found and used
        if (guidelines.Count == 0)
            return 0.5f;

        // Check if response references guideline content
        var referencesGuidelines = guidelines.Any(g =>
            response.Contains(g.Content.Split(' ').Take(5).FirstOrDefault() ?? "",
                StringComparison.OrdinalIgnoreCase));

        var baseConfidence = guidelines.Average(g => g.Score);
        var bonus = referencesGuidelines ? 0.1f : 0f;

        return Math.Clamp(baseConfidence + bonus, 0f, 1f);
    }
}
