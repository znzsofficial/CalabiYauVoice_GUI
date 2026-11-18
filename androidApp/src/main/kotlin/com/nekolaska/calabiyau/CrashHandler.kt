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
    private const val KEY_CRASH_NON_EMPTY = "crash_non_empty"

    const val EXTRA_CRASH_ID = "extra_crash_id"
    private fun records(context: Context) = CrashReportStore(context.noBackupFilesDir.resolve("crash_reports"))

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
            CrashContextStore.flush(context)
            val crashLog = buildCrashLog(thread, throwable, context)
            val id = records(context).save(crashLog)

            val intent = Intent(context, CrashReportActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_CRASH_ID, id)
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
            appendLine(boundedStackTrace(throwable))

            val crashContext = CrashContextStore.dump(context)
            if (crashContext.isNotBlank()) {
                appendLine()
                appendLine("==== Recent App Context ====")
                appendLine()
                appendLine(crashContext)
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

    fun pendingId(context: Context): String? {
        records(context).newestPending()?.let { return it }
        // Import the 2.2.1 marker so pending reports survive an application upgrade.
        val old = prefs(context).getString(KEY_CRASH_LOG, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { records(context).save(old) }.getOrNull()?.also { clearLegacyLog(context) }
    }

    fun open(context: Context, id: String) = records(context).open(id)
    fun finishReport(context: Context, id: String) = records(context).finish(id)

    private fun clearLegacyLog(context: Context) {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_CRASH_LOG) && !prefs.contains(KEY_CRASH_TIME) && !prefs.contains(KEY_CRASH_NON_EMPTY)) return
        prefs.edit(commit = true) {
            remove(KEY_CRASH_LOG)
            remove(KEY_CRASH_TIME)
            remove(KEY_CRASH_NON_EMPTY)
        }
        CrashContextStore.clear(context)
    }

    fun hasPendingCrashLog(context: Context): Boolean = pendingId(context) != null
}
