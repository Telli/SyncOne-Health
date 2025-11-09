package com.syncone.health.domain.model

/**
 * Domain model for audit trail.
 * Tracks all CHW actions for compliance.
 */
data class AuditLog(
    val id: Long,
    val eventType: AuditEventType,
    val chwId: String?,
    val threadId: Long?,
    val details: Map<String, Any>,
    val timestamp: Long,
    val synced: Boolean
)

enum class AuditEventType {
    SMS_RECEIVED,
    SMS_SENT,
    MANUAL_REPLY,
    FEEDBACK_RATED,
    FEEDBACK_EDITED,
    THREAD_ARCHIVED,
    THREAD_EXPORTED,
    APP_UNLOCKED,
    URGENCY_DETECTED,
    CONTEXT_RESET
}
