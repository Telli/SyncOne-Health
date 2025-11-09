package com.syncone.health.domain.usecase

import com.syncone.health.domain.model.AuditEventType
import com.syncone.health.domain.model.enums.MessageDirection
import com.syncone.health.domain.model.enums.MessageStatus
import com.syncone.health.domain.model.enums.ThreadStatus
import com.syncone.health.domain.model.enums.UrgencyLevel
import com.syncone.health.domain.repository.AuditRepository
import com.syncone.health.domain.repository.SmsRepository
import com.syncone.health.domain.repository.ThreadRepository
import com.syncone.health.util.Constants
import javax.inject.Inject

/**
 * Process incoming SMS message.
 * Handles thread creation, urgency detection, context management.
 */
class ProcessIncomingSmsUseCase @Inject constructor(
    private val threadRepository: ThreadRepository,
    private val smsRepository: SmsRepository,
    private val auditRepository: AuditRepository,
    private val detectUrgencyUseCase: DetectUrgencyUseCase,
    private val manageContextUseCase: ManageThreadContextUseCase
) {

    /**
     * Process incoming SMS.
     * Returns thread ID and whether it's a RESET command.
     */
    suspend operator fun invoke(
        phoneNumber: String,
        messageContent: String
    ): ProcessResult {
        // Check for RESET command
        val isResetCommand = manageContextUseCase.isResetCommand(messageContent)

        // Find or create thread
        val thread = threadRepository.getThreadByPhoneNumber(phoneNumber)
        val threadId = if (thread != null) {
            // Existing thread - check if expired
            val now = System.currentTimeMillis()
            if (now > thread.expiresAt) {
                // Archive expired thread and create new one
                threadRepository.updateThreadStatus(thread.id, ThreadStatus.ARCHIVED)
                createNewThread(phoneNumber, messageContent)
            } else {
                thread.id
            }
        } else {
            // New thread
            createNewThread(phoneNumber, messageContent)
        }

        // Handle RESET command
        if (isResetCommand) {
            manageContextUseCase.resetContext(threadId)

            auditRepository.log(
                eventType = AuditEventType.CONTEXT_RESET,
                threadId = threadId,
                details = mapOf(Constants.AUDIT_KEY_PHONE to phoneNumber)
            )

            return ProcessResult(
                threadId = threadId,
                isResetCommand = true,
                urgencyLevel = UrgencyLevel.NORMAL
            )
        }

        // Detect urgency
        val urgencyLevel = detectUrgencyUseCase(messageContent)

        // Update thread urgency if elevated
        val currentThread = threadRepository.getThreadById(threadId)
        if (currentThread != null && urgencyLevel.ordinal > currentThread.urgencyLevel.ordinal) {
            threadRepository.updateThreadUrgency(threadId, urgencyLevel)

            auditRepository.log(
                eventType = AuditEventType.URGENCY_DETECTED,
                threadId = threadId,
                details = mapOf(
                    Constants.AUDIT_KEY_URGENCY to urgencyLevel.name,
                    Constants.AUDIT_KEY_MESSAGE to messageContent
                )
            )
        }

        // Save incoming message
        smsRepository.saveMessage(
            threadId = threadId,
            content = messageContent,
            direction = MessageDirection.INCOMING,
            status = MessageStatus.SENT // Incoming messages are already received
        )

        // Update thread message count and timestamp
        threadRepository.incrementMessageCount(threadId, System.currentTimeMillis())

        // Log to audit trail
        auditRepository.log(
            eventType = AuditEventType.SMS_RECEIVED,
            threadId = threadId,
            details = mapOf(
                Constants.AUDIT_KEY_PHONE to phoneNumber,
                Constants.AUDIT_KEY_MESSAGE to messageContent,
                Constants.AUDIT_KEY_URGENCY to urgencyLevel.name
            )
        )

        return ProcessResult(
            threadId = threadId,
            isResetCommand = false,
            urgencyLevel = urgencyLevel
        )
    }

    private suspend fun createNewThread(phoneNumber: String, firstMessage: String): Long {
        val now = System.currentTimeMillis()
        val urgencyLevel = detectUrgencyUseCase(firstMessage)

        return threadRepository.createThread(
            phoneNumber = phoneNumber,
            status = ThreadStatus.ACTIVE,
            urgencyLevel = urgencyLevel
        )
    }

    data class ProcessResult(
        val threadId: Long,
        val isResetCommand: Boolean,
        val urgencyLevel: UrgencyLevel
    )
}
