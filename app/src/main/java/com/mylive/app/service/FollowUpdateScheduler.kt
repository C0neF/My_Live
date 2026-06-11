package com.mylive.app.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object FollowUpdateScheduler {

    const val WORK_NAME = "FollowUpdatePeriodicWork"

    fun schedule(context: Context, enabled: Boolean, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val duration = coerceFollowUpdateIntervalMinutes(intervalMinutes).toLong()
        val request = PeriodicWorkRequestBuilder<FollowUpdateWorker>(
            duration, TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
