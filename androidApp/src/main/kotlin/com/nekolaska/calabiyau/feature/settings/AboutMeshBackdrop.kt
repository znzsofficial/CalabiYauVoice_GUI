package com.nekolaska.calabiyau.feature.settings

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import com.nekolaska.calabiyau.core.ui.ThemeMeshBackdrop
import com.nekolaska.calabiyau.core.ui.themeMeshColors
import com.nekolaska.calabiyau.core.ui.themeMeshMotionEnabled
import com.nekolaska.calabiyau.core.ui.themeMeshPoint

@Composable
internal fun AboutMeshBackdrop(
    modifier: Modifier = Modifier,
    fadeColor: Color = MaterialTheme.colorScheme.background
) = ThemeMeshBackdrop(modifier = modifier, fadeColor = fadeColor)

// Keep the About surface's existing contrast and motion regression entry points.
internal fun aboutMeshColors(scheme: ColorScheme) = themeMeshColors(scheme)
internal fun aboutMeshMotionEnabled(scale: Float, state: Lifecycle.State) = themeMeshMotionEnabled(scale, state)
internal fun aboutMeshPoint(row: Int, column: Int, a: Float, b: Float, c: Float) = themeMeshPoint(row, column, a, b, c)
