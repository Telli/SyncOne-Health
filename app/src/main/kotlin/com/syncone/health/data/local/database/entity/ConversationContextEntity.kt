package com.syncone.health.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Maintains conversation history for context.
 * Stores last 3 turns (user/assistant pairs) as JSON.
 */
@Entity(tableName = "conversation_contexts")
data class ConversationContextEntity(
    @PrimaryKey
    val threadId: Long,

    @ColumnInfo(name = "turn_history")
    val turnHistory: String, // JSON array of turns

    @ColumnInfo(name = "token_count")
    val tokenCount: Int,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long
)
