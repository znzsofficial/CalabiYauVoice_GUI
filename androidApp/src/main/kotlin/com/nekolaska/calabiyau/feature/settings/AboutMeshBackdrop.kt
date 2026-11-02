package com.nekolaska.calabiyau.feature.settings

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.MeshGradientPainter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner

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
    // 离开前台或关闭系统动画时移除 transition，而不是让无限动画反复计算 0 → 0。
    val driftA: State<Float>
    val driftB: State<Float>
    val driftC: State<Float>
    if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "aboutMesh")
        driftA = transition.animateFloat(
            initialValue = -0.07f,
            targetValue = 0.09f,
            animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "driftA"
        )
        driftB = transition.animateFloat(
            initialValue = 0.08f,
            targetValue = -0.06f,
            animationSpec = infiniteRepeatable(tween(3900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "driftB"
        )
        driftC = transition.animateFloat(
            initialValue = -0.05f,
            targetValue = 0.11f,
            animationSpec = infiniteRepeatable(tween(4600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "driftC"
        )
    } else {
        val still = remember { mutableFloatStateOf(0f) }
        driftA = still
        driftB = still
        driftC = still
    }
    val colors = remember(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer, scheme.background) {
        aboutMeshColors(scheme)
    }
    val painter = remember(colors, driftA, driftB, driftC) {
        MeshGradientPainter(rows = MESH_ROWS, columns = MESH_COLUMNS, hasBicubicColor = true) {
            // 仅在绘制阶段读取动画值，避免整页每帧重组；切换静态/动态也要更新捕获的 State。
            val a = driftA.value
            val b = driftB.value
            val c = driftC.value
            for (row in 0..MESH_ROWS) {
                for (column in 0..MESH_COLUMNS) {
                    setVertex(
                        row,
                        column,
                        aboutMeshPoint(row, column, a, b, c),
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
    val resolver = LocalContext.current.contentResolver
    val owner = LocalLifecycleOwner.current
    var enabled by remember(resolver, owner) { mutableStateOf(false) }
    LifecycleResumeEffect(resolver, lifecycleOwner = owner) {
        var observing = true
        fun refresh() {
            // unregister 不会撤销已经投递到主线程的回调。
            if (!observing) return
            val scale = try {
                Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            } catch (_: SecurityException) {
                0f
            }
            enabled = aboutMeshMotionEnabled(scale, lifecycle.currentState)
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        val registered = try {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer
            )
            true
        } catch (_: SecurityException) {
            false
        }
        // 先订阅再读取；从设置/后台返回时也重新读取，不能沿用离开前的开关。
        if (registered) refresh()
        onPauseOrDispose {
            observing = false
            enabled = false
            if (registered) resolver.unregisterContentObserver(observer)
        }
    }
    return enabled
}

internal fun aboutMeshMotionEnabled(durationScale: Float, lifecycleState: Lifecycle.State): Boolean =
    durationScale.isFinite() && durationScale > 0f && lifecycleState == Lifecycle.State.RESUMED

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
