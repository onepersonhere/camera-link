package com.example.cameralink

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.Locale

private const val PREFS_NAME = "recording_prefs"
private const val KEY_LOCAL_ENABLED = "local_recording_enabled"
private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
private const val KEY_START_MINUTES = "schedule_start_minutes"
private const val KEY_END_MINUTES = "schedule_end_minutes"

val Context.recordingPrefs: SharedPreferences
    get() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

data class RecordingPreferences(
    val localRecordingEnabled: Boolean = false,
    val scheduleEnabled: Boolean = false,
    val startMinutes: Int = DEFAULT_START_MINUTES,
    val endMinutes: Int = DEFAULT_END_MINUTES
) {
    fun isWithinActiveWindow(nowMinutesOfDay: Int): Boolean {
        if (!scheduleEnabled || startMinutes == endMinutes) return true
        return if (startMinutes < endMinutes) {
            nowMinutesOfDay in startMinutes until endMinutes
        } else {
            nowMinutesOfDay >= startMinutes || nowMinutesOfDay < endMinutes
        }
    }
}

class RecordingPreferencesRepository(private val context: Context) {
    private val _state = MutableStateFlow(loadFromPrefs())
    val preferencesFlow: Flow<RecordingPreferences> = _state.asStateFlow()

    private fun loadFromPrefs(): RecordingPreferences {
        val prefs = context.recordingPrefs
        return RecordingPreferences(
            localRecordingEnabled = prefs.getBoolean(KEY_LOCAL_ENABLED, false),
            scheduleEnabled = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false),
            startMinutes = prefs.getInt(KEY_START_MINUTES, DEFAULT_START_MINUTES).normalizeToScheduleRange(),
            endMinutes = prefs.getInt(KEY_END_MINUTES, DEFAULT_END_MINUTES).normalizeToScheduleRange()
        )
    }

    private fun saveAndEmit(builder: (RecordingPreferences) -> RecordingPreferences) {
        val current = _state.value
        val next = builder(current)
        with(context.recordingPrefs.edit()) {
            putBoolean(KEY_LOCAL_ENABLED, next.localRecordingEnabled)
            putBoolean(KEY_SCHEDULE_ENABLED, next.scheduleEnabled)
            putInt(KEY_START_MINUTES, next.startMinutes)
            putInt(KEY_END_MINUTES, next.endMinutes)
            apply()
        }
        _state.value = next
    }

    suspend fun updateLocalRecordingEnabled(enabled: Boolean) {
        saveAndEmit { it.copy(localRecordingEnabled = enabled) }
    }

    suspend fun updateScheduleEnabled(enabled: Boolean) {
        saveAndEmit { it.copy(scheduleEnabled = enabled) }
    }

    suspend fun updateStartMinutes(minutes: Int) {
        val normalized = minutes.normalizeToScheduleRange()
        saveAndEmit { it.copy(startMinutes = normalized) }
    }

    suspend fun updateEndMinutes(minutes: Int) {
        val normalized = minutes.normalizeToScheduleRange()
        saveAndEmit { it.copy(endMinutes = normalized) }
    }
}

fun Int.normalizeToScheduleRange(): Int {
    var value = this % MINUTES_PER_DAY
    if (value < 0) value += MINUTES_PER_DAY
    return value
}

fun minutesLabel(minutes: Int): String {
    val normalized = minutes.normalizeToScheduleRange()
    val hours = normalized / 60
    val mins = normalized % 60
    return String.format(Locale.getDefault(), "%02d:%02d", hours, mins)
}

fun currentMinutesOfDay(nowMillis: Long = System.currentTimeMillis()): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}

const val MINUTES_PER_DAY = 24 * 60
const val DEFAULT_START_MINUTES = 0
const val DEFAULT_END_MINUTES = MINUTES_PER_DAY
