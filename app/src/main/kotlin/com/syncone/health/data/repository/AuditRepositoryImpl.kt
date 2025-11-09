package com.syncone.health.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.syncone.health.data.local.database.dao.AuditLogDao
import com.syncone.health.data.local.database.entity.AuditLogEntity
import com.syncone.health.domain.model.AuditEventType
import com.syncone.health.domain.model.AuditLog
import com.syncone.health.domain.repository.AuditRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Implementation of AuditRepository.
 * Logs all CHW actions for compliance.
 */
class AuditRepositoryImpl @Inject constructor(
    private val auditLogDao: AuditLogDao,
    private val gson: Gson
) : AuditRepository {

    override suspend fun log(
        eventType: AuditEventType,
        chwId: String?,
        threadId: Long?,
        details: Map<String, Any>
    ): Long {
        val detailsJson = gson.toJson(details)
        val entity = AuditLogEntity(
            eventType = eventType.name,
            chwId = chwId,
            threadId = threadId,
            details = detailsJson,
            timestamp = System.currentTimeMillis(),
            synced = false
        )
        return auditLogDao.insert(entity)
    }

    override fun observeAllLogs(): Flow<List<AuditLog>> {
        return auditLogDao.observeAll().map { entities ->
            entities.map { toDomain(it) }
        }
    }

    override fun observeLogsByThreadId(threadId: Long): Flow<List<AuditLog>> {
        return auditLogDao.observeByThreadId(threadId).map { entities ->
            entities.map { toDomain(it) }
        }
    }

    override suspend fun getUnsyncedLogs(): List<AuditLog> {
        val entities = auditLogDao.getUnsyncedLogs()
        return entities.map { toDomain(it) }
    }

    override suspend fun markAsSynced(logIds: List<Long>) {
        if (logIds.isNotEmpty()) {
            auditLogDao.markAsSynced(logIds)
        }
    }

    override suspend fun deleteOldSyncedLogs(olderThanDays: Int) {
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(olderThanDays.toLong())
        auditLogDao.deleteOldSyncedLogs(cutoffTime)
    }

    private fun toDomain(entity: AuditLogEntity): AuditLog {
        val detailsType = object : TypeToken<Map<String, Any>>() {}.type
        val details: Map<String, Any> = try {
            gson.fromJson(entity.details, detailsType)
        } catch (e: Exception) {
            emptyMap()
        }

        return AuditLog(
            id = entity.id,
            eventType = AuditEventType.valueOf(entity.eventType),
            chwId = entity.chwId,
            threadId = entity.threadId,
            details = details,
            timestamp = entity.timestamp,
            synced = entity.synced
        )
    }
}
