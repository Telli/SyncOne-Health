package com.syncone.health.domain.usecase.inference

import com.syncone.health.data.local.ml.InferenceResult
import com.syncone.health.data.local.ml.MedGemmaInference
import com.syncone.health.data.local.ml.VectorStoreManager
import com.syncone.health.domain.model.PromptContext
import com.syncone.health.domain.usecase.BuildPromptUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * Executes local inference using MedGemma-4B with RAG.
 */
class LocalInferenceUseCase @Inject constructor(
    private val medgemma: MedGemmaInference,
    private val vectorStore: VectorStoreManager,
    private val buildPrompt: BuildPromptUseCase
) {

    suspend operator fun invoke(context: PromptContext): InferenceResult {
        try {
            // 1. Retrieve relevant guidelines via RAG
            val ragChunks = vectorStore.search(
                query = context.userMessage,
                topK = 2,
                threshold = 0.6f
            )

            Timber.d("Retrieved ${ragChunks.size} RAG chunks")

            // 2. Build enhanced prompt with RAG context
            val ragContext = ragChunks.joinToString("\n\n") {
                "Reference: ${it.content}"
            }

            val fullPrompt = buildEnhancedPrompt(context, ragContext)

            // 3. Generate response
            val result = medgemma.generate(
                prompt = fullPrompt,
                maxTokens = 120, // Limit for SMS
                temperature = 0.7f
            )

            // 4. Format for SMS (≤480 chars)
            val formatted = formatForSms(result.response)

            // 5. Add disclaimer if needed
            val final = addDisclaimerIfNeeded(formatted, context.userMessage)

            return result.copy(response = final)

        } catch (e: Exception) {
            Timber.e(e, "Local inference failed")
            throw e
        }
    }

    private fun buildEnhancedPrompt(context: PromptContext, ragContext: String): String {
        return buildString {
            append(SYSTEM_PROMPT)
            append("\n\n")

            if (ragContext.isNotEmpty()) {
                append("Medical Guidelines:\n")
                append(ragContext)
                append("\n\n")
            }

            if (context.conversationHistory.isNotEmpty()) {
                append("Conversation History:\n")
                context.conversationHistory.takeLast(3).forEach { turn ->
                    append("${turn.role}: ${turn.content}\n")
                }
                append("\n")
            }

            append("User: ${context.userMessage}\n")
            append("Assistant:")
        }
    }

    private fun formatForSms(text: String): String {
        if (text.length <= 480) return text

        // Truncate at sentence boundary
        val sentences = text.split(". ")
        val truncated = StringBuilder()

        for (sentence in sentences) {
            if ((truncated.length + sentence.length + 2) <= 460) {
                truncated.append(sentence).append(". ")
            } else {
                break
            }
        }

        return truncated.toString().trim()
    }

    private fun addDisclaimerIfNeeded(text: String, query: String): String {
        val needsDisclaimer = query.lowercase().let {
            "diagnose" in it || "treatment" in it || "medication" in it
        }

        return if (needsDisclaimer && !text.contains("consult")) {
            "$text\n\nConsult a healthcare provider for proper diagnosis."
        } else {
            text
        }
    }

    companion object {
        private const val SYSTEM_PROMPT = """You are a medical assistant for rural health workers in low-resource settings.

Guidelines:
- Provide clear, actionable health advice
- Use simple language (6th grade reading level)
- Encourage seeking professional care for serious symptoms
- Stay within 480 characters total
- If emergency symptoms (bleeding, unconscious, severe pain), urge immediate care

CRITICAL: Flag emergencies clearly. This is informational only, not a diagnosis."""
    }
}
