package com.syncone.health.domain.model.enums

enum class ThreadStatus {
    ACTIVE,    // Currently being processed
    RESOLVED,  // Issue resolved, conversation ended
    ARCHIVED   // Expired or manually archived
}
