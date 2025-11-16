package com.example.cameralink

import android.content.Context
import android.content.Intent
import android.util.Log

object RecordingController {
    private const val TAG = "RecordingController"

    fun startRecording(context: Context) {
        Log.d(TAG, "startRecording")
        val intent = Intent(context, CameraStreamingService::class.java).apply {
            action = CameraStreamingService.ACTION_START_RECORDING
        }
        context.startService(intent)
    }

    fun stopRecording(context: Context) {
        Log.d(TAG, "stopRecording")
        val intent = Intent(context, CameraStreamingService::class.java).apply {
            action = CameraStreamingService.ACTION_STOP_RECORDING
        }
        context.startService(intent)
    }
}

