package com.syncone.health.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a conversation thread with a user.
 * Phone numbers are stored UNMASKED (for CHW callbacks) but encrypted at rest via SQLCipher.
 */
@Entity(tableName = "sms_threads")
data class SmsThreadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "phone_number")
    val phoneNumber: String, // E.164 format, unmasked

    @ColumnInfo(name = "status")
    val status: String, // ACTIVE, RESOLVED, ARCHIVED

    @ColumnInfo(name = "urgency_level")
    val urgencyLevel: String, // NORMAL, URGENT, CRITICAL

    @ColumnInfo(name = "last_message_at")
    val lastMessageAt: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long, // createdAt + 72h

    @ColumnInfo(name = "message_count")
    val messageCount: Int = 0
)
