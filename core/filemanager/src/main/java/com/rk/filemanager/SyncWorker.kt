package com.rk.filemanager

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Background sync worker. Poll-based (no FileObserver) because FUSE-mounted
 * /sdcard does not reliably surface inotify events. Runs each enabled sync pair
 * through [SyncEngine], persisting the post-sync state so the next run can detect
 * incremental changes via last-write-wins.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val pairs = SyncConfigStore.getPairs(applicationContext).filter { it.enabled }
        for (pair in pairs) {
            val ubuntu = java.io.File(pair.ubuntuPath)
            val phone = java.io.File(pair.phonePath)
            if (!ubuntu.exists() || !phone.exists()) continue
            val last = SyncConfigStore.getState(applicationContext, pair)
            val actions = SyncEngine.plan(ubuntu, phone, last)
            val newState = SyncEngine.apply(actions, ubuntu, phone)
            SyncConfigStore.setState(applicationContext, pair, newState)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE = "shellix-fm-sync"

        /** Enqueue a periodic (>=15 min, battery-safe) sync. Safe to call repeatedly. */
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE, androidx.work.ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }

        /** Run a single sync pass immediately (e.g. "Sync now" button). */
        fun runNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE-now", androidx.work.ExistingWorkPolicy.REPLACE, req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
