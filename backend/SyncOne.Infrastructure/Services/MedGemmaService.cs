using System.Net.Http.Json;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using SyncOne.Core.Services;

namespace SyncOne.Infrastructure.Services;

/// <summary>
/// Service for calling MedGemma-27B model
/// Supports Azure ML, AWS SageMaker, or self-hosted endpoints
/// </summary>
public class MedGemmaService : IMedGemmaService
{
    private readonly HttpClient _httpClient;
    private readonly IConfiguration _config;
    private readonly ILogger<MedGemmaService> _logger;

    public MedGemmaService(
        HttpClient httpClient,
        IConfiguration config,
        ILogger<MedGemmaService> logger)
    {
        _httpClient = httpClient;
        _config = config;
        _logger = logger;

        // HttpClient configuration (BaseAddress, headers) is handled in DI registration.
    }

    public async Task<string> GenerateAsync(
        string prompt,
        int maxTokens = 200,
        float temperature = 0.7f,
        CancellationToken cancellationToken = default)
    {
        try
        {
            var request = new
            {
                prompt,
                max_tokens = maxTokens,
                temperature,
                top_p = 0.9,
                stop = new[] { "\n\nUSER:", "\n\nASSISTANT:" }
            };

            var response = await _httpClient.PostAsJsonAsync(
                "/generate",
                request,
                cancellationToken);

            response.EnsureSuccessStatusCode();

            var result = await response.Content.ReadFromJsonAsync<GenerateResponse>(
                cancellationToken: cancellationToken);

            return result?.Text ?? string.Empty;
        }
        catch (HttpRequestException ex)
        {
            _logger.LogError(ex, "MedGemma API request failed");
            throw new InferenceException("Failed to generate response from MedGemma", ex);
        }
    }

    private record GenerateResponse(string Text, int TokensGenerated);
}

public class InferenceException : Exception
{
    public InferenceException(string message, Exception? innerException = null)
        : base(message, innerException) { }
}
