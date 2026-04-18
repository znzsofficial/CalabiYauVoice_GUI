package com.nekolaska.calabiyau

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Global uncaught-exception handler for the main app process.
 *
 * It persists the crash log synchronously, then launches [CrashReportActivity]
 * in a separate process so the user can inspect and report the failure.
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val PREFS_NAME = "crash_prefs"
    private const val KEY_CRASH_LOG = "crash_log"
    private const val KEY_CRASH_TIME = "crash_time"

    const val EXTRA_CRASH_LOG = "extra_crash_log"

    private val installLock = Any()

    @Volatile
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    private lateinit var appContext: Context

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun install(context: Context) {
        synchronized(installLock) {
            appContext = context.applicationContext
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (currentHandler === this) return
            defaultHandler = currentHandler
            Thread.setDefaultUncaughtExceptionHandler(this)
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val fallbackHandler = defaultHandler?.takeUnless { it === this }
        val context = if (this::appContext.isInitialized) appContext else null

        if (context == null) {
            fallbackHandler?.uncaughtException(thread, throwable)
            Process.killProcess(Process.myPid())
            exitProcess(1)
        }

        try {
            val crashLog = buildCrashLog(thread, throwable, context)
            prefs(context).edit(commit = true) {
                putString(KEY_CRASH_LOG, crashLog)
                putLong(KEY_CRASH_TIME, System.currentTimeMillis())
            }

            val intent = Intent(context, CrashReportActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_CRASH_LOG, crashLog)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            fallbackHandler?.uncaughtException(thread, throwable)
        }

        Process.killProcess(Process.myPid())
        exitProcess(1)
    }

    private fun buildCrashLog(
        thread: Thread,
        throwable: Throwable,
        context: Context
    ): String {
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("==== Crash Report ====")
            appendLine()
            appendLine("Time: ${timeFormat.format(Date())}")
            appendLine("Thread: ${thread.name}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("System: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendAppVersion(context)
            appendLine()
            appendLine("==== Stack Trace ====")
            appendLine()
            appendLine(throwable.stackTraceToString())

            var cause = throwable.cause
            while (cause != null) {
                appendLine()
                appendLine("==== Caused By ====")
                appendLine(cause.stackTraceToString())
                cause = cause.cause
            }
        }
    }

    private fun StringBuilder.appendAppVersion(context: Context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
            appendLine("Version: ${packageInfo.versionName} ($versionCode)")
        } catch (_: Exception) {
            // Ignore version lookup failures inside crash reporting.
        }
    }

    fun peekCrashLog(context: Context): String? {
        val prefs = prefs(context)
        return prefs.getString(KEY_CRASH_LOG, null)?.takeIf { it.isNotBlank() }
    }

    fun clearCrashLog(context: Context) {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_CRASH_LOG) && !prefs.contains(KEY_CRASH_TIME)) return
        prefs.edit(commit = true) {
            remove(KEY_CRASH_LOG)
            remove(KEY_CRASH_TIME)
        }
    }

    fun hasPendingCrashLog(context: Context): Boolean {
        val prefs = prefs(context)
        val hasValidLog = !prefs.getString(KEY_CRASH_LOG, null).isNullOrBlank()

        if (!hasValidLog && (prefs.contains(KEY_CRASH_LOG) || prefs.contains(KEY_CRASH_TIME))) {
            clearCrashLog(context)
        }

        return hasValidLog
    }
}
