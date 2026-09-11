package com.expensetracker.app.diagnostics

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal local error/crash log — deliberately not a third-party crash-reporting SDK, to keep
 * the "nothing leaves the device" privacy guarantee. Appends timestamped entries to a plain text
 * file in app-private storage so a future crash is diagnosable from this file instead of
 * requiring a fresh `adb logcat` pull every time.
 *
 * Call [record] from any `catch` block that would otherwise let an exception propagate and take
 * the whole app down — it logs and moves on rather than silently swallowing (Log.e still shows
 * in logcat) or crashing.
 */
object CrashLog {
    private const val TAG = "ExpenseTrackerDiag"
    private const val FILE_NAME = "diagnostic_log.txt"
    private const val MAX_FILE_BYTES = 512 * 1024L // trimmed well before this could matter

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    fun record(context: Context, tag: String, throwable: Throwable) {
        Log.e(TAG, tag, throwable)
        appendSafely(context) {
            val trace = throwable.stackTraceToString().lineSequence().take(15).joinToString("\n")
            "${timestampFormat.format(Date())} [$tag] ${throwable.javaClass.simpleName}: ${throwable.message}\n$trace\n\n"
        }
    }

    @Synchronized
    fun record(context: Context, tag: String, message: String) {
        Log.e(TAG, "$tag: $message")
        appendSafely(context) { "${timestampFormat.format(Date())} [$tag] $message\n" }
    }

    fun logFile(context: Context): File = File(context.filesDir, FILE_NAME)

    /** Null if nothing has been logged yet (no point offering to share an empty/missing file). */
    fun shareIntent(context: Context): Intent? {
        val file = logFile(context)
        if (!file.exists() || file.length() == 0L) return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Installs a last-resort handler that logs a truly fatal (uncaught) crash before it takes
     * the process down, then hands off to the platform's normal handler so crash behavior is
     * otherwise unchanged. Call once, early in Application.onCreate(). */
    fun installUncaughtExceptionLogger(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                record(context.applicationContext, "FATAL-${thread.name}", throwable)
            } catch (_: Throwable) {
                // Never let logging itself block the crash from being reported normally.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private inline fun appendSafely(context: Context, entry: () -> String) {
        try {
            val file = logFile(context)
            if (file.exists() && file.length() > MAX_FILE_BYTES) file.delete()
            file.appendText(entry())
        } catch (_: Exception) {
            // Logging must never itself throw and mask the original failure.
        }
    }
}
