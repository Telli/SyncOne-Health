using SyncOne.Core.Models;

namespace SyncOne.Core.Agents;

public interface IAgent
{
    string Name { get; }
    string Description { get; }

    Task<AgentResponse> InvokeAsync(
        InferenceRequest request,
        CancellationToken cancellationToken = default);
}

public record Classification(
    string AgentType,
    float Confidence,
    string Reasoning
);
