package com.syncone.health.data.local.ml

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Tokenizer for MedGemma models.
 * Supports SentencePiece or custom BPE vocabulary.
 *
 * Model files expected:
 * - assets/models/medgemma_vocab.json - Vocabulary mapping
 *
 * Format: { "token": id, ... }
 */
class MedGemmaTokenizer(
    private val context: Context,
    private val vocabPath: String = "models/medgemma_vocab.json"
) {
    private lateinit var vocab: Map<String, Int>
    private lateinit var reverseVocab: Map<Int, String>

    val vocabSize: Int get() = vocab.size
    val eosTokenId: Int = 2 // Standard EOS token
    val padTokenId: Int = 0 // PAD token
    val bosTokenId: Int = 1 // BOS token
    val unkTokenId: Int = 3 // Unknown token

    init {
        loadVocabulary()
    }

    /**
     * Load vocabulary from JSON file.
     */
    private fun loadVocabulary() {
        try {
            val inputStream = context.assets.open(vocabPath)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }

            val jsonElement = Json.parseToJsonElement(jsonString).jsonObject

            vocab = jsonElement.mapValues { (_, value) ->
                value.jsonPrimitive.int
            }

            reverseVocab = vocab.entries.associate { it.value to it.key }

            Timber.d("Loaded vocabulary: ${vocab.size} tokens")

        } catch (e: Exception) {
            Timber.w("Failed to load vocabulary from $vocabPath, using fallback")
            // Fallback to basic vocabulary for testing without model file
            vocab = createFallbackVocab()
            reverseVocab = vocab.entries.associate { it.value to it.key }
        }
    }

    /**
     * Encode text to token IDs.
     *
     * @param text Input text to encode
     * @param maxLength Maximum sequence length (will truncate if exceeded)
     * @return Array of token IDs
     */
    fun encode(text: String, maxLength: Int = 512): IntArray {
        val tokens = tokenize(text)

        val tokenIds = mutableListOf(bosTokenId) // Add BOS token

        for (token in tokens) {
            val id = vocab[token] ?: vocab[token.lowercase()] ?: unkTokenId
            tokenIds.add(id)

            if (tokenIds.size >= maxLength - 1) {
                Timber.d("Input truncated to $maxLength tokens")
                break
            }
        }

        return tokenIds.take(maxLength).toIntArray()
    }

    /**
     * Decode token IDs to text.
     *
     * @param tokenIds List of token IDs to decode
     * @return Decoded text
     */
    fun decode(tokenIds: List<Int>): String {
        return tokenIds
            .filter { it != padTokenId && it != bosTokenId && it != eosTokenId }
            .mapNotNull { reverseVocab[it] }
            .joinToString(" ")
            .replace(" ##", "") // Remove BPE artifacts
            .trim()
    }

    /**
     * Simple whitespace tokenization.
     * NOTE: For production with actual MedGemma model files, this should be replaced
     * with SentencePiece tokenizer matching the model's training tokenization.
     * Current implementation works as fallback for testing without model files.
     */
    private fun tokenize(text: String): List<String> {
        // Simple word-level tokenization (acceptable for fallback/testing)
        // Production: Replace with SentencePiece when model files are added
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
    }

    /**
     * Create fallback vocabulary for testing without model file.
     * In production, this should never be used - real vocab file required.
     */
    private fun createFallbackVocab(): Map<String, Int> {
        val commonMedicalTerms = listOf(
            "<pad>", "<bos>", "<eos>", "<unk>",
            "fever", "cough", "pain", "headache", "nausea", "vomiting",
            "diarrhea", "fatigue", "weakness", "dizzy", "bleeding",
            "swelling", "rash", "itch", "sore", "throat",
            "chest", "stomach", "back", "joint", "muscle",
            "high", "low", "severe", "mild", "chronic",
            "acute", "sudden", "gradual", "constant", "intermittent",
            "day", "days", "week", "weeks", "month",
            "year", "hour", "hours", "morning", "night",
            "pregnant", "pregnancy", "labor", "delivery", "baby",
            "child", "adult", "elderly", "male", "female",
            "medicine", "medication", "drug", "treatment", "therapy",
            "doctor", "hospital", "clinic", "health", "medical",
            "test", "exam", "diagnosis", "symptom", "condition",
            "blood", "pressure", "sugar", "temperature", "weight",
            "heart", "lung", "liver", "kidney", "brain",
            "i", "my", "have", "feel", "am", "is", "are",
            "the", "a", "an", "and", "or", "but", "for",
            "what", "how", "when", "where", "why", "can", "should"
        )

        return commonMedicalTerms.mapIndexed { index, term -> term to index }.toMap()
    }
}
