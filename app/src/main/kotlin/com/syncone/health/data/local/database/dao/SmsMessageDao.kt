package com.syncone.health.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.syncone.health.data.local.database.entity.SmsMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: SmsMessageEntity): Long

    @Update
    suspend fun update(message: SmsMessageEntity)

    @Query("SELECT * FROM sms_messages WHERE id = :messageId")
    suspend fun getById(messageId: Long): SmsMessageEntity?

    @Query("SELECT * FROM sms_messages WHERE thread_id = :threadId ORDER BY timestamp ASC")
    fun observeByThreadId(threadId: Long): Flow<List<SmsMessageEntity>>

    @Query("SELECT * FROM sms_messages WHERE thread_id = :threadId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(threadId: Long): SmsMessageEntity?

    @Query("SELECT * FROM sms_messages WHERE thread_id = :threadId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLastNMessages(threadId: Long, limit: Int): List<SmsMessageEntity>

    @Query("SELECT * FROM sms_messages WHERE thread_id = :threadId AND direction = 'OUTGOING' AND status = 'FAILED'")
    suspend fun getFailedMessages(threadId: Long): List<SmsMessageEntity>

    @Query("UPDATE sms_messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: Long, status: String)

    @Query("DELETE FROM sms_messages WHERE thread_id = :threadId")
    suspend fun deleteByThreadId(threadId: Long)
}
