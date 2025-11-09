package com.syncone.health.di

import com.google.gson.Gson
import com.syncone.health.data.local.database.dao.AuditLogDao
import com.syncone.health.data.local.database.dao.ConversationContextDao
import com.syncone.health.data.local.database.dao.SmsMessageDao
import com.syncone.health.data.local.database.dao.SmsThreadDao
import com.syncone.health.data.repository.AuditRepositoryImpl
import com.syncone.health.data.repository.SmsRepositoryImpl
import com.syncone.health.data.repository.ThreadRepositoryImpl
import com.syncone.health.domain.repository.AuditRepository
import com.syncone.health.domain.repository.SmsRepository
import com.syncone.health.domain.repository.ThreadRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for repository dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideThreadRepository(
        threadDao: SmsThreadDao,
        messageDao: SmsMessageDao
    ): ThreadRepository {
        return ThreadRepositoryImpl(threadDao, messageDao)
    }

    @Provides
    @Singleton
    fun provideSmsRepository(
        messageDao: SmsMessageDao,
        contextDao: ConversationContextDao,
        gson: Gson
    ): SmsRepository {
        return SmsRepositoryImpl(messageDao, contextDao, gson)
    }

    @Provides
    @Singleton
    fun provideAuditRepository(
        auditLogDao: AuditLogDao,
        gson: Gson
    ): AuditRepository {
        return AuditRepositoryImpl(auditLogDao, gson)
    }
}
