package com.syncone.health.domain.usecase

import com.syncone.health.domain.model.enums.UrgencyLevel
import com.syncone.health.util.Constants
import javax.inject.Inject

/**
 * Detects urgency level from message content using keyword matching.
 * Phase 1: Simple keyword-based detection.
 * Phase 2: ML-based classification.
 */
class DetectUrgencyUseCase @Inject constructor() {

    operator fun invoke(message: String): UrgencyLevel {
        val normalized = message.lowercase()

        // Check for critical keywords first
        val hasCritical = Constants.CRITICAL_KEYWORDS.any { keyword ->
            normalized.contains(keyword)
        }
        if (hasCritical) {
            return UrgencyLevel.CRITICAL
        }

        // Check for urgent keywords
        val hasUrgent = Constants.URGENT_KEYWORDS.any { keyword ->
            normalized.contains(keyword)
        }
        if (hasUrgent) {
            return UrgencyLevel.URGENT
        }

        return UrgencyLevel.NORMAL
    }
}
