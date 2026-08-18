package com.nekolaska.calabiyau.core.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.materialkolor.ktx.themeColors
import com.nekolaska.calabiyau.core.preferences.AppPrefs

internal object WallpaperSeedColor {
    fun cachedColorFor(url: String?): Int {
        if (url.isNullOrBlank()) return 0
        val cachedColor = AppPrefs.wallpaperSeedColorCache
        return if (url == AppPrefs.wallpaperSeedColorUrl && cachedColor != 0) cachedColor else 0
    }

    fun applyCachedColor(url: String?): Int {
        val cached = cachedColorFor(url)
        if (cached != 0) return cached
        if (!url.isNullOrBlank() && url != AppPrefs.wallpaperSeedColorUrl) {
            AppPrefs.wallpaperSeedColorCache = 0
            AppPrefs.wallpaperSeedColorUrl = url
        }
        return 0
    }

    suspend fun resolve(context: Context, url: String?): Int {
        if (url.isNullOrBlank()) return 0
        val cached = cachedColorFor(url)
        if (cached != 0) return cached

        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(Size(128, 128))
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        if (result !is SuccessResult) return 0
        val bitmap = runCatching { result.image.toBitmap() }.getOrNull() ?: return 0
        val color = bitmap.asImageBitmap()
            .themeColors(fallback = Color.Unspecified, desired = 1)
            .firstOrNull()
            ?.takeUnless { it == Color.Unspecified }
            ?.toArgb()
            ?: 0
        if (color != 0) {
            AppPrefs.wallpaperSeedColorCache = color
            AppPrefs.wallpaperSeedColorUrl = url
        }
        return color
    }
}
