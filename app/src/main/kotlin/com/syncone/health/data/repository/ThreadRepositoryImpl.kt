package com.syncone.health.data.repository

import com.syncone.health.data.local.database.dao.SmsMessageDao
import com.syncone.health.data.local.database.dao.SmsThreadDao
import com.syncone.health.data.local.database.entity.SmsThreadEntity
import com.syncone.health.data.repository.mapper.SmsThreadMapper
import com.syncone.health.domain.model.SmsThread
import com.syncone.health.domain.model.enums.ThreadStatus
import com.syncone.health.domain.model.enums.UrgencyLevel
import com.syncone.health.domain.repository.ThreadRepository
import com.syncone.health.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of ThreadRepository.
 * Manages SMS threads in database.
 */
class ThreadRepositoryImpl @Inject constructor(
    private val threadDao: SmsThreadDao,
    private val messageDao: SmsMessageDao
) : ThreadRepository {

    override suspend fun createThread(
        phoneNumber: String,
        status: ThreadStatus,
        urgencyLevel: UrgencyLevel
    ): Long {
        val now = System.currentTimeMillis()
        val entity = SmsThreadEntity(
            phoneNumber = phoneNumber,
            status = status.name,
            urgencyLevel = urgencyLevel.name,
            lastMessageAt = now,
            createdAt = now,
            expiresAt = now + Constants.THREAD_TTL_MS,
            messageCount = 0
        )
        return threadDao.insert(entity)
    }

    override suspend fun getThreadById(threadId: Long): SmsThread? {
        val entity = threadDao.getById(threadId) ?: return null
        val lastMessage = messageDao.getLastMessage(threadId)?.content ?: ""
        return SmsThreadMapper.toDomain(entity, lastMessage)
    }

    override suspend fun getThreadByPhoneNumber(phoneNumber: String): SmsThread? {
        val entity = threadDao.getByPhoneNumber(phoneNumber) ?: return null
        val lastMessage = messageDao.getLastMessage(entity.id)?.content ?: ""
        return SmsThreadMapper.toDomain(entity, lastMessage)
    }

    override fun observeAllThreads(): Flow<List<SmsThread>> {
        return threadDao.observeAll().map { entities ->
            entities.map { entity ->
                val lastMessage = messageDao.getLastMessage(entity.id)?.content ?: ""
                SmsThreadMapper.toDomain(entity, lastMessage)
            }
        }
    }

    override fun observeThreadsByStatus(status: ThreadStatus): Flow<List<SmsThread>> {
        return threadDao.observeByStatus(status.name).map { entities ->
            entities.map { entity ->
                val lastMessage = messageDao.getLastMessage(entity.id)?.content ?: ""
                SmsThreadMapper.toDomain(entity, lastMessage)
            }
        }
    }

    override fun observeThreadsByUrgency(urgencyLevel: UrgencyLevel): Flow<List<SmsThread>> {
        return threadDao.observeByUrgency(urgencyLevel.name).map { entities ->
            entities.map { entity ->
                val lastMessage = messageDao.getLastMessage(entity.id)?.content ?: ""
                SmsThreadMapper.toDomain(entity, lastMessage)
            }
        }
    }

    override suspend fun updateThreadStatus(threadId: Long, status: ThreadStatus) {
        val thread = threadDao.getById(threadId) ?: return
        threadDao.update(thread.copy(status = status.name))
    }

    override suspend fun updateThreadUrgency(threadId: Long, urgencyLevel: UrgencyLevel) {
        val thread = threadDao.getById(threadId) ?: return
        threadDao.update(thread.copy(urgencyLevel = urgencyLevel.name))
    }

    override suspend fun incrementMessageCount(threadId: Long, timestamp: Long) {
        threadDao.incrementMessageCount(threadId, timestamp)
    }

    override suspend fun archiveExpiredThreads(): Int {
        val now = System.currentTimeMillis()
        val expiredThreads = threadDao.getExpiredThreads(now)
        if (expiredThreads.isNotEmpty()) {
            threadDao.archiveThreads(expiredThreads.map { it.id })
        }
        return expiredThreads.size
    }

    override suspend fun deleteThread(threadId: Long) {
        threadDao.delete(threadId)
    }
}
