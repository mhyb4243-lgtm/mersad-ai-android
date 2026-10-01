package com.mersadai.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mersadai.app.data.local.ContentDao.Companion.EXTERNAL_SYNC_ID
import com.mersadai.app.data.local.MersadDatabase
import com.mersadai.app.data.local.SettingsRepository
import com.mersadai.app.data.local.SyncStateEntity
import com.mersadai.app.data.notifications.LocalNotificationDispatcher
import com.mersadai.app.data.remote.OkHttpSourceTransport
import com.mersadai.app.data.sync.PublicSourceSyncCoordinator
import com.mersadai.app.data.sync.RoomSyncStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val database = MersadDatabase.create(applicationContext)
        val dao = database.contentDao()
        val startedAt = System.currentTimeMillis()
        return try {
            val result = PublicSourceSyncCoordinator(RoomSyncStore(dao), OkHttpSourceTransport())
                .synchronize(force = inputData.getBoolean(FORCE_SYNC, false))
            try {
                LocalNotificationDispatcher(applicationContext, dao)
                    .publish(SettingsRepository(applicationContext).settings.first())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Notification failures must not turn a successful source sync into a retry.
            }
            if (result.hasTransientFailure && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        } catch (_: Exception) {
            dao.upsertSyncState(
                SyncStateEntity(EXTERNAL_SYNC_ID, "FAILURE", startedAt, System.currentTimeMillis(), "تعذر إكمال المزامنة."),
            )
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } finally {
            database.close()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "manual_source_sync"
        const val FORCE_SYNC = "force_sync"
        private const val MAX_RETRIES = 1
    }
}

class SyncScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueueInitialSync() = enqueue(force = false)

    fun enqueueManualSync() = enqueue(force = true)

    fun setPeriodicSyncEnabled(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork("periodic_source_sync")
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork("periodic_source_sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun enqueue(force: Boolean) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(workDataOf(SyncWorker.FORCE_SYNC to force))
            .build()
        workManager.enqueueUniqueWork(SyncWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
