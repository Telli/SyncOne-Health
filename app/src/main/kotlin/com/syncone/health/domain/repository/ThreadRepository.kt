package com.syncone.health.domain.repository

import com.syncone.health.domain.model.SmsThread
import com.syncone.health.domain.model.enums.ThreadStatus
import com.syncone.health.domain.model.enums.UrgencyLevel
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for SMS thread operations.
 * Clean architecture - domain layer defines contract, data layer implements.
 */
interface ThreadRepository {

    suspend fun createThread(
        phoneNumber: String,
        status: ThreadStatus,
        urgencyLevel: UrgencyLevel
    ): Long

    suspend fun getThreadById(threadId: Long): SmsThread?

    suspend fun getThreadByPhoneNumber(phoneNumber: String): SmsThread?

    fun observeAllThreads(): Flow<List<SmsThread>>

    fun observeThreadsByStatus(status: ThreadStatus): Flow<List<SmsThread>>

    fun observeThreadsByUrgency(urgencyLevel: UrgencyLevel): Flow<List<SmsThread>>

    suspend fun updateThreadStatus(threadId: Long, status: ThreadStatus)

    suspend fun updateThreadUrgency(threadId: Long, urgencyLevel: UrgencyLevel)

    suspend fun incrementMessageCount(threadId: Long, timestamp: Long)

    suspend fun archiveExpiredThreads(): Int

    suspend fun deleteThread(threadId: Long)
}
