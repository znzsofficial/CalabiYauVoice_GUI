package com.nekolaska.calabiyau.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File
import androidx.core.content.edit

/**
 * Android 端应用偏好存储，使用 SharedPreferences 持久化。
 */
object AppPrefs {

    private const val PREFS_NAME = "calabiyau_prefs"
    private const val KEY_SAVE_PATH = "save_path"
    private const val KEY_MAX_CONCURRENCY = "max_concurrency"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_SEARCH_HISTORY = "search_history"
    private const val KEY_FAVORITE_CHARACTERS = "favorite_characters"
    private const val KEY_WIKI_CACHE_MODE = "wiki_cache_mode"
    private const val KEY_DOWNLOAD_HISTORY = "download_history"
    private const val KEY_BOTTOM_BAR_STYLE = "bottom_bar_style"
    private const val KEY_CUSTOM_SEED_COLOR = "custom_seed_color"
    private const val KEY_WIKI_DESKTOP_MODE = "wiki_desktop_mode"
    private const val KEY_LIQUID_GLASS_ENABLED = "liquid_glass_enabled"
    private const val KEY_G2_CORNERS_ENABLED = "g2_corners_enabled"
    private const val KEY_WALLPAPER_URL = "wallpaper_url"
    private const val KEY_WALLPAPER_AUTO_REFRESH = "wallpaper_auto_refresh"

    /** 底栏样式：0=DockedToolbar（悬浮工具栏）, 1=BottomAppBar（经典导航栏） */
    const val BAR_STYLE_DOCKED_TOOLBAR = 0
    const val BAR_STYLE_BOTTOM_APP_BAR = 1

    /** 主题模式：0=跟随系统, 1=浅色, 2=深色 */
    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    /** Wiki 缓存模式：0=默认, 1=优先缓存 */
    const val WIKI_CACHE_DEFAULT = 0
    const val WIKI_CACHE_OFFLINE_FIRST = 1

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
        set(value) = prefs.edit {putString(KEY_SAVE_PATH, value)}

    var maxConcurrency: Int
        get() = prefs.getInt(KEY_MAX_CONCURRENCY, 8)
        set(value) = prefs.edit { putInt(KEY_MAX_CONCURRENCY, value.coerceIn(1, 32))}

    /** 主题模式 */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM)
        set(value) = prefs.edit {putInt(KEY_THEME_MODE, value)}

    /** 搜索历史（最多 20 条，逗号分隔存储） */
    var searchHistory: List<String>
        get() = prefs.getString(KEY_SEARCH_HISTORY, null)
            ?.split("|||")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) = prefs.edit {
            putString(
                KEY_SEARCH_HISTORY,
                value.take(20).joinToString("|||")
        )}

    fun addSearchHistory(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return
        val current = searchHistory.toMutableList()
        current.remove(trimmed) // 去重
        current.add(0, trimmed) // 置顶
        searchHistory = current.take(20)
    }

    fun clearSearchHistory() {
        searchHistory = emptyList()
    }

    /** 收藏的角色名列表 */
    var favoriteCharacters: Set<String>
        get() = prefs.getStringSet(KEY_FAVORITE_CHARACTERS, emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_FAVORITE_CHARACTERS, value)}

    fun toggleFavorite(name: String) {
        val current = favoriteCharacters.toMutableSet()
        if (name in current) current.remove(name) else current.add(name)
        favoriteCharacters = current
    }

    /** 底栏样式 */
    var bottomBarStyle: Int
        get() = prefs.getInt(KEY_BOTTOM_BAR_STYLE, BAR_STYLE_BOTTOM_APP_BAR)
        set(value) = prefs.edit { putInt(KEY_BOTTOM_BAR_STYLE, value)}

    /** Wiki 离线缓存模式 */
    var wikiCacheMode: Int
        get() = prefs.getInt(KEY_WIKI_CACHE_MODE, WIKI_CACHE_DEFAULT)
        set(value) = prefs.edit { putInt(KEY_WIKI_CACHE_MODE, value)}

    /**
     * 自定义主题种子色（ARGB Int）。
     * [SEED_WALLPAPER] (-1) = 跟随背景图主题色（默认）
     * 0 = 使用系统动态取色 / 默认配色
     * 其他正整数 = 自定义 ARGB 色值
     */
    const val SEED_WALLPAPER = -1

    var customSeedColor: Int
        get() = prefs.getInt(KEY_CUSTOM_SEED_COLOR, SEED_WALLPAPER)
        set(value) = prefs.edit { putInt(KEY_CUSTOM_SEED_COLOR, value) }

    /** Wiki 桌面模式 UA（默认 false） */
    var wikiDesktopMode: Boolean
        get() = prefs.getBoolean(KEY_WIKI_DESKTOP_MODE, false)
        set(value) = prefs.edit { putBoolean(KEY_WIKI_DESKTOP_MODE, value) }

    /** 下载历史记录（JSON 格式存储） */
    var downloadHistoryJson: String
        get() = prefs.getString(KEY_DOWNLOAD_HISTORY, "[]") ?: "[]"
        set(value) = prefs.edit { putString(KEY_DOWNLOAD_HISTORY, value) }

    /** 液态玻璃效果（默认关闭，需 Android 12+） */
    var liquidGlassEnabled: Boolean
        get() = prefs.getBoolean(KEY_LIQUID_GLASS_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_LIQUID_GLASS_ENABLED, value) }

    /** G2 连续圆角（默认关闭） */
    var g2CornersEnabled: Boolean
        get() = prefs.getBoolean(KEY_G2_CORNERS_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_G2_CORNERS_ENABLED, value) }

    /** 缓存的首页壁纸 URL（空字符串表示未缓存） */
    var wallpaperUrl: String?
        get() = prefs.getString(KEY_WALLPAPER_URL, null)
        set(value) = prefs.edit { putString(KEY_WALLPAPER_URL, value) }

    /** 启动时是否自动刷新壁纸（默认 true） */
    var wallpaperAutoRefresh: Boolean
        get() = prefs.getBoolean(KEY_WALLPAPER_AUTO_REFRESH, true)
        set(value) = prefs.edit { putBoolean(KEY_WALLPAPER_AUTO_REFRESH, value) }
}
