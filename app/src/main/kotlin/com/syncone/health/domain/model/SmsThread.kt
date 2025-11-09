package com.syncone.health.domain.model

import com.syncone.health.domain.model.enums.ThreadStatus
import com.syncone.health.domain.model.enums.UrgencyLevel

/**
 * Domain model representing a conversation thread.
 * Clean architecture - no framework dependencies.
 */
data class SmsThread(
    val id: Long,
    val phoneNumber: String,
    val status: ThreadStatus,
    val urgencyLevel: UrgencyLevel,
    val lastMessageAt: Long,
    val createdAt: Long,
    val expiresAt: Long,
    val messageCount: Int,
    val lastMessage: String = ""
)
