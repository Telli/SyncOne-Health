using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using SyncOne.Infrastructure.Data;
using SyncOne.Infrastructure.Data.Entities;

namespace SyncOne.Infrastructure.Repositories;

public class AuditLogRepository
{
    private readonly ApplicationDbContext _context;
    private readonly ILogger<AuditLogRepository> _logger;

    public AuditLogRepository(
        ApplicationDbContext context,
        ILogger<AuditLogRepository> logger)
    {
        _context = context;
        _logger = logger;
    }

    public async Task<AuditLog> CreateAsync(
        string action,
        string adminId,
        string details,
        string reason,
        string? deviceId = null,
        string? targetEntity = null,
        string ipAddress = "",
        string? userAgent = null,
        CancellationToken cancellationToken = default)
    {
        var auditLog = new AuditLog
        {
            Id = Guid.NewGuid(),
            Action = action,
            AdminId = adminId,
            DeviceId = deviceId,
            TargetEntity = targetEntity,
            Details = details,
            Reason = reason,
            CreatedAt = DateTime.UtcNow,
            IpAddress = ipAddress,
            UserAgent = userAgent
        };

        _context.AuditLogs.Add(auditLog);
        await _context.SaveChangesAsync(cancellationToken);

        _logger.LogInformation(
            "Audit log created: Action={Action}, Admin={AdminId}, Device={DeviceId}",
            action,
            adminId,
            deviceId);

        return auditLog;
    }

    public async Task<List<AuditLog>> GetByAdminAsync(
        string adminId,
        int limit = 100,
        CancellationToken cancellationToken = default)
    {
        return await _context.AuditLogs
            .Where(a => a.AdminId == adminId)
            .OrderByDescending(a => a.CreatedAt)
            .Take(limit)
            .ToListAsync(cancellationToken);
    }

    public async Task<List<AuditLog>> GetByDeviceAsync(
        string deviceId,
        CancellationToken cancellationToken = default)
    {
        return await _context.AuditLogs
            .Where(a => a.DeviceId == deviceId)
            .OrderByDescending(a => a.CreatedAt)
            .ToListAsync(cancellationToken);
    }

    public async Task<List<AuditLog>> GetByActionAsync(
        string action,
        int limit = 100,
        CancellationToken cancellationToken = default)
    {
        return await _context.AuditLogs
            .Where(a => a.Action == action)
            .OrderByDescending(a => a.CreatedAt)
            .Take(limit)
            .ToListAsync(cancellationToken);
    }

    public async Task<List<AuditLog>> GetRecentAsync(
        int limit = 100,
        CancellationToken cancellationToken = default)
    {
        return await _context.AuditLogs
            .OrderByDescending(a => a.CreatedAt)
            .Take(limit)
            .ToListAsync(cancellationToken);
    }
}
