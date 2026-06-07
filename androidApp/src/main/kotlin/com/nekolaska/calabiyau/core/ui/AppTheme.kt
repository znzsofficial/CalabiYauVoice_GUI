package com.nekolaska.calabiyau.core.ui

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Global theme mode state, readable and writable from composables. */
val LocalThemeMode = staticCompositionLocalOf { mutableIntStateOf(AppPrefs.themeMode) }

/** Global seed color state. -1 follows wallpaper, 0 follows system default, otherwise custom ARGB. */
val LocalSeedColor = staticCompositionLocalOf { mutableIntStateOf(AppPrefs.customSeedColor) }

/** Theme color extracted from wallpaper. 0 means not extracted yet. */
val LocalWallpaperSeedColor = staticCompositionLocalOf { mutableIntStateOf(0) }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val themeMode = remember { mutableIntStateOf(AppPrefs.themeMode) }
    val seedColor = remember { mutableIntStateOf(AppPrefs.customSeedColor) }
    val wallpaperSeedColor = remember { mutableIntStateOf(AppPrefs.wallpaperSeedColorCache) }
    val liquidGlassEnabled = remember { mutableStateOf(AppPrefs.liquidGlassEnabled) }

    LaunchedEffect(seedColor.intValue) {
        if (seedColor.intValue != AppPrefs.SEED_WALLPAPER) return@LaunchedEffect
        if (wallpaperSeedColor.intValue != 0) return@LaunchedEffect
        val color = extractWallpaperSeedColor(AppPrefs.wallpaperUrl ?: return@LaunchedEffect)
        if (color != 0) {
            wallpaperSeedColor.intValue = color
            AppPrefs.wallpaperSeedColorCache = color
        }
    }

    val darkTheme = when (themeMode.intValue) {
        AppPrefs.THEME_LIGHT -> false
        AppPrefs.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val effectiveSeed = when (seedColor.intValue) {
        AppPrefs.SEED_WALLPAPER -> wallpaperSeedColor.intValue
        else -> seedColor.intValue
    }
    val seedScheme = if (effectiveSeed != 0) {
        rememberDynamicColorScheme(
            seedColor = Color(effectiveSeed),
            isDark = darkTheme,
            style = PaletteStyle.TonalSpot
        )
    } else {
        null
    }
    val colorScheme = seedScheme ?: when {
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
        LocalLiquidGlassEnabled provides liquidGlassEnabled
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

private suspend fun extractWallpaperSeedColor(url: String): Int = withContext(Dispatchers.IO) {
    var bitmap: android.graphics.Bitmap? = null
    try {
        val request = okhttp3.Request.Builder().url(url).build()
        val bytes = WikiEngine.client.newCall(request).execute().use { response ->
            response.body.bytes()
        }
        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext 0
        val palette = Palette.from(bitmap).generate()
        palette.getVibrantColor(palette.getMutedColor(0))
    } catch (_: Exception) {
        0
    } finally {
        bitmap?.recycle()
    }
}
