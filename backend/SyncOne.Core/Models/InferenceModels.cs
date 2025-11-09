namespace SyncOne.Core.Models;

public record InferenceRequest(
    string Message,
    List<ConversationTurn> ConversationHistory,
    RequestMetadata Metadata
);

public record ConversationTurn(
    string Role,
    string Content,
    long Timestamp
);

public record RequestMetadata(
    string? PhoneNumber,
    string UrgencyLevel,
    int TokenCount
);

public record InferenceResponse(
    string Message,
    float Confidence,
    string AgentUsed,
    string UrgencyLevel,
    List<string> Sources
);

public record AgentResponse(
    string Message,
    float Confidence,
    List<string> Sources,
    Dictionary<string, object> Metadata
);
