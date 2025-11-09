package com.syncone.health.data.local.ml

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages ML model lifecycle (loading, caching, health checks).
 * Handles initialization and cleanup of TensorFlow Lite models.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var medgemma: MedGemmaInference? = null
    private var embeddings: EmbeddingModel? = null
    private var isInitialized = false

    private val _modelState = MutableStateFlow<ModelState>(ModelState.Uninitialized)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Initialize all ML models.
     * Called once at app startup.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Timber.d("Models already initialized")
            return@withContext
        }

        _modelState.value = ModelState.Loading

        try {
            Timber.i("Initializing ML models...")

            // Initialize models in parallel
            coroutineScope {
                val medgemmaJob = async {
                    MedGemmaInference(context).also { it.initialize() }
                }

                val embeddingsJob = async {
                    EmbeddingModel(context).also { it.initialize() }
                }

                medgemma = medgemmaJob.await()
                embeddings = embeddingsJob.await()
            }

            isInitialized = true
            _modelState.value = ModelState.Ready

            Timber.i("All ML models initialized successfully")

        } catch (e: Exception) {
            Timber.e(e, "Model initialization failed")
            _modelState.value = ModelState.Error(e.message ?: "Unknown error")

            // Don't throw - allow app to run without models (will use cloud fallback)
        }
    }

    /**
     * Get MedGemma inference instance.
     */
    fun getMedGemma(): MedGemmaInference {
        return medgemma ?: throw IllegalStateException("MedGemma not initialized. Check model files.")
    }

    /**
     * Get embedding model instance.
     */
    fun getEmbeddings(): EmbeddingModel {
        return embeddings ?: throw IllegalStateException("Embeddings not initialized. Check model files.")
    }

    /**
     * Check if local models are ready.
     */
    fun isLocalModelReady(): Boolean = isInitialized && medgemma != null

    /**
     * Check if embedding model is ready.
     */
    fun isEmbeddingReady(): Boolean = isInitialized && embeddings != null

    /**
     * Release models and free memory.
     * Called when app is backgrounded or low on memory.
     */
    fun release() {
        Timber.d("Releasing ML models...")

        medgemma?.close()
        embeddings?.close()

        medgemma = null
        embeddings = null
        isInitialized = false

        _modelState.value = ModelState.Uninitialized

        Timber.i("ML models released")
    }

    /**
     * Clean up resources.
     */
    fun destroy() {
        release()
        scope.cancel()
    }
}

/**
 * Model loading state.
 */
sealed class ModelState {
    object Uninitialized : ModelState()
    object Loading : ModelState()
    object Ready : ModelState()
    data class Error(val message: String) : ModelState()
}
