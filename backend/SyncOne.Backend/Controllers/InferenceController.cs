using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;
using SyncOne.Core.Agents;
using SyncOne.Core.Models;

namespace SyncOne.Backend.Controllers;

[ApiController]
[Route("api/v1/[controller]")]
[EnableRateLimiting("inference")]
public class InferenceController : ControllerBase
{
    private readonly IAgent _coordinator;
    private readonly ILogger<InferenceController> _logger;

    public InferenceController(
        IAgent coordinator,
        ILogger<InferenceController> logger)
    {
        _coordinator = coordinator;
        _logger = logger;
    }

    [HttpPost]
    public async Task<ActionResult<InferenceResponse>> Inference(
        [FromBody] InferenceRequest request,
        CancellationToken cancellationToken)
    {
        try
        {
            _logger.LogInformation("Inference request received: {Message}", request.Message);

            var response = await _coordinator.InvokeAsync(request, cancellationToken);

            return Ok(new InferenceResponse(
                Message: response.Message,
                Confidence: response.Confidence,
                AgentUsed: response.Metadata.TryGetValue("agent", out var agent)
                    ? agent.ToString()! : "Unknown",
                UrgencyLevel: request.Metadata.UrgencyLevel,
                Sources: response.Sources
            ));
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Inference failed");
            return StatusCode(500, new { error = "Inference failed" });
        }
    }

    [HttpGet("health")]
    public ActionResult Health()
    {
        return Ok(new { status = "healthy", timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() });
    }
}
