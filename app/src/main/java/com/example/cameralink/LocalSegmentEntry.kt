package com.example.cameralink

import org.json.JSONObject
import java.io.File

data class LocalSegmentEntry(
    val filePath: String,
    val createdAtMillis: Long
) {
    fun file(): File = File(filePath)

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_PATH, filePath)
        put(KEY_CREATED_AT, createdAtMillis)
    }

    companion object {
        private const val KEY_PATH = "path"
        private const val KEY_CREATED_AT = "createdAt"

        fun fromJson(json: JSONObject): LocalSegmentEntry? {
            val path = json.optString(KEY_PATH)
            val createdAt = json.optLong(KEY_CREATED_AT, -1)
            if (path.isNullOrBlank() || createdAt <= 0) return null
            return LocalSegmentEntry(path, createdAt)
        }
    }
}

