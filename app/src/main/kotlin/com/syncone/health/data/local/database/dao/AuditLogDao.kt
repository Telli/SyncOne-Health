package com.syncone.health.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.syncone.health.data.local.database.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {

    @Insert
    suspend fun insert(log: AuditLogEntity): Long

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE thread_id = :threadId ORDER BY timestamp DESC")
    fun observeByThreadId(threadId: Long): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedLogs(): List<AuditLogEntity>

    @Query("UPDATE audit_logs SET synced = 1 WHERE id IN (:logIds)")
    suspend fun markAsSynced(logIds: List<Long>)

    @Query("DELETE FROM audit_logs WHERE timestamp < :cutoffTime AND synced = 1")
    suspend fun deleteOldSyncedLogs(cutoffTime: Long)
}
