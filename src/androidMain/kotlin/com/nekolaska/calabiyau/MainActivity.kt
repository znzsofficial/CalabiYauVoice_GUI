package com.nekolaska.calabiyau

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import com.nekolaska.calabiyau.data.AppPrefs
import com.nekolaska.calabiyau.data.OfflineCache
import com.nekolaska.calabiyau.data.UpdateApi
import com.nekolaska.calabiyau.data.WikiEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nekolaska.calabiyau.ui.LocalG2CornersEnabled
import com.nekolaska.calabiyau.ui.LocalLiquidGlassEnabled
import com.nekolaska.calabiyau.ui.MainScreen
import com.nekolaska.calabiyau.viewmodel.DownloadViewModel
import com.nekolaska.calabiyau.viewmodel.PortraitViewModel
import com.nekolaska.calabiyau.viewmodel.SearchViewModel
import data.PortraitRepository

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CrashHandler.install(this)
        NotificationHelper.createChannel(this)
        AppPrefs.init(this)
        OfflineCache.init(this)
        PortraitRepository.init(
            fetchFilesInCategory = { cat, audio -> WikiEngine.fetchFilesInCategory(cat, audio) },
            searchFilesFn = { kw, audio -> WikiEngine.searchFiles(kw, audio) },
            getAllCharacterNames = { WikiEngine.getAllCharacterNames() }
        )

        // 解析 Shortcut 目标
        val shortcutTarget = intent?.getStringExtra("shortcut_target")

        setContent {
            AppTheme {
                val searchVM: SearchViewModel = viewModel()
                val downloadVM: DownloadViewModel = viewModel()
                val portraitVM: PortraitViewModel = viewModel()

                // ── 启动时静默检查更新（每天最多一次） ──
                val context = LocalContext.current
                var startupUpdateInfo by remember { mutableStateOf<UpdateApi.UpdateInfo?>(null) }
                LaunchedEffect(Unit) {
                    val now = System.currentTimeMillis()
                    val lastCheck = AppPrefs.lastUpdateCheck
                    val oneDayMs = 24 * 60 * 60 * 1000L
                    if (now - lastCheck < oneDayMs) return@LaunchedEffect
                    val currentVersion = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: return@LaunchedEffect
                    } catch (_: Exception) { return@LaunchedEffect }
                    when (val result = UpdateApi.checkUpdate(currentVersion)) {
                        is UpdateApi.Result.NewVersion -> {
                            startupUpdateInfo = result.info
                            AppPrefs.lastUpdateCheck = now
                        }
                        is UpdateApi.Result.AlreadyLatest -> {
                            AppPrefs.lastUpdateCheck = now
                        }
                        is UpdateApi.Result.Error -> { /* 静默失败 */ }
                    }
                }

                MainScreen(searchVM, downloadVM, portraitVM, shortcutTarget = shortcutTarget)

                // ── 启动更新提示 ──
                startupUpdateInfo?.let { info ->
                    val curVer = remember {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
                        } catch (_: Exception) { "?" }
                    }
                    AlertDialog(
                        onDismissRequest = { startupUpdateInfo = null },
                        title = { Text("发现新版本 ${info.versionName}") },
                        text = {
                            Column {
                                Text(
                                    "当前版本: $curVer",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (info.body.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = info.body.take(500),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            FilledTonalButton(onClick = {
                                startupUpdateInfo = null
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl))
                                )
                            }) { Text("前往下载") }
                        },
                        dismissButton = {
                            TextButton(onClick = { startupUpdateInfo = null }) {
                                Text("稍后再说")
                            }
                        }
                    )
                }
            }
        }
    }
}

/** 全局主题模式状态，可从任何 Composable 中读取/修改 */
val LocalThemeMode = staticCompositionLocalOf { mutableIntStateOf(AppPrefs.themeMode) }

/** 全局自定义种子色状态（ARGB Int，-1=跟随壁纸，0=系统默认，其他=自定义色） */
val LocalSeedColor = staticCompositionLocalOf { mutableIntStateOf(AppPrefs.customSeedColor) }

