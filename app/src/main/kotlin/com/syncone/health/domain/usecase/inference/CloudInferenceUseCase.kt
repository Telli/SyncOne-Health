package com.syncone.health.domain.usecase.inference

import com.syncone.health.data.local.ml.InferenceException
import com.syncone.health.data.local.ml.InferenceResult
import com.syncone.health.data.remote.AgentClient
import com.syncone.health.data.remote.api.InferenceRequest
import com.syncone.health.data.remote.api.RequestMetadata
import com.syncone.health.domain.model.PromptContext
import com.syncone.health.domain.usecase.DetectUrgencyUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * Cloud inference using ASP.NET backend + MedGemma-27B.
 * Routes to specialist agents (PrimaryCare, Maternal, RxSafety, Referral).
 */
class CloudInferenceUseCase @Inject constructor(
    private val agentClient: AgentClient,
    private val detectUrgency: DetectUrgencyUseCase
) {

    suspend operator fun invoke(context: PromptContext): InferenceResult {
        try {
            Timber.d("Calling cloud inference...")

            val request = InferenceRequest(
                message = context.userMessage,
                conversationHistory = context.conversationHistory,
                metadata = RequestMetadata(
                    phoneNumber = null, // Don't send PII by default
                    urgencyLevel = detectUrgency(context.userMessage).name,
                    tokenCount = context.tokensUsed
                )
            )

            val result = agentClient.inference(request)

            return result.fold(
                onSuccess = { response ->
                    InferenceResult(
                        response = response.message,
                        confidence = response.confidence,
                        tokensGenerated = response.message.split("\\s+".toRegex()).size,
                        inferenceTimeMs = 0, // Server-side timing not returned
                        model = "MedGemma-27B (${response.agentUsed})"
                    )
                },
                onFailure = { error ->
                    throw InferenceException("Cloud inference failed: ${error.message}", error)
                }
            )

        } catch (e: Exception) {
            Timber.e(e, "Cloud inference failed")
            throw InferenceException("Cloud inference unavailable", e)
        }
    }
}
