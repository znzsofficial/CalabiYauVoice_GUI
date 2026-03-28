package com.nekolaska.calabiyau.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File

/**
 * Android 端应用偏好存储，使用 SharedPreferences 持久化。
 */
object AppPrefs {

    private const val PREFS_NAME = "calabiyau_prefs"
    private const val KEY_SAVE_PATH = "save_path"
    private const val KEY_MAX_CONCURRENCY = "max_concurrency"

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 默认保存路径：优先使用公共 Downloads/CalabiYauVoice，
     * 若不可用则回退到应用专属外部存储目录。
     */
    private val defaultSavePath: String
        get() {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val preferred = File(downloadsDir, "CalabiYauVoice")
            // 尝试确保目录存在；若公共目录不可写则回退
            if (preferred.exists() || preferred.mkdirs()) return preferred.absolutePath
            return File(appContext.getExternalFilesDir(null), "CalabiYauVoice").absolutePath
        }

    var savePath: String
        get() = prefs.getString(KEY_SAVE_PATH, null) ?: defaultSavePath
        set(value) = prefs.edit().putString(KEY_SAVE_PATH, value).apply()

    var maxConcurrency: Int
        get() = prefs.getInt(KEY_MAX_CONCURRENCY, 8)
        set(value) = prefs.edit().putInt(KEY_MAX_CONCURRENCY, value.coerceIn(1, 32)).apply()
}
