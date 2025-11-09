package com.syncone.health.domain.usecase

import com.syncone.health.domain.model.ConversationContext
import com.syncone.health.domain.model.ConversationTurn
import com.syncone.health.util.Constants
import com.syncone.health.util.TokenCounter
import javax.inject.Inject

/**
 * Builds AI prompt with conversation context.
 * Maintains last 3 turns (6 messages), ≤512 tokens for local inference.
 */
class BuildPromptUseCase @Inject constructor() {

    /**
     * Build prompt from context and new user message.
     * Returns formatted prompt string ready for AI inference.
     */
    operator fun invoke(
        context: ConversationContext?,
        newUserMessage: String
    ): String {
        val systemPrompt = buildSystemPrompt()
        val conversationHistory = buildHistory(context)

        // Add new user message
        val userTurn = "User: $newUserMessage"

        // Combine all parts
        val fullPrompt = buildString {
            append(systemPrompt)
            append("\n\n")
            if (conversationHistory.isNotEmpty()) {
                append(conversationHistory)
                append("\n")
            }
            append(userTurn)
            append("\nAssistant:")
        }

        return fullPrompt
    }

    /**
     * Build updated context after AI response.
     */
    fun buildUpdatedContext(
        threadId: Long,
        existingContext: ConversationContext?,
        newUserMessage: String,
        aiResponse: String
    ): ConversationContext {
        val timestamp = System.currentTimeMillis()

        // Get existing turns or empty list
        val existingTurns = existingContext?.turns.orEmpty().toMutableList()

        // Add new user turn
        existingTurns.add(
            ConversationTurn(
                role = "user",
                content = newUserMessage,
                timestamp = timestamp
            )
        )

        // Add new assistant turn
        existingTurns.add(
            ConversationTurn(
                role = "assistant",
                content = aiResponse,
                timestamp = timestamp
            )
        )

        // Keep only last N turns (last 3 user/assistant pairs = 6 messages)
        val maxTurns = Constants.ROLLING_WINDOW_SIZE * 2
        val recentTurns = if (existingTurns.size > maxTurns) {
            existingTurns.takeLast(maxTurns)
        } else {
            existingTurns
        }

        // Calculate token count
        val allContent = recentTurns.map { it.content }
        val tokenCount = TokenCounter.estimateTotal(allContent)

        return ConversationContext(
            threadId = threadId,
            turns = recentTurns,
            tokenCount = tokenCount,
            lastUpdated = timestamp
        )
    }

    private fun buildSystemPrompt(): String {
        return """You are a medical AI assistant for SyncOne Health, helping rural communities access healthcare information via SMS.

Guidelines:
- Provide clear, concise medical guidance
- Keep responses under 480 characters (3 SMS messages)
- Use simple language accessible to non-medical users
- For serious conditions, advise seeking immediate medical attention
- Never diagnose - only provide general health information
- Be empathetic and supportive"""
    }

    private fun buildHistory(context: ConversationContext?): String {
        if (context == null || context.turns.isEmpty()) {
            return ""
        }

        return buildString {
            context.turns.forEach { turn ->
                when (turn.role) {
                    "user" -> append("User: ${turn.content}\n")
                    "assistant" -> append("Assistant: ${turn.content}\n")
                }
            }
        }.trim()
    }
}
