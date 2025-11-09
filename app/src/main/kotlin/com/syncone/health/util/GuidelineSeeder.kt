package com.syncone.health.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.syncone.health.data.local.ml.VectorStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Seeds medical guidelines into vector store for RAG.
 * Run once on first app launch or when guidelines are updated.
 */
class GuidelineSeeder @Inject constructor(
    private val context: Context,
    private val vectorStore: VectorStoreManager,
    private val gson: Gson
) {

    /**
     * Seed medical guidelines from JSON file.
     */
    suspend fun seedGuidelines(force: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            // Check if already seeded
            val existingCount = vectorStore.getChunkCount()
            if (existingCount > 0 && !force) {
                Timber.d("Guidelines already seeded ($existingCount chunks)")
                return@withContext
            }

            Timber.i("Seeding medical guidelines...")

            // Load guidelines from assets
            val guidelines = loadGuidelinesFromAssets()

            // Index in batch
            val chunks = guidelines.map { guideline ->
                guideline.content to guideline.metadata
            }

            vectorStore.indexBatch(chunks)

            Timber.i("Successfully seeded ${guidelines.size} medical guidelines")

        } catch (e: Exception) {
            Timber.e(e, "Failed to seed guidelines")
        }
    }

    /**
     * Load guidelines from JSON file in assets.
     */
    private fun loadGuidelinesFromAssets(): List<GuidelineItem> {
        return try {
            val inputStream = context.assets.open("medical_guidelines.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }

            val type = object : TypeToken<List<GuidelineItem>>() {}.type
            gson.fromJson(jsonString, type)

        } catch (e: Exception) {
            Timber.e(e, "Failed to load guidelines from assets")
            emptyList()
        }
    }

    /**
     * Guideline item from JSON.
     */
    data class GuidelineItem(
        val content: String,
        val metadata: Map<String, String>
    )
}
