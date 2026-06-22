package com.nekolaska.calabiyau.core.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Android 端应用偏好存储，使用 SharedPreferences 持久化。
 */
object AppPrefs {

    private const val PREFS_NAME = "calabiyau_prefs"

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

    /** 首页快捷入口布局：0=网格大卡, 1=六按钮 */
    const val HOME_QUICK_LAYOUT_GRID = 0
    const val HOME_QUICK_LAYOUT_BUTTONS = 1

    /** 启动器图标主题：0=品牌深色, 1=系统强调色 */
    const val LAUNCHER_ICON_BRAND = 0
    const val LAUNCHER_ICON_SYSTEM = 1

    /**
     * 自定义主题种子色（ARGB Int）。
     * [SEED_WALLPAPER] (-1) = 跟随背景图主题色（默认）
     * 0 = 使用系统动态取色 / 默认配色
     * 其他正整数 = 自定义 ARGB 色值
     */
    const val SEED_WALLPAPER = -1

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun intPref(def: Int = 0) = object : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getInt(property.name, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) = prefs.edit { putInt(property.name, value) }
    }

    private fun floatPref(def: Float = 0f) = object : ReadWriteProperty<Any?, Float> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getFloat(property.name, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) = prefs.edit { putFloat(property.name, value) }
    }

    private fun booleanPref(def: Boolean = false) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getBoolean(property.name, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) = prefs.edit { putBoolean(property.name, value) }
    }

    private fun stringPref(def: String? = null) = object : ReadWriteProperty<Any?, String?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getString(property.name, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) = prefs.edit { putString(property.name, value) }
    }

    private fun stringSetPref(def: Set<String> = emptySet()) = object : ReadWriteProperty<Any?, Set<String>> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getStringSet(property.name, def) ?: def
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Set<String>) = prefs.edit { putStringSet(property.name, value) }
    }

    private fun longPref(def: Long = 0L) = object : ReadWriteProperty<Any?, Long> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getLong(property.name, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) = prefs.edit { putLong(property.name, value) }
    }

    private fun constrainedIntPref(def: Int, min: Int, max: Int) = object : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getInt(property.name, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) = prefs.edit { putInt(property.name, value.coerceIn(min, max)) }
    }

    private fun constrainedFloatPref(def: Float, min: Float, max: Float) = object : ReadWriteProperty<Any?, Float> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getFloat(property.name, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) = prefs.edit { putFloat(property.name, value.coerceIn(min, max)) }
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
        get() = prefs.getString("savePath", null) ?: defaultSavePath
        set(value) = prefs.edit { putString("savePath", value) }

    var maxConcurrency by constrainedIntPref(8, 1, 32)
    var themeMode by intPref(THEME_SYSTEM)

    /** 搜索历史（最多 20 条，逗号分隔存储） */
    var searchHistory: List<String>
        get() = prefs.getString("searchHistory", null)
            ?.split("|||")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) = prefs.edit {
            putString("searchHistory", value.take(20).joinToString("|||"))
        }

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

    var favoriteCharacters by stringSetPref()

    fun toggleFavorite(name: String) {
        val current = favoriteCharacters.toMutableSet()
        if (name in current) current.remove(name) else current.add(name)
        favoriteCharacters = current
    }

    var bottomBarStyle by intPref(BAR_STYLE_BOTTOM_APP_BAR)
    var wikiCacheMode by intPref(WIKI_CACHE_DEFAULT)
    var offlineCacheNeverExpire by booleanPref(false)
    var customSeedColor by intPref(SEED_WALLPAPER)
    var wikiDesktopMode by booleanPref(false)
    var downloadHistoryJson: String
        get() = prefs.getString("downloadHistoryJson", "[]") ?: "[]"
        set(value) = prefs.edit { putString("downloadHistoryJson", value) }

    var liquidGlassEnabled by booleanPref(false)
    var highReadabilityDrawer by booleanPref(false)

    var launcherIconTheme: Int
        get() = prefs.getInt("launcherIconTheme", LAUNCHER_ICON_BRAND)
        set(value) = prefs.edit {
            putInt("launcherIconTheme", if (value == LAUNCHER_ICON_SYSTEM) LAUNCHER_ICON_SYSTEM else LAUNCHER_ICON_BRAND)
        }

    var wallpaperUrl by stringPref()
    var wallpaperAutoRefresh by booleanPref(false)
    var wallpaperSeedColorCache by intPref(0)
    var wallpaperSeedColorUrl by stringPref()
    var lastUpdateCheck by longPref(0L)

    var homeQuickEntryIds: List<String>
        get() = prefs.getString("homeQuickEntryIds", null)
            ?.split("|||")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(6)
            ?: emptyList()
        set(value) = prefs.edit {
            putString("homeQuickEntryIds", value.take(6).joinToString("|||"))
        }

    var homeQuickEntryLayout: Int
        get() = prefs.getInt("homeQuickEntryLayout", HOME_QUICK_LAYOUT_GRID)
        set(value) = prefs.edit {
            putInt("homeQuickEntryLayout", if (value == HOME_QUICK_LAYOUT_BUTTONS) HOME_QUICK_LAYOUT_BUTTONS else HOME_QUICK_LAYOUT_GRID)
        }

    var toolsOutputPath: String
        get() = prefs.getString("toolsOutputPath", null)
            ?: File(savePath, "素材工具").absolutePath
        set(value) = prefs.edit { putString("toolsOutputPath", value) }

    var audioSpectrogramWindowSize by constrainedIntPref(1024, 256, 8192)
    var audioSpectrogramHopPercent by constrainedIntPref(25, 5, 80)
    var audioSpectrogramTimeBins by constrainedIntPref(1200, 120, 12000)
    var audioSpectrogramFrequencyBins by constrainedIntPref(512, 64, 2048)
    var audioSpectrogramCutoffHz by constrainedIntPref(0, 0, 96000)

    var audioSpectrogramPalette: String
        get() = prefs.getString("audioSpectrogramPalette", "Ocean") ?: "Ocean"
        set(value) = prefs.edit { putString("audioSpectrogramPalette", value) }

    var audioSpectrogramGainDb by constrainedFloatPref(6f, -24f, 36f)
    var audioSpectrogramGamma by constrainedFloatPref(1.2f, 0.35f, 3f)
    var audioSpectrogramFloorDb by constrainedFloatPref(-72f, -120f, -24f)

    /**
     * 配色风格索引，对应 materialkolor PaletteStyle 枚举顺序：
     *
     *  0 = TonalSpot（默认）
     *  1 = Neutral
     *  2 = Vibrant
     *  3 = Expressive
     *  4 = Rainbow
     *  5 = FruitSalad
     *  6 = Monochrome
     *  7 = Fidelity
     *  8 = Content
     */
    var paletteStyle by constrainedIntPref(0, 0, 8)
}
