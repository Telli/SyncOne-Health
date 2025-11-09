namespace SyncOne.Infrastructure.Data.Entities;

public class AuditLog
{
    public Guid Id { get; set; }
    public string Action { get; set; } = string.Empty;
    public string AdminId { get; set; } = string.Empty;
    public string? DeviceId { get; set; }
    public string? TargetEntity { get; set; }
    public string Details { get; set; } = string.Empty;
    public string Reason { get; set; } = string.Empty;
    public DateTime CreatedAt { get; set; }
    public string IpAddress { get; set; } = string.Empty;
    public string? UserAgent { get; set; }
}
