package com.syncone.health.data.remote

import com.google.gson.GsonBuilder
import com.syncone.health.BuildConfig
import com.syncone.health.data.remote.api.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud API client for SyncOne Health backend.
 * Connects to ASP.NET Core + Microsoft Agent Framework.
 */
@Singleton
class AgentClient @Inject constructor() {

    private val baseUrl = BuildConfig.API_BASE_URL

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create(
            GsonBuilder()
                .setLenient()
                .create()
        ))
        .client(createOkHttpClient())
        .build()

    private val api = retrofit.create(SyncOneApi::class.java)

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // Long for LLM inference
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(createLoggingInterceptor())
            .addInterceptor(AuthInterceptor())
            .build()
    }

    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            Timber.tag("HTTP").d(message)
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        }
    }

    /**
     * Call cloud inference endpoint.
     */
    suspend fun inference(request: InferenceRequest): Result<InferenceResponse> {
        return try {
            val response = api.inference(request)
            Result.success(response)
        } catch (e: Exception) {
            Timber.e(e, "Cloud inference failed")
            Result.failure(e)
        }
    }

    /**
     * Submit feedback for RLHF.
     */
    suspend fun submitFeedback(feedback: FeedbackEntry): Result<FeedbackResponse> {
        return try {
            val response = api.submitFeedback(feedback)
            Result.success(response)
        } catch (e: Exception) {
            Timber.e(e, "Feedback submission failed")
            Result.failure(e)
        }
    }

    /**
     * Check backend health.
     */
    suspend fun healthCheck(): Result<HealthResponse> {
        return try {
            val response = api.healthCheck()
            Result.success(response)
        } catch (e: Exception) {
            Timber.w("Health check failed: ${e.message}")
            Result.failure(e)
        }
    }
}

/**
 * Adds authorization header to requests.
 */
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${getApiKey()}")
            .addHeader("X-Client-Version", BuildConfig.VERSION_NAME)
            .addHeader("X-Platform", "Android")
            .build()

        return chain.proceed(request)
    }

    private fun getApiKey(): String {
        // API key from BuildConfig (set during build)
        // For production, ensure API_KEY is set via gradle.properties or environment variable
        return BuildConfig.API_KEY.ifEmpty { "PLACEHOLDER_API_KEY" }
    }
}
