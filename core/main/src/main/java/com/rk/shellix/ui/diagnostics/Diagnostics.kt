package com.rk.shellix.ui.diagnostics

import android.app.Application
import android.os.Build
import android.util.Log
import com.rk.shellix.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingDeque

/**
 * In-app diagnostics: a bounded in-memory ring buffer of app log lines plus the
 * last uncaught crash report. Both are surfaced in the Settings → Diagnostics
 * viewer so failures are debuggable without pulling Logcat.
 */
object Diagnostics {

    private const val MAX_LINES = 2000

    // Tagged ring buffer. Each entry is "HH:mm:ss.SSS LEVEL/TAG: message".
    private val buffer = LinkedBlockingDeque<String>(MAX_LINES)

    @Volatile var lastCrashReport: String? = null
        private set

    var notifyOnPluginErrors: Boolean = true

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(level: Int, tag: String, msg: String) {
        val levelStr = when (level) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "?"
        }
        val line = "${timeFmt.format(Date())} $levelStr/$tag: $msg"
        if (buffer.size >= MAX_LINES) buffer.pollFirst()
        buffer.addLast(line)
    }

    fun v(tag: String, msg: String) = log(Log.VERBOSE, tag, msg)
    fun d(tag: String, msg: String) = log(Log.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(Log.INFO, tag, msg)
    fun w(tag: String, msg: String) = log(Log.WARN, tag, msg)
    fun e(tag: String, msg: String) = log(Log.ERROR, tag, msg)

    /** Snapshot of the ring buffer, oldest first. */
    fun snapshot(): List<String> = synchronized(buffer) { ArrayList(buffer) }

    fun clear() = synchronized(buffer) { buffer.clear() }

    /**
     * Install a custom [Thread.getDefaultUncaughtExceptionHandler] that records
     * the stack trace to [lastCrashReport] + persists it to
     * `filesDir/crash_report.txt`, then delegates to the previous handler so the
     * process still terminates cleanly.
     */
    fun installCrashHandler(application: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val report = buildString {
                appendLine("Shellix crash report")
                appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                appendLine("Thread: ${thread.name}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
                appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine()
                appendLine(Log.getStackTraceString(throwable))
            }
            lastCrashReport = report
            runCatching {
                File(application.filesDir, "crash_report.txt").writeText(report)
            }
            e("Crash", report)
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Persisted crash report from a previous launch, if any. */
    fun loadPersistedCrash(application: Application): String? {
        return runCatching {
            File(application.filesDir, "crash_report.txt").takeIf { it.exists() }?.readText()
        }.getOrNull()
    }
}
