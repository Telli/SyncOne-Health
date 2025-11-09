package com.syncone.health.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Individual message in a thread.
 * Cascade delete when parent thread is deleted.
 */
@Entity(
    tableName = "sms_messages",
    foreignKeys = [
        ForeignKey(
            entity = SmsThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["thread_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("thread_id")]
)
data class SmsMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "thread_id")
    val threadId: Long,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "direction")
    val direction: String, // INCOMING, OUTGOING

    @ColumnInfo(name = "status")
    val status: String, // PENDING, SENT, FAILED

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "ai_confidence")
    val aiConfidence: Float? = null,

    @ColumnInfo(name = "is_manual")
    val isManual: Boolean = false // CHW manual reply
)
