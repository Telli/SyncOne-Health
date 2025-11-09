using Pgvector;

namespace SyncOne.Infrastructure.Data.Entities;

public class GuidelineChunk
{
    public Guid Id { get; set; }
    public string Content { get; set; } = string.Empty;
    public string Source { get; set; } = string.Empty;
    public string Category { get; set; } = string.Empty;
    public Vector Embedding { get; set; } = null!;
    public DateTime CreatedAt { get; set; }
}
