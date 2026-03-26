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

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val defaultSavePath: String
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "CalabiYauVoice"
        ).absolutePath

    var savePath: String
        get() = prefs.getString(KEY_SAVE_PATH, null) ?: defaultSavePath
        set(value) = prefs.edit().putString(KEY_SAVE_PATH, value).apply()

    var maxConcurrency: Int
        get() = prefs.getInt(KEY_MAX_CONCURRENCY, 8)
        set(value) = prefs.edit().putInt(KEY_MAX_CONCURRENCY, value.coerceIn(1, 32)).apply()
}
