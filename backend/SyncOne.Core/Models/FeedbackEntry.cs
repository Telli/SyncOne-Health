namespace SyncOne.Core.Models;

public record FeedbackEntry(
    string MessageId,
    string OriginalResponse,
    string? EditedResponse,
    string Rating,
    string? ChwId,
    long Timestamp
);
