package com.example.cameralink
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
/**
 * WorkManager Worker that periodically pings all Tailscale connections
 */
class TailscalePingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "TailscalePingWorker"
        const val WORK_NAME = "tailscale_ping_work"
    }
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting Tailscale ping job")
            // Ping all Tailscale connections
            val results = TailscalePinger.pingAllTailscaleConnections()
            if (results.isEmpty()) {
                Log.i(TAG, "No Tailscale connections to ping")
            } else {
                val successCount = results.values.count { it }
                Log.i(TAG, "Pinged ${results.size} Tailscale connection(s): $successCount successful")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in Tailscale ping worker", e)
            Result.retry()
        }
    }
}
