package com.syncone.health.domain.model

/**
 * Domain model for conversation context.
 * Maintains rolling window of last 3 turns for AI prompt building.
 */
data class ConversationContext(
    val threadId: Long,
    val turns: List<ConversationTurn>,
    val tokenCount: Int,
    val lastUpdated: Long
)

data class ConversationTurn(
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long
)
