package com.syncone.health.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.syncone.health.data.local.database.entity.VectorChunkEntity

@Dao
interface VectorChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: VectorChunkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<VectorChunkEntity>)

    @Query("SELECT * FROM vector_chunks")
    suspend fun getAllChunks(): List<VectorChunkEntity>

    @Query("SELECT * FROM vector_chunks WHERE source = :source")
    suspend fun getChunksBySource(source: String): List<VectorChunkEntity>

    @Query("SELECT COUNT(*) FROM vector_chunks")
    suspend fun getChunkCount(): Int

    @Query("DELETE FROM vector_chunks WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("DELETE FROM vector_chunks")
    suspend fun deleteAll()

    @Query("SELECT * FROM vector_chunks WHERE id = :id")
    suspend fun getById(id: Long): VectorChunkEntity?
}
