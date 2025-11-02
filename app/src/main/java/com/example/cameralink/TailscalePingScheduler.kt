package com.example.cameralink

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Manager class to schedule and manage Tailscale ping jobs
 */
object TailscalePingScheduler {
    private const val TAG = "TailscalePingScheduler"

    /**
     * Schedule periodic Tailscale pinging every 15 seconds
     * Note: Due to WorkManager limitations, we use 15-minute intervals with repeating work.
     * For true 15-second intervals, use startForegroundPinging() instead.
     */
    fun schedulePeriodicPing(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val pingWorkRequest = PeriodicWorkRequestBuilder<TailscalePingWorker>(
            15, TimeUnit.MINUTES, // Minimum allowed by WorkManager
            5, TimeUnit.MINUTES // Flex period
        )
            .setConstraints(constraints)
            .setInitialDelay(0, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TailscalePingWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing work if already scheduled
            pingWorkRequest
        )

        Log.i(TAG, "Scheduled Tailscale ping job to run every 15 minutes (WorkManager limitation)")
    }

    /**
     * Cancel the periodic Tailscale ping job
     */
    fun cancelPeriodicPing(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(TailscalePingWorker.WORK_NAME)
        Log.i(TAG, "Cancelled Tailscale ping job")
    }

    /**
     * Check if the periodic ping job is currently scheduled
     */
    fun isScheduled(context: Context): Boolean {
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(TailscalePingWorker.WORK_NAME)
            .get()
        return workInfos.any { !it.state.isFinished }
    }
}

