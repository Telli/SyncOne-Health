package com.syncone.health.domain.model.enums

enum class MessageStatus {
    PENDING,  // Queued for sending
    SENT,     // Successfully delivered
    FAILED    // Send failed, needs retry
}
