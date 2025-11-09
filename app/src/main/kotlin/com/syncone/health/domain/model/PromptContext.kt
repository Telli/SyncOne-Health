package com.syncone.health.domain.model

/**
 * Context for building inference prompts.
 */
data class PromptContext(
    val userMessage: String,
    val conversationHistory: List<ConversationTurn>,
    val tokensUsed: Int,
    val threadId: Long
)

/**
 * Routing decision result.
 */
enum class RoutingDecision {
    LOCAL,  // Use local MedGemma-4B
    CLOUD   // Use cloud MedGemma-27B + agents
}
