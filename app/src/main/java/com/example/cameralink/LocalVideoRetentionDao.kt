package com.example.cameralink

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

private const val MANIFEST_FILE = "local_segments.json"
private const val MAX_RETENTION_MILLIS = 24 * 60 * 60 * 1000L // 24h

class LocalVideoRetentionDao(private val context: Context) {
    private val manifestFile: File = File(context.filesDir, MANIFEST_FILE)

    suspend fun persistSegment(entry: LocalSegmentEntry) = withContext(Dispatchers.IO) {
        val entries = readEntries().toMutableList()
        entries.add(entry)
        writeEntries(entries)
    }

    suspend fun pruneOldSegments(nowMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val entries = readEntries().sortedBy { it.createdAtMillis }.toMutableList()
        var changed = false
        while (entries.isNotEmpty() && nowMillis - entries.first().createdAtMillis > MAX_RETENTION_MILLIS) {
            val entry = entries.removeAt(0)
            entry.file().delete()
            changed = true
        }
        if (changed) {
            writeEntries(entries)
        }
    }

    private fun readEntries(): List<LocalSegmentEntry> {
        if (!manifestFile.exists()) return emptyList()
        val json = JSONArray(manifestFile.readText())
        return buildList {
            for (i in 0 until json.length()) {
                val obj = json.optJSONObject(i)
                LocalSegmentEntry.fromJson(obj)?.let { add(it) }
            }
        }
    }

    private fun writeEntries(entries: List<LocalSegmentEntry>) {
        val array = JSONArray()
        entries.forEach { array.put(it.toJson()) }
        manifestFile.writeText(array.toString())
    }
}
