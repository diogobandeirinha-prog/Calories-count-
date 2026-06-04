package com.caloriescount.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.caloriescount.app.CaloriesApp

/**
 * Drains all PENDING food entries to the remote server. Instantiated by the default
 * WorkerFactory, so dependencies are pulled from the application container rather than
 * injected through the constructor.
 *
 * Returns:
 *  - success when everything synced (or no server is configured — nothing to do),
 *  - retry on a transient failure, so WorkManager re-runs it with backoff once the
 *    connection is stable again.
 */
class FoodSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CaloriesApp).container
        return when (container.foodRepository.syncPending()) {
            SyncOutcome.Success, SyncOutcome.NoServer -> Result.success()
            SyncOutcome.Retry -> Result.retry()
        }
    }
}
