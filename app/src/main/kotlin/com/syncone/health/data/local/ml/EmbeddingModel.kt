package com.syncone.health.data.local.ml

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.sqrt

/**
 * BGE-small-en embedding model (int8 quantized).
 * Used for semantic search in medical guidelines (RAG).
 *
 * Model: BAAI/bge-small-en-v1.5
 * Dimensions: 384
 * Input: Text (max 512 tokens)
 * Output: Float vector (384-dim)
 *
 * Model file expected:
 * - assets/models/bge_small_en_int8.tflite
 * - assets/models/bge_vocab.txt
 */
class EmbeddingModel(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var tokenizer: SimpleTokenizer? = null

    companion object {
        private const val MODEL_PATH = "models/bge_small_en_int8.tflite"
        private const val VOCAB_PATH = "models/bge_vocab.txt"
        private const val EMBEDDING_DIM = 384
        private const val MAX_LENGTH = 512
    }

    /**
     * Initialize embedding model and tokenizer.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Timber.d("Initializing embedding model...")

            val options = Interpreter.Options().apply {
                setNumThreads(2)
                setUseXNNPACK(true)
            }

            // Load model
            val modelBuffer = try {
                FileUtil.loadMappedFile(context, MODEL_PATH)
            } catch (e: Exception) {
                Timber.w("Embedding model file not found at $MODEL_PATH: ${e.message}")
                throw ModelInitializationException(
                    "Embedding model not found. Place bge_small_en_int8.tflite in assets/models/",
                    e
                )
            }

            interpreter = Interpreter(modelBuffer, options)

            // Load tokenizer
            tokenizer = SimpleTokenizer(context, VOCAB_PATH)

            Timber.i("Embedding model initialized successfully")

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize embedding model")
            throw ModelInitializationException("Failed to load embedding model", e)
        }
    }

    /**
     * Generate embedding for text.
     *
     * @param text Input text to embed
     * @return 384-dimensional embedding vector (normalized for cosine similarity)
     */
    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        val interpreter = this@EmbeddingModel.interpreter
            ?: throw IllegalStateException("Embedding model not initialized")

        val tokenizer = this@EmbeddingModel.tokenizer
            ?: throw IllegalStateException("Tokenizer not initialized")

        try {
            // Tokenize
            val inputIds = tokenizer.encode(text, maxLength = MAX_LENGTH)
            val attentionMask = IntArray(inputIds.size) { if (inputIds[it] != 0) 1 else 0 }

            // Prepare input tensors
            val inputTensor = Array(1) { inputIds }
            val maskTensor = Array(1) { attentionMask }

            // Prepare output tensor
            val outputTensor = Array(1) { FloatArray(EMBEDDING_DIM) }

            // Run inference
            val inputs = arrayOf<Any>(inputTensor, maskTensor)
            val outputs = mutableMapOf<Int, Any>(0 to outputTensor)
            interpreter.runForMultipleInputsOutputs(inputs, outputs)

            // Normalize (for cosine similarity)
            val embedding = outputTensor[0]
            val norm = sqrt(embedding.fold(0.0) { acc, value -> acc + (value * value).toDouble() }).toFloat()

            if (norm == 0f || norm.isNaN() || norm.isInfinite()) {
                return@withContext FloatArray(EMBEDDING_DIM) { 0f }
            }

            return@withContext FloatArray(embedding.size) { index ->
                embedding[index] / norm
            }

        } catch (e: Exception) {
            Timber.e(e, "Embedding generation failed")
            // Return zero vector on failure
            return@withContext FloatArray(EMBEDDING_DIM) { 0f }
        }
    }

    /**
     * Batch embed multiple texts (for document ingestion).
     *
     * @param texts List of texts to embed
     * @return List of embedding vectors
     */
    suspend fun embedBatch(texts: List<String>): List<FloatArray> {
        return texts.map { embed(it) }
    }

    /**
     * Close interpreter and release resources.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        Timber.d("Embedding model released")
    }
}

/**
 * Simple tokenizer for embedding model.
 * Loads vocabulary from text file.
 */
class SimpleTokenizer(
    private val context: Context,
    private val vocabPath: String
) {
    private lateinit var vocab: Map<String, Int>

    private val padTokenId = 0
    private val clsTokenId = 101 // [CLS] token
    private val sepTokenId = 102 // [SEP] token
    private val unkTokenId = 100 // [UNK] token

    init {
        loadVocabulary()
    }

    /**
     * Load vocabulary from text file.
     * Format: one token per line, line number is token ID.
     */
    private fun loadVocabulary() {
        try {
            val inputStream = context.assets.open(vocabPath)
            val reader = BufferedReader(InputStreamReader(inputStream))

            vocab = reader.useLines { lines ->
                lines.mapIndexed { index, token -> token.trim() to index }
                    .toMap()
            }

            Timber.d("Loaded vocabulary: ${vocab.size} tokens")

        } catch (e: Exception) {
            Timber.w("Failed to load vocabulary from $vocabPath, using fallback")
            // Fallback vocabulary
            vocab = createFallbackVocab()
        }
    }

    /**
     * Encode text to token IDs.
     */
    fun encode(text: String, maxLength: Int = 512): IntArray {
        val tokens = tokenize(text)

        val tokenIds = mutableListOf<Int>()
        tokenIds.add(clsTokenId) // Add [CLS] at start

        for (token in tokens) {
            val id = vocab[token] ?: vocab[token.lowercase()] ?: unkTokenId
            tokenIds.add(id)

            if (tokenIds.size >= maxLength - 1) break
        }

        tokenIds.add(sepTokenId) // Add [SEP] at end

        // Pad to maxLength
        while (tokenIds.size < maxLength) {
            tokenIds.add(padTokenId)
        }

        return tokenIds.take(maxLength).toIntArray()
    }

    /**
     * Simple whitespace tokenization.
     */
    private fun tokenize(text: String): List<String> {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
    }

    /**
     * Fallback vocabulary for testing.
     */
    private fun createFallbackVocab(): Map<String, Int> {
        val tokens = listOf(
            "[PAD]", "[UNK]", "[CLS]", "[SEP]", "[MASK]"
        ) + "abcdefghijklmnopqrstuvwxyz".map { it.toString() } +
                (0..9).map { it.toString() }

        return tokens.mapIndexed { index, token -> token to index }.toMap()
    }
}
