package com.nekolaska.calabiyau.feature.settings

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.MeshGradientPainter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

private const val MESH_ROWS = 3
private const val MESH_COLUMNS = 3

/**
 * 关于页上半屏的主题色网格。四角钉死，内部顶点用三组不同周期的偏移慢慢拧动。
 * 底部淡进页面背景，避免介绍卡片压在高饱和色上。
 */
@Composable
internal fun AboutMeshBackdrop(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val background = scheme.background
    val motionEnabled = rememberAnimatorEnabled()
    // 绘制块会反复读取这些 State。不能先收成普通 Float，否则记住的 Painter 只会停在第一帧。
    val transition = rememberInfiniteTransition(label = "aboutMesh")
    val driftA by transition.animateFloat(
        initialValue = if (motionEnabled) -0.07f else 0f,
        targetValue = if (motionEnabled) 0.09f else 0f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "driftA"
    )
    val driftB by transition.animateFloat(
        initialValue = if (motionEnabled) 0.08f else 0f,
        targetValue = if (motionEnabled) -0.06f else 0f,
        animationSpec = infiniteRepeatable(tween(3900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "driftB"
    )
    val driftC by transition.animateFloat(
        initialValue = if (motionEnabled) -0.05f else 0f,
        targetValue = if (motionEnabled) 0.11f else 0f,
        animationSpec = infiniteRepeatable(tween(4600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "driftC"
    )
    val colors = remember(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer, scheme.background) {
        aboutMeshColors(scheme)
    }
    val painter = remember(colors) {
        MeshGradientPainter(rows = MESH_ROWS, columns = MESH_COLUMNS, hasBicubicColor = true) {
            for (row in 0..MESH_ROWS) {
                for (column in 0..MESH_COLUMNS) {
                    setVertex(
                        row,
                        column,
                        aboutMeshPoint(row, column, driftA, driftB, driftC),
                        colors[row * (MESH_COLUMNS + 1) + column]
                    )
                }
            }
        }
    }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .paint(painter, sizeToIntrinsics = false, contentScale = ContentScale.FillBounds)
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.42f to Color.Transparent,
                        0.78f to background.copy(alpha = 0.72f),
                        1f to background
                    )
                )
        )
    }
}

@Composable
private fun rememberAnimatorEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

internal fun aboutMeshColors(scheme: ColorScheme): List<Color> {
    val background = scheme.background
    val palette = listOf(
        scheme.primary, scheme.tertiary, scheme.secondary, scheme.primary,
        scheme.tertiary, scheme.primary, scheme.secondary, scheme.tertiary,
        scheme.secondary, scheme.primaryContainer, scheme.tertiary, scheme.secondary,
        background, scheme.primary, scheme.tertiary, background
    )
    return palette.mapIndexed { index, color ->
        val row = index / 4
        val column = index % 4
        lerp(background, color, aboutMeshStrength(row, column))
    }
}

private fun aboutMeshStrength(row: Int, column: Int): Float = when {
    row == 3 -> 0.08f
    row == 0 && (column == 0 || column == 3) -> 0.46f
    row == 0 -> 0.62f
    row == 1 && (column == 1 || column == 2) -> 0.9f
    row == 2 && (column == 1 || column == 2) -> 0.66f
    else -> 0.52f
}

/** 内部点保持从左到右、从上到下的顺序，避免补丁折过去。 */
internal fun aboutMeshPoint(row: Int, column: Int, driftA: Float, driftB: Float, driftC: Float): Offset {
    val x = when (column) {
        0 -> 0f
        3 -> 1f
        1 -> (0.30f + driftFor(row, column, driftA, driftB, driftC).x).coerceIn(0.16f, 0.46f)
        else -> (0.70f + driftFor(row, column, driftA, driftB, driftC).x).coerceIn(0.54f, 0.86f)
    }
    val y = when (row) {
        0 -> 0f
        3 -> 1f
        1 -> (0.34f + driftFor(row, column, driftA, driftB, driftC).y).coerceIn(0.16f, 0.48f)
        else -> (0.68f + driftFor(row, column, driftA, driftB, driftC).y).coerceIn(0.54f, 0.86f)
    }
    return Offset(x, y)
}

private fun driftFor(row: Int, column: Int, driftA: Float, driftB: Float, driftC: Float): Offset {
    val phase = ((row * 3 + column) % 3)
    val x = when (phase) {
        0 -> driftA
        1 -> driftB * 0.8f
        else -> -driftC * 0.7f
    }
    val y = when (phase) {
        0 -> driftC * 0.85f
        1 -> -driftA
        else -> driftB * 0.6f
    }
    return Offset(x, y)
}

internal fun meshPointsStayOrdered(driftA: Float, driftB: Float, driftC: Float): Boolean {
    val points = List(4) { row -> List(4) { column -> aboutMeshPoint(row, column, driftA, driftB, driftC) } }
    for (row in 0..3) {
        for (column in 0..2) {
            if (points[row][column].x > points[row][column + 1].x) return false
        }
    }
    for (column in 0..3) {
        for (row in 0..2) {
            if (points[row][column].y > points[row + 1][column].y) return false
        }
    }
    return points[0][0] == Offset.Zero &&
        points[0][3] == Offset(1f, 0f) &&
        points[3][0] == Offset(0f, 1f) &&
        points[3][3] == Offset(1f, 1f) &&
        abs(driftA) < 1f
}
