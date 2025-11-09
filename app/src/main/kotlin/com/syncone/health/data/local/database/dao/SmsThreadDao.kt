package com.syncone.health.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.syncone.health.data.local.database.entity.SmsThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsThreadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(thread: SmsThreadEntity): Long

    @Update
    suspend fun update(thread: SmsThreadEntity)

    @Query("SELECT * FROM sms_threads WHERE id = :threadId")
    suspend fun getById(threadId: Long): SmsThreadEntity?

    @Query("SELECT * FROM sms_threads WHERE id = :threadId")
    fun observeById(threadId: Long): Flow<SmsThreadEntity?>

    @Query("SELECT * FROM sms_threads WHERE phone_number = :phoneNumber LIMIT 1")
    suspend fun getByPhoneNumber(phoneNumber: String): SmsThreadEntity?

    @Query("SELECT * FROM sms_threads ORDER BY last_message_at DESC")
    fun observeAll(): Flow<List<SmsThreadEntity>>

    @Query("SELECT * FROM sms_threads WHERE status = :status ORDER BY last_message_at DESC")
    fun observeByStatus(status: String): Flow<List<SmsThreadEntity>>

    @Query("SELECT * FROM sms_threads WHERE urgency_level = :urgencyLevel ORDER BY last_message_at DESC")
    fun observeByUrgency(urgencyLevel: String): Flow<List<SmsThreadEntity>>

    @Query("SELECT * FROM sms_threads WHERE expires_at < :currentTime")
    suspend fun getExpiredThreads(currentTime: Long): List<SmsThreadEntity>

    @Query("UPDATE sms_threads SET status = 'ARCHIVED' WHERE id IN (:threadIds)")
    suspend fun archiveThreads(threadIds: List<Long>)

    @Query("DELETE FROM sms_threads WHERE id = :threadId")
    suspend fun delete(threadId: Long)

    @Query("UPDATE sms_threads SET message_count = message_count + 1, last_message_at = :timestamp WHERE id = :threadId")
    suspend fun incrementMessageCount(threadId: Long, timestamp: Long)
}
