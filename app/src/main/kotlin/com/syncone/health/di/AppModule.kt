package com.syncone.health.di

import android.content.Context
import androidx.biometric.BiometricManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.syncone.health.util.PhoneNumberFormatter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for application-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setPrettyPrinting()
            .create()
    }

    @Provides
    @Singleton
    fun providePhoneNumberFormatter(
        @ApplicationContext context: Context
    ): PhoneNumberFormatter {
        return PhoneNumberFormatter(context)
    }

    @Provides
    @Singleton
    fun provideBiometricManager(
        @ApplicationContext context: Context
    ): BiometricManager {
        return BiometricManager.from(context)
    }
}
