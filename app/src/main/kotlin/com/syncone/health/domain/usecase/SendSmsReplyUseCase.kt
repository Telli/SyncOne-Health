package com.syncone.health.domain.usecase

import com.syncone.health.domain.model.AuditEventType
import com.syncone.health.domain.model.enums.MessageDirection
import com.syncone.health.domain.model.enums.MessageStatus
import com.syncone.health.domain.repository.AuditRepository
import com.syncone.health.domain.repository.SmsRepository
import com.syncone.health.domain.repository.ThreadRepository
import com.syncone.health.util.Constants
import com.syncone.health.util.SmsSegmenter
import javax.inject.Inject

/**
 * Send SMS reply (AI-generated or manual).
 */
class SendSmsReplyUseCase @Inject constructor(
    private val smsRepository: SmsRepository,
    private val threadRepository: ThreadRepository,
    private val auditRepository: AuditRepository
) {

    /**
     * Save outgoing message to database.
     * Returns message ID.
     */
    suspend operator fun invoke(
        threadId: Long,
        content: String,
        isManual: Boolean,
        aiConfidence: Float? = null,
        chwId: String? = null
    ): Long {
        // Truncate if too long
        val finalContent = SmsSegmenter.truncate(content)

        // Save message
        val messageId = smsRepository.saveMessage(
            threadId = threadId,
            content = finalContent,
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.PENDING, // Will be updated after send attempt
            aiConfidence = aiConfidence,
            isManual = isManual
        )

        // Update thread's last message timestamp
        threadRepository.incrementMessageCount(threadId, System.currentTimeMillis())

        // Log to audit trail
        val eventType = if (isManual) {
            AuditEventType.MANUAL_REPLY
        } else {
            AuditEventType.SMS_SENT
        }

        auditRepository.log(
            eventType = eventType,
            chwId = chwId,
            threadId = threadId,
            details = mapOf(
                Constants.AUDIT_KEY_MESSAGE to finalContent,
                "ai_confidence" to (aiConfidence ?: 0f),
                "is_manual" to isManual
            )
        )

        return messageId
    }

    /**
     * Update message status after send attempt.
     */
    suspend fun updateStatus(messageId: Long, status: MessageStatus) {
        smsRepository.updateMessageStatus(messageId, status)
    }
}
