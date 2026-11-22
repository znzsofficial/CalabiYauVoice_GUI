package com.nekolaska.calabiyau.feature.wiki.hub

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.kyant.backdrop.Backdrop
import com.nekolaska.calabiyau.core.ui.LiquidGlassTuning
import com.nekolaska.calabiyau.core.ui.LocalLiquidGlassEnabled
import com.nekolaska.calabiyau.core.ui.liquidGlass

internal val LocalHasWallpaper = staticCompositionLocalOf { false }

/** Only one layer paints the tint: the Material container, or the backdrop's onDrawSurface. */
internal data class HubPanelMaterial(val containerColor: Color, val glassAlpha: Float, val tuning: LiquidGlassTuning?)

@Composable
internal fun rememberHubPanelMaterial(
    glassAlpha: Float,
    tone: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    ink: Color = MaterialTheme.colorScheme.onSurface,
    supportingInk: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    wallpaperAlpha: Float = 0.78f,
    tintPlainGlass: Boolean = false,
): HubPanelMaterial {
    val glass = LocalLiquidGlassEnabled.current.value // Effective setting supplied by WikiHubScreen.
    val wallpaper = LocalHasWallpaper.current
    return remember(glass, wallpaper, tone, ink, supportingInk, glassAlpha, wallpaperAlpha, tintPlainGlass) {
        resolveHubPanelMaterial(glass, wallpaper, tone, ink, supportingInk, glassAlpha, wallpaperAlpha, tintPlainGlass)
    }
}

internal fun resolveHubPanelMaterial(
    glass: Boolean, wallpaper: Boolean, tone: Color, ink: Color, supportingInk: Color,
    glassAlpha: Float, wallpaperAlpha: Float, tintPlainGlass: Boolean,
): HubPanelMaterial {
    val tint = when {
        wallpaper -> hubReadableTint(tone, ink, if (glass) glassAlpha else wallpaperAlpha, supportingInk)
        glass && tintPlainGlass -> tone.copy(alpha = glassAlpha)
        else -> null
    }
    return if (glass) HubPanelMaterial(Color.Transparent, glassAlpha, tint?.let { LiquidGlassTuning(surfaceColor = it) })
    else HubPanelMaterial(tint ?: tone, glassAlpha, null)
}

@Composable
internal fun Modifier.hubPanelGlass(backdrop: Backdrop, shape: Shape, material: HubPanelMaterial): Modifier =
    liquidGlass(backdrop = backdrop, shape = { shape }, surfaceAlpha = material.glassAlpha, tuning = material.tuning)

/** Minimum surface tint behind text, including when a light theme meets a black photo or vice versa. */
internal fun wallpaperInkProtection(surface: Color, ink: Color): Float {
    val text = ink.luminance()
    val darkInk = text < surface.luminance()
    val worstPhoto = if (darkInk) Color.Black else Color.White
    var low = 0f
    var high = 1f // If even the opaque theme pairing has low contrast, retain that pairing.
    repeat(9) {
        val alpha = (low + high) / 2f
        val background = surface.copy(alpha = alpha).compositeOver(worstPhoto).luminance()
        val contrast = (maxOf(text, background) + 0.05f) / (minOf(text, background) + 0.05f)
        // Only accept contrast on the surface's side of the ink: intermediate photos may match the ink.
        if ((background >= text) == darkInk && contrast >= 4.5f) high = alpha else low = alpha
    }
    return high
}

internal fun hubReadableTint(surface: Color, ink: Color, baseAlpha: Float, supportingInk: Color = ink): Color {
    val primaryFloor = wallpaperInkProtection(surface, ink)
    val floor = if (supportingInk == ink) primaryFloor else maxOf(primaryFloor, wallpaperInkProtection(surface, supportingInk))
    return surface.copy(alpha = maxOf(baseAlpha, floor))
}
