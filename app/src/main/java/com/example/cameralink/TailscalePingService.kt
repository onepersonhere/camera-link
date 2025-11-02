package com.example.cameralink

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Foreground service that pings Tailscale connections every 15 seconds
 */
class TailscalePingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var pingRunnable: Runnable? = null

    companion object {
        private const val TAG = "TailscalePingService"
        private const val PING_INTERVAL_MS = 15000L // 15 seconds
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "tailscale_ping_channel"

        fun start(context: Context) {
            val intent = Intent(context, TailscalePingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "Starting TailscalePingService")
        }

        fun stop(context: Context) {
            val intent = Intent(context, TailscalePingService::class.java)
            context.stopService(intent)
            Log.i(TAG, "Stopping TailscalePingService")
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "TailscalePingService created")

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification("Starting Tailscale ping service...", 0, 0))

        startPinging()
    }

    private fun startPinging() {
        pingRunnable = object : Runnable {
            override fun run() {
                serviceScope.launch {
                    try {
                        Log.d(TAG, "🔄 Starting ping cycle...")
                        val results = TailscalePinger.pingAllTailscaleConnections()
                        if (results.isNotEmpty()) {
                            val successCount = results.values.count { it }
                            val totalCount = results.size
                            Log.i(TAG, "✅ Ping cycle complete: $successCount/$totalCount successful")

                            // Update notification with results
                            val notification = createNotification(
                                "Tailscale Ping Active",
                                successCount,
                                totalCount
                            )
                            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            notificationManager.notify(NOTIFICATION_ID, notification)
                        } else {
                            Log.w(TAG, "⚠️ No Tailscale targets configured")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error during ping cycle", e)
                    }
                }
                // Schedule next ping
                handler.postDelayed(this, PING_INTERVAL_MS)
            }
        }
        // Start first ping immediately
        handler.post(pingRunnable!!)
        Log.i(TAG, "🚀 Started pinging every ${PING_INTERVAL_MS / 1000} seconds")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tailscale Ping Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Tailscale connections alive by pinging peers"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, successCount: Int, totalCount: Int): Notification {
        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (totalCount > 0) {
            "Pinging $totalCount device(s): $successCount online"
        } else {
            "Monitoring Tailscale connections"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        pingRunnable?.let { handler.removeCallbacks(it) }
        Log.i(TAG, "🛑 TailscalePingService destroyed")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}

