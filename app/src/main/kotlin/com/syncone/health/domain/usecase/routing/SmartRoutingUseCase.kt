package com.syncone.health.domain.usecase.routing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.syncone.health.data.local.ml.InferenceResult
import com.syncone.health.data.local.ml.ModelManager
import com.syncone.health.domain.model.PromptContext
import com.syncone.health.domain.model.RoutingDecision
import com.syncone.health.domain.usecase.inference.CloudInferenceUseCase
import com.syncone.health.domain.usecase.inference.LocalInferenceUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Smart routing between local and cloud inference.
 *
 * Routing Logic:
 * - LOCAL if: Simple symptoms, <300 tokens, no medications, offline, or model ready
 * - CLOUD if: Complex query, drug interactions, pregnancy, referral needed, >300 tokens
 *
 * Fallback: If cloud fails, try local. If local unavailable, use stub response.
 */
class SmartRoutingUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localInference: LocalInferenceUseCase,
    private val cloudInference: CloudInferenceUseCase,
    private val modelManager: ModelManager
) {

    suspend operator fun invoke(promptContext: PromptContext): InferenceResult {
        // Check model availability
        if (!modelManager.isLocalModelReady()) {
            Timber.w("Local model not ready, attempting cloud")
            return if (isOnline()) {
                cloudInference(promptContext)
            } else {
                createOfflineStubResponse()
            }
        }

        // Make routing decision
        val decision = decide(promptContext)

        Timber.d("Routing decision: $decision")

        return when (decision) {
            RoutingDecision.LOCAL -> {
                try {
                    localInference(promptContext)
                } catch (e: Exception) {
                    Timber.e(e, "Local inference failed")
                    if (isOnline()) {
                        Timber.d("Falling back to cloud")
                        cloudInference(promptContext)
                    } else {
                        createErrorResult("AI temporarily unavailable. Please try again.")
                    }
                }
            }

            RoutingDecision.CLOUD -> {
                if (isOnline()) {
                    try {
                        cloudInference(promptContext)
                    } catch (e: Exception) {
                        Timber.e(e, "Cloud inference failed, falling back to local")
                        runLocalFallback(promptContext, "[Limited info - connect for detailed advice]")
                    }
                } else {
                    Timber.d("Offline, using local inference")
                    runLocalFallback(promptContext, "[Offline mode - connect for comprehensive guidance]")
                }
            }
        }
    }

    /**
     * Decide whether to route to local or cloud.
     */
    private fun decide(context: PromptContext): RoutingDecision {
        val message = context.userMessage.lowercase()
        val tokenCount = context.tokensUsed

        // Cloud triggers (high complexity)
        val hasPregnancyQuery = PREGNANCY_KEYWORDS.any { it in message }
        val hasMedicationQuery = MEDICATION_KEYWORDS.any { it in message }
        val hasReferralQuery = REFERRAL_KEYWORDS.any { it in message }
        val hasChronicQuery = CHRONIC_KEYWORDS.any { it in message }

        val needsCloud = hasPregnancyQuery || hasMedicationQuery || hasReferralQuery ||
                hasChronicQuery || tokenCount > 300

        // Check complexity (multiple symptoms)
        val symptomCount = countSymptoms(message)
        val isComplex = symptomCount > 2

        return if (needsCloud || isComplex) {
            RoutingDecision.CLOUD
        } else {
            RoutingDecision.LOCAL
        }
    }

    /**
     * Count number of symptoms mentioned.
     */
    private fun countSymptoms(message: String): Int {
        return SYMPTOM_KEYWORDS.count { it in message }
    }

    /**
     * Check if device is online.
     */
    private fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun runLocalFallback(
        promptContext: PromptContext,
        suffix: String,
        confidenceScale: Float = 0.7f
    ): InferenceResult {
        return try {
            val localResult = localInference(promptContext)
            val trimmedSuffix = suffix.trim()
            val combinedResponse = buildString {
                append(localResult.response.trim())
                if (trimmedSuffix.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append(trimmedSuffix)
                }
            }.trim()

            localResult.copy(
                response = combinedResponse,
                confidence = (localResult.confidence * confidenceScale).coerceIn(0f, 1f)
            )
        } catch (fallbackError: Exception) {
            Timber.e(fallbackError, "Local fallback failed")
            createErrorResult("AI temporarily unavailable. Please try again.")
        }
    }

    /**
     * Create error result.
     */
    private fun createErrorResult(message: String): InferenceResult {
        return InferenceResult(
            response = message,
            confidence = 0f,
            tokensGenerated = 0,
            inferenceTimeMs = 0,
            model = "error"
        )
    }

    /**
     * Create offline stub response.
     */
    private fun createOfflineStubResponse(): InferenceResult {
        return InferenceResult(
            response = "Thank you for contacting SyncOne Health. Models are loading. Please try again shortly.",
            confidence = 0.5f,
            tokensGenerated = 0,
            inferenceTimeMs = 0,
            model = "stub"
        )
    }

    companion object {
        private val PREGNANCY_KEYWORDS = listOf(
            "pregnant", "pregnancy", "labor", "contractions", "prenatal",
            "antenatal", "delivery", "birth", "trimester"
        )

        private val MEDICATION_KEYWORDS = listOf(
            "drug", "medication", "medicine", "prescription", "dosage",
            "interaction", "pill", "tablet", "antibiotic"
        )

        private val REFERRAL_KEYWORDS = listOf(
            "hospital", "clinic", "doctor", "specialist", "emergency",
            "referral", "urgent care", "ambulance"
        )

        private val CHRONIC_KEYWORDS = listOf(
            "diabetes", "hypertension", "hiv", "tuberculosis", "tb",
            "asthma", "epilepsy", "cancer"
        )

        private val SYMPTOM_KEYWORDS = listOf(
            "fever", "headache", "cough", "pain", "vomiting", "diarrhea",
            "nausea", "fatigue", "weakness", "dizzy", "swelling", "rash",
            "bleeding", "sore throat", "chest pain", "stomach pain"
        )
    }
}
