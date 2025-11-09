using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using SyncOne.Core.Services;
using SyncOne.Infrastructure.Data;

namespace SyncOne.Infrastructure.Services;

/// <summary>
/// Stores CHW feedback for model improvement
/// </summary>
public class FeedbackStorageService : IFeedbackService
{
    private readonly ApplicationDbContext _context;
    private readonly ILogger<FeedbackStorageService> _logger;

    public FeedbackStorageService(
        ApplicationDbContext context,
        ILogger<FeedbackStorageService> logger)
    {
        _context = context;
        _logger = logger;
    }

    public async Task StoreFeedbackAsync(
        Core.Models.FeedbackEntry feedback,
        CancellationToken cancellationToken = default)
    {
        try
        {
            var entity = new Data.Entities.FeedbackEntry
            {
                Id = Guid.NewGuid(),
                MessageId = feedback.MessageId,
                OriginalResponse = feedback.OriginalResponse,
                EditedResponse = feedback.EditedResponse,
                Rating = feedback.Rating,
                ChwId = feedback.ChwId,
                Timestamp = feedback.Timestamp,
                NeedsReview = feedback.Rating == "BAD"
            };

            _context.Feedback.Add(entity);
            await _context.SaveChangesAsync(cancellationToken);

            _logger.LogInformation(
                "Feedback stored: Message {MessageId}, Rating: {Rating}",
                feedback.MessageId,
                feedback.Rating);

            // Flag for human review if bad rating
            if (feedback.Rating == "BAD")
            {
                _logger.LogWarning(
                    "BAD RATING flagged for review: Message {MessageId}",
                    feedback.MessageId);
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to store feedback");
            throw;
        }
    }

    public async Task<List<Core.Models.FeedbackEntry>> GetFeedbackForReviewAsync(
        int limit = 50,
        CancellationToken cancellationToken = default)
    {
        return await _context.Feedback
            .Where(f => f.NeedsReview)
            .OrderByDescending(f => f.Timestamp)
            .Take(limit)
            .Select(f => new Core.Models.FeedbackEntry(
                f.MessageId,
                f.OriginalResponse,
                f.EditedResponse,
                f.Rating,
                f.ChwId,
                f.Timestamp
            ))
            .ToListAsync(cancellationToken);
    }
}
