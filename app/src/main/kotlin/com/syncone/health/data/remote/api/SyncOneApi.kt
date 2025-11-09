package com.syncone.health.data.remote.api

import com.google.gson.annotations.SerializedName
import com.syncone.health.domain.model.ConversationTurn
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * API interface for SyncOne Health cloud backend.
 * ASP.NET Core + Microsoft Agent Framework.
 */
interface SyncOneApi {

    @POST("api/v1/inference")
    suspend fun inference(@Body request: InferenceRequest): InferenceResponse

    @POST("api/v1/feedback")
    suspend fun submitFeedback(@Body feedback: FeedbackEntry): FeedbackResponse

    @GET("api/v1/health")
    suspend fun healthCheck(): HealthResponse
}

/**
 * Inference request payload.
 */
data class InferenceRequest(
    @SerializedName("message")
    val message: String,

    @SerializedName("conversation_history")
    val conversationHistory: List<ConversationTurn>,

    @SerializedName("metadata")
    val metadata: RequestMetadata
)

/**
 * Request metadata.
 */
data class RequestMetadata(
    @SerializedName("phone_number")
    val phoneNumber: String? = null, // Optional for privacy

    @SerializedName("urgency_level")
    val urgencyLevel: String,

    @SerializedName("token_count")
    val tokenCount: Int,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Inference response from cloud.
 */
data class InferenceResponse(
    @SerializedName("message")
    val message: String,

    @SerializedName("confidence")
    val confidence: Float,

    @SerializedName("agent_used")
    val agentUsed: String, // "PrimaryCare", "Maternal", "RxSafety", etc.

    @SerializedName("urgency_level")
    val urgencyLevel: String,

    @SerializedName("sources")
    val sources: List<String> = emptyList(),

    @SerializedName("requires_review")
    val requiresReview: Boolean = false
)

/**
 * Feedback entry for RLHF.
 */
data class FeedbackEntry(
    @SerializedName("message_id")
    val messageId: Long,

    @SerializedName("original_response")
    val originalResponse: String,

    @SerializedName("edited_response")
    val editedResponse: String?,

    @SerializedName("rating")
    val rating: String, // "GOOD" or "BAD"

    @SerializedName("chw_id")
    val chwId: String,

    @SerializedName("timestamp")
    val timestamp: Long
)

/**
 * Feedback response.
 */
data class FeedbackResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String
)

/**
 * Health check response.
 */
data class HealthResponse(
    @SerializedName("status")
    val status: String, // "healthy", "degraded", "down"

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("version")
    val version: String = "1.0.0"
)
