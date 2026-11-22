package com.nekolaska.calabiyau.feature.settings

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutBoundsHolder
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import com.nekolaska.calabiyau.core.ui.ThemeMeshBackdrop
import com.nekolaska.calabiyau.core.ui.themeMeshColors
import com.nekolaska.calabiyau.core.ui.themeMeshMotionEnabled
import com.nekolaska.calabiyau.core.ui.themeMeshPoint

@Composable
internal fun AboutMeshBackdrop(
    viewport: LayoutBoundsHolder,
    modifier: Modifier = Modifier,
    fadeColor: Color = MaterialTheme.colorScheme.background
) {
    var visible by remember(viewport) { mutableStateOf(false) }
    ThemeMeshBackdrop(
        modifier = modifier.onVisibilityChanged(minFractionVisible = 0f, viewportBounds = viewport) { visible = it },
        animate = visible && LocalWindowInfo.current.isWindowFocused,
        fadeColor = fadeColor
    )
}

// Keep the About surface's existing contrast and motion regression entry points.
internal fun aboutMeshColors(scheme: ColorScheme) = themeMeshColors(scheme)
internal fun aboutMeshMotionEnabled(scale: Float, state: Lifecycle.State) = themeMeshMotionEnabled(scale, state)
internal fun aboutMeshPoint(row: Int, column: Int, a: Float, b: Float, c: Float) = themeMeshPoint(row, column, a, b, c)
