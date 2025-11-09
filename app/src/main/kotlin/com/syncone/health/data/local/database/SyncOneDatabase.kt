package com.syncone.health.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.syncone.health.data.local.database.dao.AuditLogDao
import com.syncone.health.data.local.database.dao.ConversationContextDao
import com.syncone.health.data.local.database.dao.SmsMessageDao
import com.syncone.health.data.local.database.dao.SmsThreadDao
import com.syncone.health.data.local.database.entity.AuditLogEntity
import com.syncone.health.data.local.database.entity.ConversationContextEntity
import com.syncone.health.data.local.database.entity.SmsMessageEntity
import com.syncone.health.data.local.database.entity.SmsThreadEntity
import com.syncone.health.data.local.security.EncryptionKeyManager
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * Encrypted Room database using SQLCipher AES-256.
 * Stores all SMS threads, messages, conversation contexts, and audit logs.
 */
@Database(
    entities = [
        SmsThreadEntity::class,
        SmsMessageEntity::class,
        ConversationContextEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class SyncOneDatabase : RoomDatabase() {

    abstract fun smsThreadDao(): SmsThreadDao
    abstract fun smsMessageDao(): SmsMessageDao
    abstract fun conversationContextDao(): ConversationContextDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        private const val DATABASE_NAME = "syncone_health.db"

        @Volatile
        private var INSTANCE: SyncOneDatabase? = null

        fun getInstance(context: Context): SyncOneDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): SyncOneDatabase {
            val keyManager = EncryptionKeyManager(context)
            val passphrase = keyManager.getDatabaseKey()

            // Initialize SQLCipher
            SQLiteDatabase.loadLibs(context)
            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                SyncOneDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration() // For development; use migrations in production
                .build()
        }
    }
}
