package com.syncone.health.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.syncone.health.data.local.database.entity.ConversationContextEntity

@Dao
interface ConversationContextDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(context: ConversationContextEntity)

    @Update
    suspend fun update(context: ConversationContextEntity)

    @Query("SELECT * FROM conversation_contexts WHERE threadId = :threadId")
    suspend fun getByThreadId(threadId: Long): ConversationContextEntity?

    @Query("DELETE FROM conversation_contexts WHERE threadId = :threadId")
    suspend fun delete(threadId: Long)

    @Query("DELETE FROM conversation_contexts WHERE threadId IN (SELECT id FROM sms_threads WHERE expires_at < :currentTime)")
    suspend fun deleteExpiredContexts(currentTime: Long)
}
