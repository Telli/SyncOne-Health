package com.syncone.health.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.syncone.health.domain.repository.ThreadRepository
import com.syncone.health.domain.usecase.ManageThreadContextUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Background worker that expires threads older than 72 hours.
 * Runs periodically (every hour).
 */
@HiltWorker
class ThreadExpirationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val threadRepository: ThreadRepository,
    private val manageContextUseCase: ManageThreadContextUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Timber.d("Running thread expiration cleanup")

            // Archive expired threads
            val archivedCount = threadRepository.archiveExpiredThreads()
            Timber.d("Archived $archivedCount expired threads")

            // Delete expired conversation contexts
            manageContextUseCase.deleteExpiredContexts()
            Timber.d("Deleted expired conversation contexts")

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Error during thread expiration cleanup")
            Result.retry()
        }
    }
}
