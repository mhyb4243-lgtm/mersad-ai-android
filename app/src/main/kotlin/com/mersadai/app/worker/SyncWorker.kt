package com.mersadai.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mersadai.app.data.local.ContentDao.Companion.EXTERNAL_SYNC_ID
import com.mersadai.app.data.local.MersadDatabase
import com.mersadai.app.data.local.SyncStateEntity
import com.mersadai.app.domain.model.SyncState
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val database = MersadDatabase.create(applicationContext)
        val dao = database.contentDao()
        val startedAt = System.currentTimeMillis()
        return try {
            dao.upsertSyncState(
                SyncStateEntity(EXTERNAL_SYNC_ID, SyncState.SYNCING.name, startedAt, null, null),
            )
            dao.upsertSyncState(
                SyncStateEntity(
                    EXTERNAL_SYNC_ID,
                    SyncState.NOT_CONFIGURED.name,
                    startedAt,
                    System.currentTimeMillis(),
                    null,
                ),
            )
            Result.success()
        } catch (_: Exception) {
            dao.upsertSyncState(
                SyncStateEntity(EXTERNAL_SYNC_ID, SyncState.FAILURE.name, startedAt, System.currentTimeMillis(), null),
            )
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } finally {
            database.close()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "manual_source_sync"
        private const val MAX_RETRIES = 2
    }
}

class SyncScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueueManualSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(SyncWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
