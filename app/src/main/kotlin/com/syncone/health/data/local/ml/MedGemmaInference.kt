package com.syncone.health.data.local.ml

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import timber.log.Timber
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * TensorFlow Lite wrapper for MedGemma-4B int4 quantized model.
 *
 * Model requirements:
 * - File: assets/models/medgemma_4b_int4.tflite (place in assets/models/)
 * - Input: token_ids (IntArray)
 * - Output: logits (FloatArray) or generated_ids (IntArray)
 * - Quantization: int4 dynamic range
 *
 * Performance targets:
 * - Inference time: <3s for 150 tokens on 4GB RAM device
 * - Memory usage: <800MB total
 */
class MedGemmaInference(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var tokenizer: MedGemmaTokenizer? = null

    companion object {
        private const val MODEL_PATH = "models/medgemma_4b_int4.tflite"
        private const val VOCAB_PATH = "models/medgemma_vocab.json"
        private const val MAX_SEQUENCE_LENGTH = 512
    }

    /**
     * Initialize model and tokenizer.
     * Must be called before generate().
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Timber.d("Initializing MedGemma model...")

            // Load model with GPU delegate if available
            val options = Interpreter.Options().apply {
                setNumThreads(4)

                // Try GPU delegate first
                val compatList = CompatibilityList()
                if (compatList.isDelegateSupportedOnThisDevice) {
                    try {
                        val delegateOptions = compatList.bestOptionsForThisDevice
                        addDelegate(GpuDelegate(delegateOptions))
                        Timber.d("Using GPU acceleration")
                    } catch (e: Exception) {
                        Timber.w("GPU delegate failed, falling back to CPU: ${e.message}")
                    }
                } else {
                    Timber.d("GPU not supported, using CPU")
                }

                // Enable XNNPack for CPU optimization
                setUseXNNPACK(true)
            }

            // Load model file
            val modelBuffer = try {
                FileUtil.loadMappedFile(context, MODEL_PATH)
            } catch (e: Exception) {
                Timber.e("Model file not found at $MODEL_PATH: ${e.message}")
                throw ModelInitializationException(
                    "Model file not found. Please place medgemma_4b_int4.tflite in assets/models/",
                    e
                )
            }

            interpreter = Interpreter(modelBuffer, options)

            // Load tokenizer
            tokenizer = MedGemmaTokenizer(context, VOCAB_PATH)

            Timber.i("MedGemma model initialized successfully")

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize MedGemma model")
            throw ModelInitializationException("Failed to load MedGemma model", e)
        }
    }

    /**
     * Generate response for medical query.
     *
     * @param prompt Full prompt with system message and context
     * @param maxTokens Maximum tokens to generate (default 150)
     * @param temperature Sampling temperature (default 0.7)
     * @return Generated text and confidence score
     */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 150,
        temperature: Float = 0.7f
    ): InferenceResult = withContext(Dispatchers.Default) {

        val interpreter = this@MedGemmaInference.interpreter
            ?: throw IllegalStateException("Model not initialized. Call initialize() first.")

        val tokenizer = this@MedGemmaInference.tokenizer
            ?: throw IllegalStateException("Tokenizer not initialized")

        try {
            val startTime = System.currentTimeMillis()

            // 1. Tokenize input
            val inputIds = tokenizer.encode(prompt, maxLength = MAX_SEQUENCE_LENGTH)

            if (inputIds.size >= MAX_SEQUENCE_LENGTH) {
                Timber.w("Input truncated to $MAX_SEQUENCE_LENGTH tokens")
            }

            // 2. Generate tokens autoregressively
            val outputIds = generateTokens(interpreter, inputIds, maxTokens, temperature, tokenizer)

            // 3. Decode output
            val generatedText = tokenizer.decode(outputIds)

            // 4. Post-process
            val cleaned = cleanResponse(generatedText, prompt)

            // 5. Calculate confidence score
            val confidence = calculateConfidence(outputIds.size, maxTokens)

            val inferenceTime = System.currentTimeMillis() - startTime
            Timber.d("Generated ${outputIds.size} tokens in ${inferenceTime}ms")

            InferenceResult(
                response = cleaned,
                confidence = confidence,
                tokensGenerated = outputIds.size,
                inferenceTimeMs = inferenceTime,
                model = "MedGemma-4B"
            )

        } catch (e: Exception) {
            Timber.e(e, "Inference failed")
            throw InferenceException("Failed to generate response", e)
        }
    }

    /**
     * Generate tokens autoregressively.
     * This is a simplified implementation - actual TFLite model may have different I/O.
     */
    private fun generateTokens(
        interpreter: Interpreter,
        inputIds: IntArray,
        maxTokens: Int,
        temperature: Float,
        tokenizer: MedGemmaTokenizer
    ): List<Int> {
        val outputIds = mutableListOf<Int>()
        var currentInput = inputIds.toMutableList()

        try {
            repeat(maxTokens) {
                // Prepare input tensor
                val inputArray = Array(1) { currentInput.toIntArray() }

                // Allocate output tensors
                // Note: Actual model output shape may differ
                val outputTensor = Array(1) { FloatArray(tokenizer.vocabSize) }

                // Run inference
                interpreter.run(inputArray, outputTensor)

                // Sample next token
                val nextToken = sampleToken(outputTensor[0], temperature)

                // Check for EOS
                if (nextToken == tokenizer.eosTokenId) {
                    Timber.d("EOS token generated, stopping")
                    return@repeat
                }

                outputIds.add(nextToken)
                currentInput.add(nextToken)

                // Prevent context overflow
                if (currentInput.size > MAX_SEQUENCE_LENGTH) {
                    currentInput = currentInput.takeLast(MAX_SEQUENCE_LENGTH).toMutableList()
                }
            }
        } catch (e: Exception) {
            Timber.w("Token generation stopped early: ${e.message}")
            // Return what we have so far
        }

        return outputIds
    }

    /**
     * Sample next token from logits using temperature.
     */
    private fun sampleToken(logits: FloatArray, temperature: Float): Int {
        // Apply temperature scaling
        val scaledLogits = logits.map { it / temperature }.toFloatArray()

        // Softmax
        val expLogits = scaledLogits.map { exp(it.toDouble()).toFloat() }
        val sumExp = expLogits.sum()
        val probs = expLogits.map { it / sumExp }

        // Sample from distribution
        val randomValue = Random.nextFloat()
        var cumulative = 0f

        for (i in probs.indices) {
            cumulative += probs[i]
            if (randomValue <= cumulative) {
                return i
            }
        }

        // Fallback to argmax
        return probs.indices.maxByOrNull { probs[it] } ?: 0
    }

    /**
     * Clean generated response.
     */
    private fun cleanResponse(generated: String, prompt: String): String {
        // Remove prompt echo if present
        var cleaned = generated.trim()

        // Try to remove the prompt from the start
        if (cleaned.startsWith(prompt, ignoreCase = true)) {
            cleaned = cleaned.substring(prompt.length).trim()
        }

        // Remove incomplete sentences at the end
        val lastPeriod = cleaned.lastIndexOf('.')
        if (lastPeriod > 0 && lastPeriod < cleaned.length - 10) {
            cleaned = cleaned.substring(0, lastPeriod + 1)
        }

        // Remove any XML/markdown artifacts
        cleaned = cleaned.replace(Regex("<[^>]+>"), "")
        cleaned = cleaned.replace(Regex("\\*\\*"), "")
        cleaned = cleaned.replace(Regex("```[a-z]*"), "")

        return cleaned.trim()
    }

    /**
     * Calculate confidence score based on generation metrics.
     */
    private fun calculateConfidence(generatedTokens: Int, maxTokens: Int): Float {
        // Simple heuristic: more tokens generated = higher confidence
        // In production, use actual token probabilities
        val completionRatio = generatedTokens.toFloat() / maxTokens.toFloat()

        return when {
            completionRatio < 0.1f -> 0.3f // Very short response
            completionRatio < 0.3f -> 0.6f // Short response
            completionRatio < 0.7f -> 0.8f // Medium response
            else -> 0.9f // Full response
        }.coerceIn(0f, 1f)
    }

    /**
     * Close interpreter and release resources.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        Timber.d("MedGemma model released")
    }
}

/**
 * Result from inference.
 */
data class InferenceResult(
    val response: String,
    val confidence: Float,
    val tokensGenerated: Int,
    val inferenceTimeMs: Long,
    val model: String
)

/**
 * Exception thrown when model initialization fails.
 */
class ModelInitializationException(message: String, cause: Throwable?) : Exception(message, cause)

/**
 * Exception thrown when inference fails.
 */
class InferenceException(message: String, cause: Throwable?) : Exception(message, cause)
