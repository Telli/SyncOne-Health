using System.Net.Http.Json;
using System.Text;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using Pgvector;
using SyncOne.Core.Services;
using SyncOne.Infrastructure.Data;
using SyncOne.Infrastructure.Data.Entities;

namespace SyncOne.Infrastructure.Services;

/// <summary>
/// RAG service using PostgreSQL with pgvector extension
/// Stores and retrieves medical guidelines via semantic search
/// </summary>
public class RagService : IRagService
{
    private readonly ApplicationDbContext _context;
    private readonly HttpClient _embeddingClient;
    private readonly ILogger<RagService> _logger;

    public RagService(
        ApplicationDbContext context,
        IHttpClientFactory httpClientFactory,
        ILogger<RagService> logger)
    {
        _context = context;
        _embeddingClient = httpClientFactory.CreateClient("Embeddings");
        _logger = logger;
    }

    public async Task<List<RagChunk>> SearchGuidelinesAsync(
        string query,
        string? category = null,
        int topK = 3,
        CancellationToken cancellationToken = default)
    {
        try
        {
            // 1. Generate embedding for query
            var queryEmbedding = await GenerateEmbeddingAsync(query, cancellationToken);

            // 2. Search vector store
            var results = await _context.GuidelineChunks
                .Where(c => category == null || c.Category == category)
                .OrderBy(c => c.Embedding.CosineDistance(queryEmbedding))
                .Take(topK)
                .Select(c => new RagChunk(
                    c.Content,
                    c.Source,
                    1.0f - (float)c.Embedding.CosineDistance(queryEmbedding), // Convert to similarity
                    new Dictionary<string, string>
                    {
                        ["category"] = c.Category,
                        ["chunk_id"] = c.Id.ToString()
                    }
                ))
                .ToListAsync(cancellationToken);

            _logger.LogInformation(
                "Found {Count} relevant guidelines for query: {Query}",
                results.Count,
                query);

            return results;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "RAG search failed");
            throw;
        }
    }

    public async Task IndexDocumentAsync(
        string content,
        string source,
        string category,
        CancellationToken cancellationToken = default)
    {
        try
        {
            // 1. Split into chunks (512 tokens each)
            var chunks = SplitIntoChunks(content, maxTokens: 512);

            foreach (var chunk in chunks)
            {
                // 2. Generate embedding
                var embedding = await GenerateEmbeddingAsync(chunk, cancellationToken);

                // 3. Store in database
                var entity = new GuidelineChunk
                {
                    Id = Guid.NewGuid(),
                    Content = chunk,
                    Source = source,
                    Category = category,
                    Embedding = embedding,
                    CreatedAt = DateTime.UtcNow
                };

                _context.GuidelineChunks.Add(entity);
            }

            await _context.SaveChangesAsync(cancellationToken);

            _logger.LogInformation(
                "Indexed {ChunkCount} chunks from {Source}",
                chunks.Count,
                source);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to index document");
            throw;
        }
    }

    private async Task<Vector> GenerateEmbeddingAsync(
        string text,
        CancellationToken cancellationToken)
    {
        var request = new { text };

        var response = await _embeddingClient.PostAsJsonAsync(
            "/embed",
            request,
            cancellationToken);

        response.EnsureSuccessStatusCode();

        var result = await response.Content.ReadFromJsonAsync<EmbeddingResponse>(
            cancellationToken: cancellationToken);

        return new Vector(result!.Embedding);
    }

    private List<string> SplitIntoChunks(string content, int maxTokens)
    {
        // Simple sentence-based chunking
        var sentences = content.Split(new[] { '.', '!', '?' },
            StringSplitOptions.RemoveEmptyEntries);

        var chunks = new List<string>();
        var currentChunk = new StringBuilder();
        var currentTokens = 0;

        foreach (var sentence in sentences)
        {
            var tokens = EstimateTokens(sentence);

            if (currentTokens + tokens > maxTokens && currentChunk.Length > 0)
            {
                chunks.Add(currentChunk.ToString().Trim());
                currentChunk.Clear();
                currentTokens = 0;
            }

            currentChunk.Append(sentence).Append(". ");
            currentTokens += tokens;
        }

        if (currentChunk.Length > 0)
        {
            chunks.Add(currentChunk.ToString().Trim());
        }

        return chunks;
    }

    private int EstimateTokens(string text)
    {
        return (int)(text.Split(' ').Length * 0.75);
    }

    private record EmbeddingResponse(float[] Embedding);
}
