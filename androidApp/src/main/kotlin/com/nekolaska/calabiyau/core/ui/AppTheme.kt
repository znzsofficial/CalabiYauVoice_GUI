package com.nekolaska.calabiyau.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.materialkolor.Contrast
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.kyant.capsule.ContinuousRoundedRectangle
import com.nekolaska.calabiyau.core.preferences.AppPrefs

/** Global theme mode state, readable and writable from composables. */
val LocalThemeMode = staticCompositionLocalOf { mutableIntStateOf(AppPrefs.themeMode) }

/** Global seed color state. -1 follows wallpaper, 0 follows system default, otherwise custom ARGB. */
val LocalSeedColor = staticCompositionLocalOf { mutableIntStateOf(AppPrefs.customSeedColor) }

/** Theme color extracted from wallpaper. 0 means not extracted yet. */
val LocalWallpaperSeedColor = staticCompositionLocalOf { mutableIntStateOf(0) }

/** Palette style index (0–8), maps to materialkolor PaletteStyle enum ordinal. */
val LocalPaletteStyle = staticCompositionLocalOf { mutableIntStateOf(AppPrefs.paletteStyle) }

/** Contrast index: 0=Default, 1=Medium, 2=High, 3=Reduced. */
val LocalContrastLevel = staticCompositionLocalOf { mutableIntStateOf(AppPrefs.contrastLevel) }

val LocalAmoledDark = staticCompositionLocalOf { mutableStateOf(AppPrefs.amoledDark) }

val LocalColorSpec2025 = staticCompositionLocalOf { mutableStateOf(AppPrefs.colorSpec2025) }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val themeMode = remember { mutableIntStateOf(AppPrefs.themeMode) }
    val seedColor = remember { mutableIntStateOf(AppPrefs.customSeedColor) }
    val wallpaperSeedColor = remember {
        mutableIntStateOf(WallpaperSeedColor.applyCachedColor(AppPrefs.wallpaperUrl))
    }
    val liquidGlassEnabled = remember { mutableStateOf(AppPrefs.liquidGlassEnabled) }
    val highReadabilityDrawer = remember { mutableStateOf(AppPrefs.highReadabilityDrawer) }
    val paletteStyle = remember { mutableIntStateOf(AppPrefs.paletteStyle) }
    val contrastLevel = remember { mutableIntStateOf(AppPrefs.contrastLevel) }
    val amoledDark = remember { mutableStateOf(AppPrefs.amoledDark) }
    val colorSpec2025 = remember { mutableStateOf(AppPrefs.colorSpec2025) }
    val currentContent = rememberUpdatedState(content)
    val movableContent = remember { movableContentOf { currentContent.value() } }
    val context = LocalContext.current

    LaunchedEffect(seedColor.intValue, AppPrefs.wallpaperUrl) {
        if (seedColor.intValue != AppPrefs.SEED_WALLPAPER) return@LaunchedEffect
        val url = AppPrefs.wallpaperUrl
        val cached = WallpaperSeedColor.applyCachedColor(url)
        if (cached != 0) {
            wallpaperSeedColor.intValue = cached
            return@LaunchedEffect
        }
        val color = WallpaperSeedColor.resolve(context, url)
        if (color != 0) wallpaperSeedColor.intValue = color
    }

    val darkTheme = when (themeMode.intValue) {
        AppPrefs.THEME_LIGHT -> false
        AppPrefs.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val effectiveSeed = when (seedColor.intValue) {
        AppPrefs.SEED_WALLPAPER -> wallpaperSeedColor.intValue
        else -> seedColor.intValue
    }
    val systemColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    val themeSeed = if (effectiveSeed != 0) Color(effectiveSeed) else systemColorScheme.primary
    val useSystemPrimary = effectiveSeed == 0

    CompositionLocalProvider(
        LocalThemeMode provides themeMode,
        LocalSeedColor provides seedColor,
        LocalWallpaperSeedColor provides wallpaperSeedColor,
        LocalLiquidGlassEnabled provides liquidGlassEnabled,
        LocalHighReadabilityDrawer provides highReadabilityDrawer,
        LocalPaletteStyle provides paletteStyle,
        LocalContrastLevel provides contrastLevel,
        LocalAmoledDark provides amoledDark,
        LocalColorSpec2025 provides colorSpec2025
    ) {
        val appShapes = remember {
            Shapes(
                extraSmall = ContinuousRoundedRectangle(12.dp),
                small = ContinuousRoundedRectangle(12.dp),
                medium = ContinuousRoundedRectangle(16.dp),
                large = ContinuousRoundedRectangle(24.dp),
                extraLarge = ContinuousRoundedRectangle(28.dp)
            )
        }
        val appTypography = remember {
            val base = Typography()
            Typography(
                titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
                titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
                bodyLarge = base.bodyLarge.copy(fontWeight = FontWeight.Medium),
                labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium)
            )
        }
        DynamicMaterialExpressiveTheme(
            seedColor = themeSeed,
            motionScheme = MotionScheme.expressive(),
            isDark = darkTheme,
            isAmoled = darkTheme && amoledDark.value,
            primary = themeSeed.takeIf { useSystemPrimary },
            style = PaletteStyle.entries[paletteStyle.intValue],
            contrastLevel = contrastLevel.intValue.toContrastValue(),
            specVersion = if (colorSpec2025.value) {
                ColorSpec.SpecVersion.SPEC_2025
            } else {
                ColorSpec.SpecVersion.SPEC_2021
            },
            shapes = appShapes,
            typography = appTypography,
            animate = true,
            content = movableContent
        )
    }
}

private fun Int.toContrastValue(): Double = when (this) {
    AppPrefs.CONTRAST_MEDIUM -> Contrast.Medium.value
    AppPrefs.CONTRAST_HIGH -> Contrast.High.value
    AppPrefs.CONTRAST_REDUCED -> Contrast.Reduced.value
    else -> Contrast.Default.value
}
