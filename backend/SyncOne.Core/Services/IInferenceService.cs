using SyncOne.Core.Models;

namespace SyncOne.Core.Services;

public interface IMedGemmaService
{
    Task<string> GenerateAsync(
        string prompt,
        int maxTokens = 200,
        float temperature = 0.7f,
        CancellationToken cancellationToken = default);
}

public record RagChunk(
    string Content,
    string Source,
    float Score,
    Dictionary<string, string> Metadata
);

public interface IRagService
{
    Task<List<RagChunk>> SearchGuidelinesAsync(
        string query,
        string? category = null,
        int topK = 3,
        CancellationToken cancellationToken = default);

    Task IndexDocumentAsync(
        string content,
        string source,
        string category,
        CancellationToken cancellationToken = default);
}

public interface IAlertService
{
    Task DispatchAlertAsync(
        string? phoneNumber,
        string message,
        string urgency,
        CancellationToken cancellationToken = default);
}

public interface IFeedbackService
{
    Task StoreFeedbackAsync(
        FeedbackEntry feedback,
        CancellationToken cancellationToken = default);

    Task<List<FeedbackEntry>> GetFeedbackForReviewAsync(
        int limit = 50,
        CancellationToken cancellationToken = default);
}
