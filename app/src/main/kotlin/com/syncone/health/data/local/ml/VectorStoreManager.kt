package com.syncone.health.data.local.ml

import com.google.gson.Gson
import com.syncone.health.data.local.database.SyncOneDatabase
import com.syncone.health.data.local.database.entity.VectorChunkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Vector store using SQLite with in-memory cosine similarity search.
 * Stores medical guideline embeddings for RAG retrieval.
 *
 * For production at scale, consider:
 * - FAISS for approximate nearest neighbor search
 * - sqlite-vec extension for native vector search
 * - Separate vector database (Pinecone, Weaviate, etc.)
 */
@Singleton
class VectorStoreManager @Inject constructor(
    private val database: SyncOneDatabase,
    private val embeddingModel: EmbeddingModel,
    private val gson: Gson
) {

    companion object {
        private const val EMBEDDING_DIM = 384
        private const val TOP_K = 3
        private const val DEFAULT_THRESHOLD = 0.5f
    }

    /**
     * Index a document chunk with embedding.
     *
     * @param content Text content to index
     * @param metadata Additional metadata (source, page, etc.)
     */
    suspend fun indexChunk(
        content: String,
        metadata: Map<String, String> = emptyMap()
    ) = withContext(Dispatchers.IO) {
        try {
            Timber.d("Indexing chunk: ${content.take(50)}...")

            // Generate embedding
            val embedding = embeddingModel.embed(content)

            // Create entity
            val entity = VectorChunkEntity(
                content = content,
                embedding = embedding.joinToString(","),
                source = metadata["source"] ?: "unknown",
                metadata = gson.toJson(metadata)
            )

            // Insert into database
            database.vectorChunkDao().insert(entity)

            Timber.d("Chunk indexed successfully")

        } catch (e: Exception) {
            Timber.e(e, "Failed to index chunk")
            throw e
        }
    }

    /**
     * Index multiple chunks in batch.
     *
     * @param chunks List of (content, metadata) pairs
     */
    suspend fun indexBatch(chunks: List<Pair<String, Map<String, String>>>) {
        withContext(Dispatchers.IO) {
            Timber.d("Batch indexing ${chunks.size} chunks...")

            val entities = chunks.map { (content, metadata) ->
                val embedding = embeddingModel.embed(content)

                VectorChunkEntity(
                    content = content,
                    embedding = embedding.joinToString(","),
                    source = metadata["source"] ?: "unknown",
                    metadata = gson.toJson(metadata)
                )
            }

            database.vectorChunkDao().insertAll(entities)

            Timber.d("Batch indexed ${entities.size} chunks successfully")
        }
    }

    /**
     * Search for relevant chunks using cosine similarity.
     *
     * @param query Search query
     * @param topK Number of results to return
     * @param threshold Minimum similarity score (0.0 to 1.0)
     * @return List of relevant chunks with similarity scores
     */
    suspend fun search(
        query: String,
        topK: Int = TOP_K,
        threshold: Float = DEFAULT_THRESHOLD
    ): List<RagChunk> = withContext(Dispatchers.Default) {
        try {
            Timber.d("Searching for: $query")

            // Generate query embedding
            val queryEmbedding = embeddingModel.embed(query)

            // Get all chunks from database
            val allChunks = database.vectorChunkDao().getAllChunks()

            if (allChunks.isEmpty()) {
                Timber.w("No chunks in vector store")
                return@withContext emptyList()
            }

            // Calculate cosine similarity for each chunk
            val scored = allChunks.mapNotNull { chunk ->
                try {
                    val chunkEmbedding = chunk.embedding
                        .split(",")
                        .map { it.toFloat() }
                        .toFloatArray()

                    val similarity = cosineSimilarity(queryEmbedding, chunkEmbedding)

                    chunk to similarity

                } catch (e: Exception) {
                    Timber.w("Failed to parse embedding for chunk ${chunk.id}")
                    null
                }
            }

            // Filter by threshold and sort by similarity
            val results = scored
                .filter { it.second >= threshold }
                .sortedByDescending { it.second }
                .take(topK)
                .map { (chunk, score) ->
                    val metadata = try {
                        gson.fromJson(chunk.metadata, Map::class.java) as Map<String, String>
                    } catch (e: Exception) {
                        emptyMap()
                    }

                    RagChunk(
                        content = chunk.content,
                        source = chunk.source,
                        score = score,
                        metadata = metadata
                    )
                }

            Timber.d("Found ${results.size} relevant chunks (threshold: $threshold)")

            return@withContext results

        } catch (e: Exception) {
            Timber.e(e, "Search failed")
            return@withContext emptyList()
        }
    }

    /**
     * Get total number of indexed chunks.
     */
    suspend fun getChunkCount(): Int {
        return database.vectorChunkDao().getChunkCount()
    }

    /**
     * Delete all chunks from a specific source.
     */
    suspend fun deleteSource(source: String) {
        database.vectorChunkDao().deleteBySource(source)
        Timber.d("Deleted chunks from source: $source")
    }

    /**
     * Clear all indexed chunks.
     */
    suspend fun clearAll() {
        database.vectorChunkDao().deleteAll()
        Timber.d("Cleared all vector chunks")
    }

    /**
     * Calculate cosine similarity between two vectors.
     *
     * @param a First vector
     * @param b Second vector
     * @return Similarity score (0.0 to 1.0, higher is more similar)
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have same dimension" }

        val dotProduct = a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }
        val normA = sqrt(a.sumOf { (it * it).toDouble() })
        val normB = sqrt(b.sumOf { (it * it).toDouble() })

        return if (normA == 0.0 || normB == 0.0) {
            0f
        } else {
            (dotProduct / (normA * normB)).toFloat().coerceIn(0f, 1f)
        }
    }
}

/**
 * Retrieved chunk from RAG search.
 */
data class RagChunk(
    val content: String,
    val source: String,
    val score: Float,
    val metadata: Map<String, String>
)
