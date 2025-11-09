package com.syncone.health.domain.usecase

import com.syncone.health.domain.model.SmsThread
import com.syncone.health.domain.model.enums.ThreadStatus
import com.syncone.health.domain.model.enums.UrgencyLevel
import com.syncone.health.domain.repository.ThreadRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Get threads with optional filtering.
 */
class GetThreadsUseCase @Inject constructor(
    private val threadRepository: ThreadRepository
) {

    /**
     * Get all threads.
     */
    fun all(): Flow<List<SmsThread>> {
        return threadRepository.observeAllThreads()
    }

    /**
     * Get threads by status.
     */
    fun byStatus(status: ThreadStatus): Flow<List<SmsThread>> {
        return threadRepository.observeThreadsByStatus(status)
    }

    /**
     * Get threads by urgency level.
     */
    fun byUrgency(urgencyLevel: UrgencyLevel): Flow<List<SmsThread>> {
        return threadRepository.observeThreadsByUrgency(urgencyLevel)
    }

    /**
     * Get single thread by ID.
     */
    suspend fun byId(threadId: Long): SmsThread? {
        return threadRepository.getThreadById(threadId)
    }

    /**
     * Get thread by phone number.
     */
    suspend fun byPhoneNumber(phoneNumber: String): SmsThread? {
        return threadRepository.getThreadByPhoneNumber(phoneNumber)
    }
}
