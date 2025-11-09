package com.syncone.health.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.syncone.health.data.local.database.dao.ConversationContextDao
import com.syncone.health.data.local.database.dao.SmsMessageDao
import com.syncone.health.data.local.database.entity.ConversationContextEntity
import com.syncone.health.data.local.database.entity.SmsMessageEntity
import com.syncone.health.data.repository.mapper.SmsMessageMapper
import com.syncone.health.domain.model.ConversationContext
import com.syncone.health.domain.model.ConversationTurn
import com.syncone.health.domain.model.SmsMessage
import com.syncone.health.domain.model.enums.MessageDirection
import com.syncone.health.domain.model.enums.MessageStatus
import com.syncone.health.domain.repository.SmsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of SmsRepository.
 * Manages SMS messages and conversation contexts.
 */
class SmsRepositoryImpl @Inject constructor(
    private val messageDao: SmsMessageDao,
    private val contextDao: ConversationContextDao,
    private val gson: Gson
) : SmsRepository {

    override suspend fun saveMessage(
        threadId: Long,
        content: String,
        direction: MessageDirection,
        status: MessageStatus,
        aiConfidence: Float?,
        isManual: Boolean
    ): Long {
        val entity = SmsMessageEntity(
            threadId = threadId,
            content = content,
            direction = direction.name,
            status = status.name,
            timestamp = System.currentTimeMillis(),
            aiConfidence = aiConfidence,
            isManual = isManual
        )
        return messageDao.insert(entity)
    }

    override suspend fun getMessageById(messageId: Long): SmsMessage? {
        val entity = messageDao.getById(messageId) ?: return null
        return SmsMessageMapper.toDomain(entity)
    }

    override fun observeMessagesByThreadId(threadId: Long): Flow<List<SmsMessage>> {
        return messageDao.observeByThreadId(threadId).map { entities ->
            entities.map { SmsMessageMapper.toDomain(it) }
        }
    }

    override suspend fun getLastMessage(threadId: Long): SmsMessage? {
        val entity = messageDao.getLastMessage(threadId) ?: return null
        return SmsMessageMapper.toDomain(entity)
    }

    override suspend fun getLastNMessages(threadId: Long, limit: Int): List<SmsMessage> {
        val entities = messageDao.getLastNMessages(threadId, limit)
        return entities.map { SmsMessageMapper.toDomain(it) }.reversed()
    }

    override suspend fun updateMessageStatus(messageId: Long, status: MessageStatus) {
        messageDao.updateStatus(messageId, status.name)
    }

    override suspend fun getFailedMessages(threadId: Long): List<SmsMessage> {
        val entities = messageDao.getFailedMessages(threadId)
        return entities.map { SmsMessageMapper.toDomain(it) }
    }

    // Conversation Context

    override suspend fun saveConversationContext(context: ConversationContext) {
        val turnHistory = gson.toJson(context.turns)
        val entity = ConversationContextEntity(
            threadId = context.threadId,
            turnHistory = turnHistory,
            tokenCount = context.tokenCount,
            lastUpdated = context.lastUpdated
        )
        contextDao.insert(entity)
    }

    override suspend fun getConversationContext(threadId: Long): ConversationContext? {
        val entity = contextDao.getByThreadId(threadId) ?: return null

        val turnsType = object : TypeToken<List<ConversationTurn>>() {}.type
        val turns: List<ConversationTurn> = try {
            gson.fromJson(entity.turnHistory, turnsType)
        } catch (e: Exception) {
            emptyList()
        }

        return ConversationContext(
            threadId = entity.threadId,
            turns = turns,
            tokenCount = entity.tokenCount,
            lastUpdated = entity.lastUpdated
        )
    }

    override suspend fun resetConversationContext(threadId: Long) {
        contextDao.delete(threadId)
    }

    override suspend fun deleteExpiredContexts() {
        val currentTime = System.currentTimeMillis()
        contextDao.deleteExpiredContexts(currentTime)
    }
}
