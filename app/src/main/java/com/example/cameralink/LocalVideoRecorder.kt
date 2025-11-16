package com.example.cameralink

import android.content.Context
import android.os.Environment
import com.example.cameralink.LocalVideoRetentionDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val MAX_SEGMENT_DURATION_MS = 10 * 60 * 1000L // 10 minutes
private const val MAX_SEGMENT_SIZE_BYTES = 150L * 1024 * 1024 // ~150MB

class LocalVideoRecorder(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val isRecording = AtomicBoolean(false)
    private var currentWriter: RollingFileWriter? = null
    private var currentSegmentStartMillis: Long = 0L
    private val retentionDao = LocalVideoRetentionDao(context)

    fun start() {
        if (!isRecording.getAndSet(true)) {
            scope.launch {
                mutex.withLock {
                    openNewSegmentLocked()
                }
            }
        }
    }

    fun stop() {
        if (isRecording.getAndSet(false)) {
            scope.launch {
                mutex.withLock {
                    currentWriter?.close()
                    currentWriter = null
                }
            }
        }
    }

    fun isRecording(): Boolean = isRecording.get()

    fun appendFrame(frameBytes: ByteArray) {
        if (!isRecording() || frameBytes.isEmpty()) return
        scope.launch {
            mutex.withLock {
                if (currentWriter == null) {
                    openNewSegmentLocked()
                }
                currentWriter?.writeFrame(frameBytes)
                rotateIfNeededLocked()
            }
        }
    }

    private suspend fun openNewSegmentLocked(nowMillis: Long = System.currentTimeMillis()) {
        currentWriter?.close()
        val writer = RollingFileWriter(context, nowMillis)
        currentWriter = writer
        currentSegmentStartMillis = nowMillis
        retentionDao.persistSegment(writer.entry)
        retentionDao.pruneOldSegments(nowMillis)
    }

    private suspend fun rotateIfNeededLocked(nowMillis: Long = System.currentTimeMillis()) {
        val writer = currentWriter ?: return
        if (nowMillis - currentSegmentStartMillis >= MAX_SEGMENT_DURATION_MS ||
            writer.bytesWritten >= MAX_SEGMENT_SIZE_BYTES
        ) {
            openNewSegmentLocked(nowMillis)
        }
    }

    fun release() {
        scope.cancel()
    }
}

class RollingFileWriter(private val context: Context, createdAtMillis: Long) {
    private val file: File
    private val output: FileOutputStream
    val entry: LocalSegmentEntry
    var bytesWritten: Long = 0L
        private set

    init {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "CameraLink")
        if (!dir.exists()) dir.mkdirs()
        val fileName = "segment_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(createdAtMillis))}.mjpg"
        file = File(dir, fileName)
        output = FileOutputStream(file)
        entry = LocalSegmentEntry(file.absolutePath, createdAtMillis)
    }

    fun writeFrame(frame: ByteArray) {
        if (frame.isEmpty()) return
        writeBytes(BOUNDARY)
        writeBytes(CONTENT_TYPE)
        writeBytes("Content-Length: ${frame.size}\r\n\r\n".toByteArray())
        writeBytes(frame)
        writeBytes(NEWLINE)
    }

    private fun writeBytes(bytes: ByteArray) {
        output.write(bytes)
        bytesWritten += bytes.size
    }

    fun close() {
        output.flush()
        output.close()
    }

    companion object {
        private val BOUNDARY = "--CameraLinkMJPEG\r\n".toByteArray()
        private val CONTENT_TYPE = "Content-Type: image/jpeg\r\n".toByteArray()
        private val NEWLINE = "\r\n".toByteArray()
    }
}
