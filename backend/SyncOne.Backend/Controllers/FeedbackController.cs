using Microsoft.AspNetCore.Mvc;
using SyncOne.Core.Models;
using SyncOne.Core.Services;

namespace SyncOne.Backend.Controllers;

[ApiController]
[Route("api/v1/[controller]")]
public class FeedbackController : ControllerBase
{
    private readonly IFeedbackService _feedbackService;
    private readonly ILogger<FeedbackController> _logger;

    public FeedbackController(
        IFeedbackService feedbackService,
        ILogger<FeedbackController> logger)
    {
        _feedbackService = feedbackService;
        _logger = logger;
    }

    [HttpPost]
    public async Task<ActionResult> SubmitFeedback(
        [FromBody] FeedbackEntry feedback,
        CancellationToken cancellationToken)
    {
        try
        {
            await _feedbackService.StoreFeedbackAsync(feedback, cancellationToken);
            return Ok(new { success = true, message = "Feedback received" });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to store feedback");
            return StatusCode(500, new { error = "Failed to store feedback" });
        }
    }

    [HttpGet("review")]
    public async Task<ActionResult<List<FeedbackEntry>>> GetFeedbackForReview(
        [FromQuery] int limit = 50,
        CancellationToken cancellationToken = default)
    {
        var feedback = await _feedbackService.GetFeedbackForReviewAsync(limit, cancellationToken);
        return Ok(feedback);
    }
}