/** 从壁纸提取的主题色（ARGB Int，0 = 尚未提取） */
val LocalWallpaperSeedColor = staticCompositionLocalOf { mutableIntStateOf(0) }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val themeMode = remember { mutableIntStateOf(AppPrefs.themeMode) }
    val seedColor = remember { mutableIntStateOf(AppPrefs.customSeedColor) }
    val wallpaperSeedColor = remember { mutableIntStateOf(AppPrefs.wallpaperSeedColorCache) }
    val liquidGlassEnabled = remember { mutableStateOf(AppPrefs.liquidGlassEnabled) }
    val g2CornersEnabled = remember { mutableStateOf(AppPrefs.g2CornersEnabled) }

    // 当选择"跟随背景图"时，立即从缓存的壁纸 URL 提取主题色
    LaunchedEffect(seedColor.intValue) {
        if (seedColor.intValue != AppPrefs.SEED_WALLPAPER) return@LaunchedEffect
        if (wallpaperSeedColor.intValue != 0) return@LaunchedEffect  // 已提取过
        val url = AppPrefs.wallpaperUrl ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder().url(url).build()
                val bytes = WikiEngine.client.newCall(request).execute().body.bytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@withContext
                val palette = Palette.from(bitmap).generate()
                val color = palette.getVibrantColor(palette.getMutedColor(0))
                if (color != 0) {
                    wallpaperSeedColor.intValue = color
                    AppPrefs.wallpaperSeedColorCache = color
                }
                bitmap.recycle()
            } catch (_: Exception) { }
        }
    }

    val darkTheme = when (themeMode.intValue) {
        AppPrefs.THEME_LIGHT -> false
        AppPrefs.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current

    // 解析实际生效的种子色
    val effectiveSeed = when (seedColor.intValue) {
        AppPrefs.SEED_WALLPAPER -> wallpaperSeedColor.intValue  // 跟随壁纸，未取到时为 0（回退系统色）
        else -> seedColor.intValue                               // 0=系统色，其他=自定义色
    }

    val colorScheme = when {
        effectiveSeed != 0 -> colorSchemeFromSeed(Color(effectiveSeed), darkTheme)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    CompositionLocalProvider(
        LocalThemeMode provides themeMode,
        LocalSeedColor provides seedColor,
        LocalWallpaperSeedColor provides wallpaperSeedColor,
        LocalLiquidGlassEnabled provides liquidGlassEnabled,
        LocalG2CornersEnabled provides g2CornersEnabled
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

/**
 * 从种子色生成完整的 Material3 配色方案。
 * 通过 HSL 色彩空间派生出各色调层级。
 */
private fun colorSchemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    // 使用 HSL 色彩空间（不是 HSV），lightness 参数才是真正的亮度
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(seed.toArgb(), hsl)
    val h = hsl[0]  // 色相 0-360
    val s = hsl[1]  // 饱和度 0-1

    /** 从种子色的色相出发，生成指定亮度和饱和度倍率的颜色 */
    fun tone(lightness: Float, satMul: Float = 1f): Color {
        val outHsl = floatArrayOf(h, (s * satMul).coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
        return Color(androidx.core.graphics.ColorUtils.HSLToColor(outHsl))
    }

    // 中性色饱和度倍率（surface / background / outline 等）
    val ns = 0.12f

    return if (dark) {
        darkColorScheme(
            primary = tone(0.80f),
            onPrimary = tone(0.20f),
            primaryContainer = tone(0.30f),
            onPrimaryContainer = tone(0.90f),
            secondary = tone(0.75f, 0.6f),
            onSecondary = tone(0.20f, 0.6f),
            secondaryContainer = tone(0.30f, 0.5f),
            onSecondaryContainer = tone(0.90f, 0.5f),
            tertiary = tone(0.80f, 0.4f),
            onTertiary = tone(0.20f, 0.4f),
            tertiaryContainer = tone(0.30f, 0.4f),
            onTertiaryContainer = tone(0.90f, 0.4f),
            // HSL lightness 在暗色端偏暗于 HCT tone，需要适当补偿
            // HCT tone 6 ≈ HSL 0.08, tone 10 ≈ HSL 0.14, tone 12 ≈ HSL 0.16
            background = tone(0.08f, ns),
            onBackground = tone(0.90f, ns),
            surface = tone(0.08f, ns),
            onSurface = tone(0.90f, ns),
            surfaceVariant = tone(0.28f, 0.25f),
            onSurfaceVariant = tone(0.80f, 0.25f),
            surfaceTint = tone(0.80f),
            inverseSurface = tone(0.90f, ns),
            inverseOnSurface = tone(0.20f, ns),
            inversePrimary = tone(0.40f),
            outline = tone(0.60f, 0.2f),
            outlineVariant = tone(0.30f, 0.2f),
            surfaceBright = tone(0.28f, ns),       // HCT tone 24
            surfaceDim = tone(0.08f, ns),          // HCT tone 6
            surfaceContainer = tone(0.16f, ns),    // HCT tone 12
            surfaceContainerHigh = tone(0.21f, ns),// HCT tone 17
            surfaceContainerHighest = tone(0.26f, ns),// HCT tone 22
            surfaceContainerLow = tone(0.14f, ns), // HCT tone 10
            surfaceContainerLowest = tone(0.06f, ns),// HCT tone 4
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        lightColorScheme(
            primary = tone(0.40f),
            onPrimary = Color.White,
            primaryContainer = tone(0.90f),
            onPrimaryContainer = tone(0.10f),
            secondary = tone(0.45f, 0.6f),
            onSecondary = Color.White,
            secondaryContainer = tone(0.90f, 0.5f),
            onSecondaryContainer = tone(0.10f, 0.5f),
            tertiary = tone(0.45f, 0.4f),
            onTertiary = Color.White,
            tertiaryContainer = tone(0.90f, 0.4f),
            onTertiaryContainer = tone(0.10f, 0.4f),
            background = tone(0.98f, ns),
            onBackground = tone(0.10f, ns),
            surface = tone(0.98f, ns),
            onSurface = tone(0.10f, ns),
            surfaceVariant = tone(0.90f, 0.2f),
            onSurfaceVariant = tone(0.30f, 0.25f),
            surfaceTint = tone(0.40f),
            inverseSurface = tone(0.20f, ns),
            inverseOnSurface = tone(0.95f, ns),
            inversePrimary = tone(0.80f),
            outline = tone(0.50f, 0.25f),
            outlineVariant = tone(0.80f, 0.15f),
            surfaceBright = tone(0.98f, ns),
            surfaceDim = tone(0.87f, ns),
            surfaceContainer = tone(0.94f, ns),
            surfaceContainerHigh = tone(0.92f, ns),
            surfaceContainerHighest = tone(0.90f, ns),
            surfaceContainerLow = tone(0.96f, ns),
            surfaceContainerLowest = Color.White,
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        )
    }
}
