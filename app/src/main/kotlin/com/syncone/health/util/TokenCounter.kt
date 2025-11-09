package com.syncone.health.util

/**
 * Token counter for context management.
 * Phase 1: Simple word-based estimation (1 word ≈ 1.3 tokens).
 * Phase 2: Replace with actual tokenizer for MedGemma.
 */
object TokenCounter {

    /**
     * Estimate token count from text.
     * Rough approximation: word count * 1.3
     */
    fun estimate(text: String): Int {
        val wordCount = text.trim().split(Regex("\\s+")).size
        return (wordCount * 1.3).toInt()
    }

    /**
     * Estimate total tokens from multiple turns.
     */
    fun estimateTotal(texts: List<String>): Int {
        return texts.sumOf { estimate(it) }
    }

    /**
     * Check if adding new text would exceed limit.
     */
    fun wouldExceedLimit(currentTokens: Int, newText: String, limit: Int): Boolean {
        return currentTokens + estimate(newText) > limit
    }
}
