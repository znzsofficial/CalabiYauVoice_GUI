package com.nekolaska.calabiyau

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nekolaska.calabiyau.data.AppPrefs
import com.nekolaska.calabiyau.data.WikiEngine
import com.nekolaska.calabiyau.ui.MainScreen
import data.PortraitRepository

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppPrefs.init(this)
        PortraitRepository.init(
            fetchFilesInCategory = { cat, audio -> WikiEngine.fetchFilesInCategory(cat, audio) },
            searchFilesFn = { kw, audio -> WikiEngine.searchFiles(kw, audio) },
            getAllCharacterNames = { WikiEngine.getAllCharacterNames() }
        )

        setContent {
            AppTheme {
                val vm: MainViewModel = viewModel()
                MainScreen(vm)
            }
        }
    }
}

/** 全局主题模式状态，可从任何 Composable 中读取/修改 */
val LocalThemeMode = staticCompositionLocalOf { mutableIntStateOf(AppPrefs.themeMode) }

/** 全局自定义种子色状态（ARGB Int，0 = 系统默认） */
val LocalSeedColor = staticCompositionLocalOf { mutableIntStateOf(AppPrefs.customSeedColor) }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val themeMode = remember { mutableIntStateOf(AppPrefs.themeMode) }
    val seedColor = remember { mutableIntStateOf(AppPrefs.customSeedColor) }

    val darkTheme = when (themeMode.intValue) {
        AppPrefs.THEME_LIGHT -> false
        AppPrefs.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colorScheme = when {
        // 有自定义种子色时，用种子色生成配色方案
        seedColor.intValue != 0 -> {
            colorSchemeFromSeed(Color(seedColor.intValue), darkTheme)
        }
        // Android 12+ 动态取色
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    CompositionLocalProvider(
        LocalThemeMode provides themeMode,
        LocalSeedColor provides seedColor
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

/**
 * 从种子色生成完整的 Material3 配色方案。
 * 通过 HSL 色彩空间派生出各色调层级。
 */
private fun colorSchemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsl)
    val h = hsl[0]; val s = hsl[1]

    fun tone(lightness: Float, satMul: Float = 1f): Color {
        val argb = android.graphics.Color.HSVToColor(
            floatArrayOf(h, (s * satMul).coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
        )
        return Color(argb)
    }

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
            surface = tone(0.10f, 0.1f),
            onSurface = tone(0.90f, 0.1f),
            surfaceVariant = tone(0.20f, 0.2f),
            onSurfaceVariant = tone(0.80f, 0.2f),
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
            tertiary = tone(0.40f, 0.4f),
            onTertiary = Color.White,
            tertiaryContainer = tone(0.90f, 0.4f),
            onTertiaryContainer = tone(0.10f, 0.4f),
            surface = tone(0.98f, 0.05f),
            onSurface = tone(0.10f, 0.1f),
            surfaceVariant = tone(0.92f, 0.15f),
            onSurfaceVariant = tone(0.30f, 0.2f),
        )
    }
}
