namespace SyncOne.Infrastructure.Data.Entities;

public class Alert
{
    public Guid Id { get; set; }
    public string? PhoneNumber { get; set; }
    public string Message { get; set; } = string.Empty;
    public string Urgency { get; set; } = string.Empty;
    public string Status { get; set; } = string.Empty;
    public DateTime CreatedAt { get; set; }
}
