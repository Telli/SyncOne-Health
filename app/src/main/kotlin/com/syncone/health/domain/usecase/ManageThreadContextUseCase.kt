package com.syncone.health.domain.usecase

import com.syncone.health.domain.model.ConversationContext
import com.syncone.health.domain.repository.SmsRepository
import com.syncone.health.util.Constants
import javax.inject.Inject

/**
 * Manages conversation context including 72h TTL and RESET command.
 */
class ManageThreadContextUseCase @Inject constructor(
    private val smsRepository: SmsRepository
) {

    /**
     * Get conversation context for a thread.
     */
    suspend fun getContext(threadId: Long): ConversationContext? {
        return smsRepository.getConversationContext(threadId)
    }

    /**
     * Save updated context.
     */
    suspend fun saveContext(context: ConversationContext) {
        smsRepository.saveConversationContext(context)
    }

    /**
     * Reset context (handles RESET command).
     */
    suspend fun resetContext(threadId: Long) {
        smsRepository.resetConversationContext(threadId)
    }

    /**
     * Check if message is RESET command.
     */
    fun isResetCommand(message: String): Boolean {
        return message.trim().uppercase() == Constants.RESET_COMMAND
    }

    /**
     * Delete expired contexts (for background cleanup).
     */
    suspend fun deleteExpiredContexts() {
        smsRepository.deleteExpiredContexts()
    }

    /**
     * Check if context has exceeded TTL.
     */
    fun hasExceeded72Hours(context: ConversationContext): Boolean {
        val elapsed = System.currentTimeMillis() - context.lastUpdated
        return elapsed > Constants.THREAD_TTL_MS
    }
}
