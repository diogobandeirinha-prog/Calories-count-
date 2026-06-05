package com.caloriescount.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Outcome of a sync pass, mapped to a WorkManager [androidx.work.ListenableWorker.Result]. */
enum class SyncOutcome { Success, NoServer, Retry }

/**
 * Enqueues the offline-first sync job. The job is constrained to require network
 * connectivity, so when the user logs food offline the work is parked by WorkManager
 * and runs automatically the moment a connection is available again — surviving app
 * restarts and process death. Failures retry with exponential backoff.
 */
class SyncManager(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun requestSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<FoodSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()

        // KEEP: if a job is already waiting for connectivity, don't pile on duplicates —
        // a single run drains every PENDING row.
        workManager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val TAG = "food-sync"
        private const val UNIQUE_WORK = "food-sync-work"
    }
}
