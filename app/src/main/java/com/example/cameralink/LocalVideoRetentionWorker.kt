package com.example.cameralink

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class LocalVideoRetentionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val dao = LocalVideoRetentionDao(appContext)

    override suspend fun doWork(): Result {
        return try {
            dao.pruneOldSegments()
            schedule(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "local_video_retention"
        private val INTERVAL = TimeUnit.HOURS.toMillis(1)

        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val request = OneTimeWorkRequestBuilder<LocalVideoRetentionWorker>()
                .setInitialDelay(INTERVAL, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

