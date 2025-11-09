package com.syncone.health.domain.repository

import com.syncone.health.domain.model.AuditLog
import com.syncone.health.domain.model.AuditEventType
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for audit logging.
 */
interface AuditRepository {

    suspend fun log(
        eventType: AuditEventType,
        chwId: String? = null,
        threadId: Long? = null,
        details: Map<String, Any> = emptyMap()
    ): Long

    fun observeAllLogs(): Flow<List<AuditLog>>

    fun observeLogsByThreadId(threadId: Long): Flow<List<AuditLog>>

    suspend fun getUnsyncedLogs(): List<AuditLog>

    suspend fun markAsSynced(logIds: List<Long>)

    suspend fun deleteOldSyncedLogs(olderThanDays: Int)
}
