using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SyncOne.Infrastructure.Services;

namespace SyncOne.Backend.Controllers;

[ApiController]
[Route("api/v1/[controller]")]
[Authorize(Roles = "Admin")]
public class AdminController : ControllerBase
{
    private readonly RemoteWipeService _wipeService;
    private readonly ILogger<AdminController> _logger;

    public AdminController(
        RemoteWipeService wipeService,
        ILogger<AdminController> logger)
    {
        _wipeService = wipeService;
        _logger = logger;
    }

    [HttpPost("remote-wipe")]
    public async Task<ActionResult> RemoteWipe(
        [FromBody] RemoteWipeRequest request,
        CancellationToken cancellationToken)
    {
        try
        {
            var adminId = User.FindFirst("sub")?.Value ?? "Unknown";

            await _wipeService.InitiateWipeAsync(
                request.DeviceId,
                adminId,
                request.Reason,
                cancellationToken);

            return Ok(new { success = true, message = "Wipe initiated" });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to initiate wipe");
            return StatusCode(500, new { error = "Failed to initiate wipe" });
        }
    }
}

public record RemoteWipeRequest(string DeviceId, string Reason);
