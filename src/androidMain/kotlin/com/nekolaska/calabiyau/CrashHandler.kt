package com.nekolaska.calabiyau

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import kotlin.system.exitProcess
import androidx.core.content.edit

/**
 * 全局未捕获异常处理器。
 *
 * 捕获崩溃后将堆栈信息写入 SharedPreferences（使用 commit 确保同步写入），
 * 然后启动 [CrashReportActivity] 显示崩溃日志。
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val PREFS_NAME = "crash_prefs"
    private const val KEY_CRASH_LOG = "crash_log"
    private const val KEY_CRASH_TIME = "crash_time"

    private lateinit var appContext: Context
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun install(context: Context) {
        appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val log = buildString {
                appendLine("═══ 崩溃报告 ═══")
                appendLine()
                appendLine("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                appendLine("线程: ${thread.name}")
                appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                try {
                    val pm = appContext.packageManager
                    val pi = pm.getPackageInfo(appContext.packageName, 0)
                    @Suppress("DEPRECATION")
                    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong()
                    appendLine("版本: ${pi.versionName} ($code)")
                } catch (_: Exception) {}
                appendLine()
                appendLine("═══ 异常堆栈 ═══")
                appendLine()
                appendLine(throwable.stackTraceToString())
                // 如果有 cause 链，也打印
                var cause = throwable.cause
                while (cause != null) {
                    appendLine()
                    appendLine("═══ Caused by ═══")
                    appendLine(cause.stackTraceToString())
                    cause = cause.cause
                }
            }

            // 同步写入（commit），确保崩溃时数据不丢失
            prefs(appContext).edit(commit = true) {
                putString(KEY_CRASH_LOG, log)
                putLong(KEY_CRASH_TIME, System.currentTimeMillis())
            }

            // 启动崩溃报告页面
            val intent = Intent(appContext, CrashReportActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            appContext.startActivity(intent)
        } catch (_: Exception) {
            // 如果连崩溃处理都失败了，交给系统默认处理
            defaultHandler?.uncaughtException(thread, throwable)
        }

        Process.killProcess(Process.myPid())
        exitProcess(1)
    }

    /** 读取上次崩溃日志（读取后清除） */
    fun consumeCrashLog(context: Context): String? {
        val prefs = prefs(context)
        val log = prefs.getString(KEY_CRASH_LOG, null)?.takeIf { it.isNotBlank() }
        if (prefs.contains(KEY_CRASH_LOG) || prefs.contains(KEY_CRASH_TIME)) {
            prefs.edit(commit = true) {
                remove(KEY_CRASH_LOG)
                remove(KEY_CRASH_TIME)
            }
        }
        return log
    }

    /** 检查是否有未读的崩溃日志 */
    fun hasPendingCrashLog(context: Context): Boolean {
        val prefs = prefs(context)
        val log = prefs.getString(KEY_CRASH_LOG, null)
        val hasValidLog = !log.isNullOrBlank()

        if (!hasValidLog && (prefs.contains(KEY_CRASH_LOG) || prefs.contains(KEY_CRASH_TIME))) {
            prefs.edit(commit = true) {
                remove(KEY_CRASH_LOG)
                remove(KEY_CRASH_TIME)
            }
        }

        return hasValidLog
    }
}
