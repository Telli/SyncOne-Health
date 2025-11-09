package com.syncone.health.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Vector chunk entity for RAG (Retrieval-Augmented Generation).
 * Stores medical guideline chunks with embeddings for semantic search.
 *
 * Embedding stored as comma-separated string for simplicity.
 * For large-scale deployment, consider using FAISS or sqlite-vec extension.
 */
@Entity(
    tableName = "vector_chunks",
    indices = [Index("source"), Index("created_at")]
)
data class VectorChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "content")
    val content: String, // The actual text chunk

    @ColumnInfo(name = "embedding")
    val embedding: String, // 384-dim vector as CSV (e.g., "0.1,0.2,0.3,...")

    @ColumnInfo(name = "source")
    val source: String, // Source document (e.g., "WHO_Malaria_Guidelines.pdf")

    @ColumnInfo(name = "metadata")
    val metadata: String, // JSON with additional metadata

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
