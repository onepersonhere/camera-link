package com.example.cameralink

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RecordingScheduler(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val prefsRepo = RecordingPreferencesRepository(context)
    private var cachedPrefs: RecordingPreferences = RecordingPreferences()

    init {
        scope.launch {
            prefsRepo.preferencesFlow.collectLatest { prefs ->
                cachedPrefs = prefs
                scheduleNextWindow()
            }
        }
    }

    fun shouldRecordNow(nowMinutes: Int = currentMinutesOfDay()): Boolean {
        return cachedPrefs.localRecordingEnabled && cachedPrefs.isWithinActiveWindow(nowMinutes)
    }

    private fun scheduleNextWindow(nowMillis: Long = System.currentTimeMillis()) {
        val alarmManager = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(ACTION_START))
        alarmManager.cancel(pendingIntent(ACTION_STOP))

        if (!cachedPrefs.localRecordingEnabled || !cachedPrefs.scheduleEnabled || cachedPrefs.startMinutes == cachedPrefs.endMinutes) {
            if (cachedPrefs.localRecordingEnabled) {
                triggerStart()
            } else {
                triggerStop()
            }
            return
        }

        val nowMinutes = currentMinutesOfDay(nowMillis)
        val startDelay = minutesUntil(cachedPrefs.startMinutes, nowMinutes)
        val stopDelay = minutesUntil(cachedPrefs.endMinutes, nowMinutes)

        val startTime = nowMillis + startDelay * 60_000L
        val stopTime = nowMillis + stopDelay * 60_000L

        setExact(alarmManager, startTime, ACTION_START)
        setExact(alarmManager, stopTime, ACTION_STOP)

        if (cachedPrefs.isWithinActiveWindow(nowMinutes)) {
            triggerStart()
        } else {
            triggerStop()
        }
    }

    private fun minutesUntil(targetMinutes: Int, nowMinutes: Int): Int {
        return if (targetMinutes >= nowMinutes) {
            targetMinutes - nowMinutes
        } else {
            MINUTES_PER_DAY - (nowMinutes - targetMinutes)
        }
    }

    private fun setExact(alarmManager: AlarmManager, triggerAtMillis: Long, action: String) {
        val pi = pendingIntent(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun pendingIntent(action: String): PendingIntent {
        val intent = Intent(context, RecordingWindowReceiver::class.java).apply {
            this.action = action
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, flags)
    }

    private fun triggerStart() {
        Log.d(TAG, "triggerStart")
        RecordingController.startRecording(context)
    }

    private fun triggerStop() {
        Log.d(TAG, "triggerStop")
        RecordingController.stopRecording(context)
    }

    companion object {
        private const val TAG = "RecordingScheduler"
        const val ACTION_START = "com.example.cameralink.recording.START"
        const val ACTION_STOP = "com.example.cameralink.recording.STOP"
    }
}

class RecordingWindowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            RecordingScheduler.ACTION_START -> RecordingController.startRecording(context)
            RecordingScheduler.ACTION_STOP -> RecordingController.stopRecording(context)
        }
    }
}
