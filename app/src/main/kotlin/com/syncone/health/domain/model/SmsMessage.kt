package com.syncone.health.domain.model

import com.syncone.health.domain.model.enums.MessageDirection
import com.syncone.health.domain.model.enums.MessageStatus

/**
 * Domain model representing an individual SMS message.
 */
data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val content: String,
    val direction: MessageDirection,
    val status: MessageStatus,
    val timestamp: Long,
    val aiConfidence: Float? = null,
    val isManual: Boolean = false
)
