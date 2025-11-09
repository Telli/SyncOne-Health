package com.syncone.health.di

import android.content.Context
import com.google.gson.Gson
import com.syncone.health.data.local.database.SyncOneDatabase
import com.syncone.health.data.local.ml.EmbeddingModel
import com.syncone.health.data.local.ml.MedGemmaInference
import com.syncone.health.data.local.ml.ModelManager
import com.syncone.health.data.local.ml.VectorStoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for ML components.
 * Provides TensorFlow Lite models, vector store, and related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object MLModule {

    @Provides
    @Singleton
    fun provideModelManager(
        @ApplicationContext context: Context
    ): ModelManager {
        return ModelManager(context)
    }

    @Provides
    fun provideMedGemmaInference(manager: ModelManager): MedGemmaInference {
        return manager.getMedGemma()
    }

    @Provides
    fun provideEmbeddingModel(manager: ModelManager): EmbeddingModel {
        return manager.getEmbeddings()
    }

    @Provides
    @Singleton
    fun provideVectorStoreManager(
        database: SyncOneDatabase,
        embeddingModel: EmbeddingModel,
        gson: Gson
    ): VectorStoreManager {
        return VectorStoreManager(database, embeddingModel, gson)
    }
}
