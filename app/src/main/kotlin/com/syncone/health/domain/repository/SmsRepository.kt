package com.syncone.health.domain.repository

import com.syncone.health.domain.model.ConversationContext
import com.syncone.health.domain.model.SmsMessage
import com.syncone.health.domain.model.enums.MessageDirection
import com.syncone.health.domain.model.enums.MessageStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for SMS message operations.
 */
interface SmsRepository {

    suspend fun saveMessage(
        threadId: Long,
        content: String,
        direction: MessageDirection,
        status: MessageStatus,
        aiConfidence: Float? = null,
        isManual: Boolean = false
    ): Long

    suspend fun getMessageById(messageId: Long): SmsMessage?

    fun observeMessagesByThreadId(threadId: Long): Flow<List<SmsMessage>>

    suspend fun getLastMessage(threadId: Long): SmsMessage?

    suspend fun getLastNMessages(threadId: Long, limit: Int): List<SmsMessage>

    suspend fun updateMessageStatus(messageId: Long, status: MessageStatus)

    suspend fun getFailedMessages(threadId: Long): List<SmsMessage>

    // Conversation Context
    suspend fun saveConversationContext(context: ConversationContext)

    suspend fun getConversationContext(threadId: Long): ConversationContext?

    suspend fun resetConversationContext(threadId: Long)

    suspend fun deleteExpiredContexts()
}
