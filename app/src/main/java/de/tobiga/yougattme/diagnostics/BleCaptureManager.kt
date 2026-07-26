package de.tobiga.yougattme.diagnostics

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.provider.MediaStore
import androidx.preference.PreferenceManager
import de.tobiga.yougattme.BuildConfig
import de.tobiga.yougattme.gatt.trace.BleTrafficDirection
import de.tobiga.yougattme.gatt.trace.BleTrafficEvent
import de.tobiga.yougattme.gatt.trace.BleTrafficRole
import de.tobiga.yougattme.gatt.trace.BleTrafficTrace
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Streams opt-in raw BLE traces into MediaStore Downloads without storage
 * permissions. All file I/O is kept off Bluetooth callback threads.
 */
object BleCaptureManager {
    const val PREFERENCE_ENABLED = "diagnostics.ble_capture_enabled"
    const val PUBLIC_DIRECTORY = "Downloads/YouGattMe/BLE captures"

    private const val MAX_FILE_BYTES = 25L * 1024L * 1024L
    private const val FLUSH_EVENT_COUNT = 20
    private val fileTime = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneOffset.UTC)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ble-capture-writer").apply { isDaemon = true }
    }

    @Volatile
    var currentFileName: String? = null
        private set

    @Volatile
    var lastError: String? = null
        private set

    private var initialized = false
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private var writer: BufferedWriter? = null
    private var bytesWritten = 0L
    private var pendingEvents = 0
    private var lastFlushNanos = 0L
    private var captureStartedAt = Instant.EPOCH
    private var part = 0
    @Volatile
    private var configurationGeneration = 0L

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == PREFERENCE_ENABLED) {
                configure(sharedPreferences.getBoolean(PREFERENCE_ENABLED, false))
            }
        }

    @Synchronized
    fun initialize(value: Context) {
        if (initialized) return
        initialized = true
        context = value.applicationContext
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        configure(preferences.getBoolean(PREFERENCE_ENABLED, false))
    }

    @Synchronized
    private fun configure(enabled: Boolean) {
        configurationGeneration += 1
        val generation = configurationGeneration
        if (enabled) {
            if (BleTrafficTraceSink.installed) return
            lastError = null
            captureStartedAt = Instant.now()
            part = 0
            executor.execute {
                if (generation != configurationGeneration) return@execute
                if (openFile() && generation == configurationGeneration) {
                    BleTrafficTrace.installSink(BleTrafficTraceSink::accept)
                    BleTrafficTraceSink.installed = true
                    BleTrafficTrace.record(
                        role = BleTrafficRole.CENTRAL,
                        direction = BleTrafficDirection.EVENT,
                        event = "CAPTURE_STARTED",
                        details = mapOf(
                            "app_version" to BuildConfig.VERSION_NAME,
                            "version_code" to BuildConfig.VERSION_CODE,
                            "format" to "you-gatt-me-ble-jsonl-v1",
                            "public_directory" to PUBLIC_DIRECTORY
                        )
                    )
                } else if (generation == configurationGeneration) {
                    preferences.edit().putBoolean(PREFERENCE_ENABLED, false).apply()
                } else {
                    closeFile()
                }
            }
        } else {
            if (BleTrafficTraceSink.installed) {
                BleTrafficTrace.record(
                    role = BleTrafficRole.CENTRAL,
                    direction = BleTrafficDirection.EVENT,
                    event = "CAPTURE_STOPPED"
                )
            }
            BleTrafficTrace.installSink(null)
            BleTrafficTraceSink.installed = false
            executor.execute(::closeFile)
        }
    }

    private object BleTrafficTraceSink {
        @Volatile
        var installed = false

        fun accept(event: BleTrafficEvent) {
            executor.execute { write(event) }
        }
    }

    private fun write(event: BleTrafficEvent) {
        val line = event.toJsonLine() + "\n"
        val lineBytes = line.toByteArray(StandardCharsets.UTF_8).size.toLong()
        if (writer == null && !openFile()) {
            disableAfterWriteFailure()
            return
        }
        if (bytesWritten > 0 && bytesWritten + lineBytes > MAX_FILE_BYTES) {
            closeFile()
            part += 1
            if (!openFile()) {
                disableAfterWriteFailure()
                return
            }
        }
        runCatching {
            writer?.write(line)
            bytesWritten += lineBytes
            pendingEvents += 1
            val now = System.nanoTime()
            if (pendingEvents >= FLUSH_EVENT_COUNT || now - lastFlushNanos >= FLUSH_INTERVAL_NANOS) {
                writer?.flush()
                pendingEvents = 0
                lastFlushNanos = now
            }
        }.onFailure { failure ->
            lastError = failure.message ?: failure.javaClass.simpleName
            disableAfterWriteFailure()
        }
    }

    private fun disableAfterWriteFailure() {
        closeFile()
        BleTrafficTrace.installSink(null)
        BleTrafficTraceSink.installed = false
        preferences.edit().putBoolean(PREFERENCE_ENABLED, false).apply()
    }

    private fun openFile(): Boolean {
        if (writer != null) return true
        val suffix = if (part == 0) "" else "-part${part + 1}"
        val displayName =
            "you-gatt-me-ble-${fileTime.format(captureStartedAt)}$suffix.jsonl"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/YouGattMe/BLE captures"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        return runCatching {
            val uri = checkNotNull(
                context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                )
            ) { "MediaStore refused BLE capture file" }
            val stream = checkNotNull(context.contentResolver.openOutputStream(uri, "w")) {
                "Cannot open BLE capture output"
            }
            writer = BufferedWriter(OutputStreamWriter(stream, StandardCharsets.UTF_8))
            bytesWritten = 0
            pendingEvents = 0
            lastFlushNanos = System.nanoTime()
            currentFileName = displayName
            true
        }.getOrElse { failure ->
            lastError = failure.message ?: failure.javaClass.simpleName
            currentFileName = null
            false
        }
    }

    private fun closeFile() {
        runCatching {
            writer?.flush()
            writer?.close()
        }.onFailure { failure ->
            lastError = failure.message ?: failure.javaClass.simpleName
        }
        writer = null
        currentFileName = null
        bytesWritten = 0
        pendingEvents = 0
        lastFlushNanos = 0L
    }

    private const val FLUSH_INTERVAL_NANOS = 1_000_000_000L
}
