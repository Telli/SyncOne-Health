package com.syncone.health.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks all CHW actions for compliance.
 * Synced to backend when connectivity available.
 */
@Entity(
    tableName = "audit_logs",
    indices = [Index("timestamp"), Index("synced")]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "event_type")
    val eventType: String, // SMS_RECEIVED, SMS_SENT, MANUAL_REPLY, FEEDBACK_RATED, etc.

    @ColumnInfo(name = "chw_id")
    val chwId: String?, // Null for system events

    @ColumnInfo(name = "thread_id")
    val threadId: Long?,

    @ColumnInfo(name = "details")
    val details: String, // JSON with event-specific data

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "synced")
    val synced: Boolean = false
)
