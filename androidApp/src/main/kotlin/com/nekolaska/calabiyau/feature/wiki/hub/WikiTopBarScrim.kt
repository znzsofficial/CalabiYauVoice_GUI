package com.nekolaska.calabiyau.feature.wiki.hub

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color

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

internal fun Modifier.wikiTopBarScrim(surfaceColor: Color, alpha: Float): Modifier = drawBehind {
    if (alpha > 0f) {
        drawRect(surfaceColor.copy(alpha = alpha))
    }
}
