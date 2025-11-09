namespace SyncOne.Infrastructure.Data.Entities;

public class FeedbackEntry
{
    public Guid Id { get; set; }
    public string MessageId { get; set; } = string.Empty;
    public string OriginalResponse { get; set; } = string.Empty;
    public string? EditedResponse { get; set; }
    public string Rating { get; set; } = string.Empty;
    public string? ChwId { get; set; }
    public long Timestamp { get; set; }
    public bool NeedsReview { get; set; }
}
