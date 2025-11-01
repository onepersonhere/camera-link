package com.example.cameralink

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.net.NetworkInterface
import java.util.concurrent.Executors

class CameraStreamingService : LifecycleService() {

    private var streamingServer: StreamingServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "camera_streaming_channel"
        const val ACTION_START_STREAMING = "com.example.cameralink.START_STREAMING"
        const val ACTION_STOP_STREAMING = "com.example.cameralink.STOP_STREAMING"
        const val EXTRA_PORT = "port"

        fun startService(context: Context, port: Int = 8080) {
            val intent = Intent(context, CameraStreamingService::class.java).apply {
                action = ACTION_START_STREAMING
                putExtra(EXTRA_PORT, port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CameraStreamingService::class.java).apply {
                action = ACTION_STOP_STREAMING
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START_STREAMING -> {
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                startStreaming(port)
            }
            ACTION_STOP_STREAMING -> {
                stopStreaming()
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startStreaming(port: Int) {
        // Start foreground service with notification
        val notification = createNotification("Starting camera stream...", getIpAddress(), port)
        startForeground(NOTIFICATION_ID, notification)

        // Start streaming server
        streamingServer = StreamingServer(port).apply {
            start()
        }

        // Start camera
        startCamera()

        // Update notification with streaming info
        val updatedNotification = createNotification("Camera streaming active", getIpAddress(), port)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            streamingServer?.updateFrame(imageProxy)
                            imageProxy.close()
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopStreaming() {
        streamingServer?.stop()
        streamingServer = null
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CameraLink::StreamingWakeLock"
        ).apply {
            acquire(10 * 60 * 60 * 1000L /*10 hours*/)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Camera Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for camera streaming service"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, ipAddress: String, port: Int): Notification {
        val stopIntent = Intent(this, CameraStreamingService::class.java).apply {
            action = ACTION_STOP_STREAMING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (ipAddress.isNotEmpty() && ipAddress != "Unable to get IP") {
            "Stream URL: http://$ipAddress:$port"
        } else {
            "Waiting for network connection..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\n\nTap to open app. Camera will continue streaming even with screen off.")
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun getIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val hostAddress = addr.hostAddress
                        if (hostAddress != null && hostAddress.indexOf(':') < 0) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}

