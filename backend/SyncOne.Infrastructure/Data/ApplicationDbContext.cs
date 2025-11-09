using Microsoft.EntityFrameworkCore;
using Pgvector.EntityFrameworkCore;
using SyncOne.Infrastructure.Data.Entities;

namespace SyncOne.Infrastructure.Data;

public class ApplicationDbContext : DbContext
{
    public ApplicationDbContext(DbContextOptions<ApplicationDbContext> options)
        : base(options) { }

    public DbSet<GuidelineChunk> GuidelineChunks { get; set; }
    public DbSet<FeedbackEntry> Feedback { get; set; }
    public DbSet<Alert> Alerts { get; set; }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        // Configure pgvector extension
        modelBuilder.HasPostgresExtension("vector");

        modelBuilder.Entity<GuidelineChunk>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Content).IsRequired();
            entity.Property(e => e.Source).IsRequired();
            entity.Property(e => e.Category).IsRequired();
            entity.Property(e => e.Embedding).HasColumnType("vector(384)");
            entity.HasIndex(e => e.Category);

            // Vector index for faster similarity search
            entity.HasIndex(e => e.Embedding)
                .HasMethod("ivfflat")
                .HasOperators("vector_cosine_ops");
        });

        modelBuilder.Entity<FeedbackEntry>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.HasIndex(e => e.Timestamp);
            entity.HasIndex(e => e.Rating);
        });

        modelBuilder.Entity<Alert>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.HasIndex(e => e.CreatedAt);
            entity.HasIndex(e => new { e.PhoneNumber, e.Urgency });
        });
    }
}
