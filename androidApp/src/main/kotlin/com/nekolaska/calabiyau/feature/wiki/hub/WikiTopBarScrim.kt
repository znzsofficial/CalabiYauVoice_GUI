package com.nekolaska.calabiyau.feature.wiki.hub

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.nekolaska.calabiyau.core.ui.LocalLiquidGlassEnabled

internal fun wikiTopBarScrimAlpha(
    progress: Float,
    liquidGlassEnabled: Boolean,
    liquidGlassMaxAlpha: Float = 0.28f,
    regularMaxAlpha: Float = 0.52f,
    startThreshold: Float = 0f
): Float {
    val visibleFraction = if (startThreshold >= 1f) {
        1f
    } else {
        ((progress - startThreshold) / (1f - startThreshold)).coerceIn(0f, 1f)
    }
    return visibleFraction * if (liquidGlassEnabled) liquidGlassMaxAlpha else regularMaxAlpha
}

private fun Modifier.wikiTopBarScrim(surfaceColor: Color, alphaProvider: () -> Float): Modifier = drawBehind {
    val alpha = alphaProvider()
    if (alpha > 0f) drawRect(surfaceColor.copy(alpha = alpha))
}

@Composable
internal fun WikiHomeTopBar(scrollBehavior: TopAppBarScrollBehavior, showDrawerButton: Boolean, onOpenDrawer: () -> Unit) {
    val hasWallpaper = LocalHasWallpaper.current
    val glass = LocalLiquidGlassEnabled.current.value
    val surface = MaterialTheme.colorScheme.surface
    LargeTopAppBar(
        title = { Text("卡拉彼丘 Wiki", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (showDrawerButton) IconButton(onClick = onOpenDrawer) { Icon(Icons.Outlined.Menu, contentDescription = "菜单") }
        },
        scrollBehavior = scrollBehavior,
        colors = if (hasWallpaper) TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent
        ) else TopAppBarDefaults.topAppBarColors(),
        modifier = if (hasWallpaper) Modifier.wikiTopBarScrim(surface) {
            // Match the 2.2.4 large title: no tint during the first 8% of collapse.
            wikiTopBarScrimAlpha(scrollBehavior.state.collapsedFraction, glass, regularMaxAlpha = 0.78f, startThreshold = 0.08f)
        } else Modifier
    )
}

@Composable
internal fun Modifier.aggregateTopBarScrim(surfaceColor: Color, alphaProvider: () -> Float): Modifier {
    val text = MaterialTheme.colorScheme.onSurface
    val floor = remember(surfaceColor, text) { wallpaperInkProtection(surfaceColor, text) }
    return wikiTopBarScrim(surfaceColor) { floor + (1f - floor) * alphaProvider() }
}
