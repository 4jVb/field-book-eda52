package com.fieldbook.tracker.utilities

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.fieldbook.tracker.BuildConfig
import com.fieldbook.tracker.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EDA52 / Breedbase build — error log you can share without a computer.
 *
 *  - Uncaught crashes are written to a file (stack trace + the app's own logcat tail). The next
 *    time the app opens it asks whether to share that report.
 *  - [shareLog] (Settings > System > Share error log) shares the app's recent log at any time,
 *    which includes every Log.e/Log.w the app wrote (BrAPI failures, list errors, sync errors).
 */
object ErrorLog {

    private const val TAG = "ErrorLog"
    private const val DIR = "error_logs"
    private const val MAX_SHARE_CHARS = 120_000

    @Volatile
    private var prompted = false

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(app, thread, throwable)
            } catch (_: Throwable) {
                // never let the logger hide the real crash
            }
            previous?.uncaughtException(thread, throwable)
        }

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                promptIfPendingCrash(activity)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun logDir(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    private fun header(): String = buildString {
        append("Field Book EDA52 ${BuildConfig.VERSION_NAME}\n")
        append("Fecha: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        append("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})\n\n")
    }

    private fun readLogcat(lines: Int): String = try {
        val pid = android.os.Process.myPid()
        var text = execLogcat(arrayOf("logcat", "-d", "-t", lines.toString(), "--pid=$pid"))
        if (text.isBlank()) text = execLogcat(arrayOf("logcat", "-d", "-t", lines.toString()))
        text
    } catch (e: Throwable) {
        "(no se pudo leer logcat: ${e.message})"
    }

    private fun execLogcat(cmd: Array<String>): String {
        val process = Runtime.getRuntime().exec(cmd)
        val out = process.inputStream.bufferedReader().use { it.readText() }
        process.destroy()
        return out
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(logDir(context), "crash_$stamp.txt")
        file.writeText(
            header() +
                "CIERRE INESPERADO en hilo '${thread.name}'\n" +
                Log.getStackTraceString(throwable) +
                "\n--- logcat reciente ---\n" + readLogcat(300)
        )
    }

    private fun promptIfPendingCrash(activity: Activity) {
        if (prompted) return
        val pending = logDir(activity).listFiles { f -> f.name.startsWith("crash_") }
            ?.maxByOrNull { it.lastModified() } ?: return
        prompted = true
        try {
            AlertDialog.Builder(activity)
                .setTitle(R.string.error_log_crash_title)
                .setMessage(R.string.error_log_crash_message)
                .setPositiveButton(R.string.error_log_share) { _, _ ->
                    send(activity, pending.readText().takeLast(MAX_SHARE_CHARS))
                    deleteAll(activity)
                }
                .setNegativeButton(R.string.error_log_discard) { _, _ -> deleteAll(activity) }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Could not show the crash dialog", e)
        }
    }

    private fun deleteAll(context: Context) {
        logDir(context).listFiles()?.forEach { it.delete() }
    }

    /** Shares the app's recent log (all levels) as text. */
    fun shareLog(activity: Activity) {
        val text = (header() + readLogcat(3000)).takeLast(MAX_SHARE_CHARS)
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val dir = activity.getExternalFilesDir("logs")
            if (dir != null) {
                dir.mkdirs()
                File(dir, "log_$stamp.txt").writeText(text)
            }
        } catch (_: Exception) {
        }
        send(activity, text)
    }

    private fun send(activity: Activity, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Registro de errores Field Book EDA52")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.error_log_share)))
    }
}
