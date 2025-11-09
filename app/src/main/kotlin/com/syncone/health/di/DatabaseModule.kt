package com.syncone.health.di

import android.content.Context
import com.syncone.health.data.local.database.SyncOneDatabase
import com.syncone.health.data.local.database.dao.AuditLogDao
import com.syncone.health.data.local.database.dao.ConversationContextDao
import com.syncone.health.data.local.database.dao.SmsMessageDao
import com.syncone.health.data.local.database.dao.SmsThreadDao
import com.syncone.health.data.local.preferences.SecurePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for database dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): SyncOneDatabase {
        return SyncOneDatabase.getInstance(context)
    }

    @Provides
    fun provideSmsThreadDao(database: SyncOneDatabase): SmsThreadDao {
        return database.smsThreadDao()
    }

    @Provides
    fun provideSmsMessageDao(database: SyncOneDatabase): SmsMessageDao {
        return database.smsMessageDao()
    }

    @Provides
    fun provideConversationContextDao(database: SyncOneDatabase): ConversationContextDao {
        return database.conversationContextDao()
    }

    @Provides
    fun provideAuditLogDao(database: SyncOneDatabase): AuditLogDao {
        return database.auditLogDao()
    }

    @Provides
    @Singleton
    fun provideSecurePreferences(
        @ApplicationContext context: Context
    ): SecurePreferences {
        return SecurePreferences(context)
    }
}
