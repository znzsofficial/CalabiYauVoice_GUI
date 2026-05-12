package com.nekolaska.calabiyau.core.preferences

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
    private const val KEY_OFFLINE_CACHE_NEVER_EXPIRE = "offline_cache_never_expire"
    private const val KEY_DOWNLOAD_HISTORY = "download_history"
    private const val KEY_BOTTOM_BAR_STYLE = "bottom_bar_style"
    private const val KEY_CUSTOM_SEED_COLOR = "custom_seed_color"
    private const val KEY_WIKI_DESKTOP_MODE = "wiki_desktop_mode"
    private const val KEY_LIQUID_GLASS_ENABLED = "liquid_glass_enabled"
    //private const val KEY_G2_CORNERS_ENABLED = "g2_corners_enabled"
    private const val KEY_WALLPAPER_URL = "wallpaper_url"
    private const val KEY_WALLPAPER_AUTO_REFRESH = "wallpaper_auto_refresh"
    private const val KEY_WALLPAPER_SEED_COLOR_CACHE = "wallpaper_seed_color_cache"
    private const val KEY_WALLPAPER_SEED_COLOR_URL = "wallpaper_seed_color_url"
    private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    private const val KEY_HOME_QUICK_ENTRY_IDS = "home_quick_entry_ids"
    private const val KEY_TOOLS_OUTPUT_PATH = "tools_output_path"
    private const val KEY_AUDIO_SPECTROGRAM_WINDOW_SIZE = "audio_spectrogram_window_size"
    private const val KEY_AUDIO_SPECTROGRAM_HOP_PERCENT = "audio_spectrogram_hop_percent"
    private const val KEY_AUDIO_SPECTROGRAM_TIME_BINS = "audio_spectrogram_time_bins"
    private const val KEY_AUDIO_SPECTROGRAM_FREQUENCY_BINS = "audio_spectrogram_frequency_bins"
    private const val KEY_AUDIO_SPECTROGRAM_CUTOFF_HZ = "audio_spectrogram_cutoff_hz"
    private const val KEY_AUDIO_SPECTROGRAM_PALETTE = "audio_spectrogram_palette"

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

    /** 离线缓存是否永不过期。开启后启动清理不会删除过期 Wiki 缓存。 */
    var offlineCacheNeverExpire: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_CACHE_NEVER_EXPIRE, false)
        set(value) = prefs.edit { putBoolean(KEY_OFFLINE_CACHE_NEVER_EXPIRE, value) }

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

    /** 缓存的首页壁纸 URL（空字符串表示未缓存） */
    var wallpaperUrl: String?
        get() = prefs.getString(KEY_WALLPAPER_URL, null)
        set(value) = prefs.edit { putString(KEY_WALLPAPER_URL, value) }

    /** 启动时是否自动刷新壁纸（默认 false） */
    var wallpaperAutoRefresh: Boolean
        get() = prefs.getBoolean(KEY_WALLPAPER_AUTO_REFRESH, false)
        set(value) = prefs.edit { putBoolean(KEY_WALLPAPER_AUTO_REFRESH, value) }

    /** 缓存的壁纸主题色（ARGB Int，0 = 未缓存） */
    var wallpaperSeedColorCache: Int
        get() = prefs.getInt(KEY_WALLPAPER_SEED_COLOR_CACHE, 0)
        set(value) = prefs.edit { putInt(KEY_WALLPAPER_SEED_COLOR_CACHE, value) }

    /** 缓存主题色对应的壁纸 URL（用于判断壁纸是否更换） */
    var wallpaperSeedColorUrl: String?
        get() = prefs.getString(KEY_WALLPAPER_SEED_COLOR_URL, null)
        set(value) = prefs.edit { putString(KEY_WALLPAPER_SEED_COLOR_URL, value) }

    /** 上次检查更新的时间戳（毫秒） */
    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_UPDATE_CHECK, value) }

    /** 首页顶部六按钮的配置 ID 列表，最多 6 个。 */
    var homeQuickEntryIds: List<String>
        get() = prefs.getString(KEY_HOME_QUICK_ENTRY_IDS, null)
            ?.split("|||")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(6)
            ?: emptyList()
        set(value) = prefs.edit {
            putString(KEY_HOME_QUICK_ENTRY_IDS, value.take(6).joinToString("|||"))
        }

    /** 素材工具默认输出目录 */
    var toolsOutputPath: String
        get() = prefs.getString(KEY_TOOLS_OUTPUT_PATH, null)
            ?: File(savePath, "素材工具").absolutePath
        set(value) = prefs.edit { putString(KEY_TOOLS_OUTPUT_PATH, value) }

    /** 音频工具频谱图 FFT 窗长 */
    var audioSpectrogramWindowSize: Int
        get() = prefs.getInt(KEY_AUDIO_SPECTROGRAM_WINDOW_SIZE, 1024)
        set(value) = prefs.edit { putInt(KEY_AUDIO_SPECTROGRAM_WINDOW_SIZE, value.coerceIn(256, 8192)) }

    /** 音频工具频谱图步进比例（百分比） */
    var audioSpectrogramHopPercent: Int
        get() = prefs.getInt(KEY_AUDIO_SPECTROGRAM_HOP_PERCENT, 25)
        set(value) = prefs.edit { putInt(KEY_AUDIO_SPECTROGRAM_HOP_PERCENT, value.coerceIn(5, 80)) }

    /** 音频工具频谱图时间桶数量 */
    var audioSpectrogramTimeBins: Int
        get() = prefs.getInt(KEY_AUDIO_SPECTROGRAM_TIME_BINS, 720)
        set(value) = prefs.edit { putInt(KEY_AUDIO_SPECTROGRAM_TIME_BINS, value.coerceIn(120, 2000)) }

    /** 音频工具频谱图频率桶数量 */
    var audioSpectrogramFrequencyBins: Int
        get() = prefs.getInt(KEY_AUDIO_SPECTROGRAM_FREQUENCY_BINS, 256)
        set(value) = prefs.edit { putInt(KEY_AUDIO_SPECTROGRAM_FREQUENCY_BINS, value.coerceIn(64, 1024)) }

    /** 音频工具频谱图截止频率（Hz，0 表示自动使用 Nyquist） */
    var audioSpectrogramCutoffHz: Int
        get() = prefs.getInt(KEY_AUDIO_SPECTROGRAM_CUTOFF_HZ, 0)
        set(value) = prefs.edit { putInt(KEY_AUDIO_SPECTROGRAM_CUTOFF_HZ, value.coerceIn(0, 96000)) }

    /** 音频工具频谱图配色方案 */
    var audioSpectrogramPalette: String
        get() = prefs.getString(KEY_AUDIO_SPECTROGRAM_PALETTE, "Ocean") ?: "Ocean"
        set(value) = prefs.edit { putString(KEY_AUDIO_SPECTROGRAM_PALETTE, value) }

}
