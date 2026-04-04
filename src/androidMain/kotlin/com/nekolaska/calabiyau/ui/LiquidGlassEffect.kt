package com.nekolaska.calabiyau.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle

// ════════════════════════════════════════════════════════
//  液态玻璃效果 & G2 连续圆角 工具集
// ════════════════════════════════════════════════════════

/** 液态玻璃是否启用（开启时自动开启 G2） */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { mutableStateOf(false) }

/** G2 连续圆角是否启用（可独立开启） */
val LocalG2CornersEnabled = staticCompositionLocalOf { mutableStateOf(false) }

// ────────────────────────────────────────────
//  G2 连续圆角 Shape 工具
// ────────────────────────────────────────────

/**
 * 根据 G2 开关返回对应的圆角 Shape。
 * 液态玻璃开启时也自动使用 G2 圆角。
 */
@Composable
fun smoothCornerShape(radius: Dp): Shape {
    val useG2 = LocalG2CornersEnabled.current.value || LocalLiquidGlassEnabled.current.value
    return remember(useG2, radius) {
        if (useG2) ContinuousRoundedRectangle(radius)
        else RoundedCornerShape(radius)
    }
}

/** 胶囊形状 */
@Composable
fun smoothCapsuleShape(): Shape {
    val useG2 = LocalG2CornersEnabled.current.value || LocalLiquidGlassEnabled.current.value
    return remember(useG2) {
        if (useG2) ContinuousCapsule
        else RoundedCornerShape(50)
    }
}

// ────────────────────────────────────────────
//  液态玻璃 Modifier 扩展
// ────────────────────────────────────────────

/**
 * 为组件添加液态玻璃效果。
 * backdrop 由调用方提供（通常是页面级别的 layerBackdrop）。
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop = emptyBackdrop(),
    shape: () -> Shape = { ContinuousRoundedRectangle(24.dp) },
    blurRadius: Dp = 4.dp,
    lensHeight: Dp = 16.dp,
    lensAmount: Dp = 24.dp,
    surfaceAlpha: Float = 0.35f,
    enabled: Boolean = LocalLiquidGlassEnabled.current.value
): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val isDark = isSystemInDarkTheme()
    val surfaceColor = if (isDark) Color.Black.copy(alpha = surfaceAlpha)
    else Color.White.copy(alpha = surfaceAlpha)

    return this.drawBackdrop(
        backdrop = backdrop,
        shape = shape,
        effects = {
            vibrancy()
            blur(blurRadius.toPx())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(lensHeight.toPx(), lensAmount.toPx())
            }
        },
        onDrawSurface = { drawRect(surfaceColor) }
    )
}

/**
 * 轻量级液态玻璃（用于小型组件如按钮、标签）
 */
@Composable
fun Modifier.liquidGlassLight(
    backdrop: Backdrop = emptyBackdrop(),
    shape: () -> Shape = { ContinuousCapsule },
    enabled: Boolean = LocalLiquidGlassEnabled.current.value
): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val isDark = isSystemInDarkTheme()
    val surfaceColor = if (isDark) Color.Black.copy(alpha = 0.25f)
    else Color.White.copy(alpha = 0.3f)

    return this.drawBackdrop(
        backdrop = backdrop,
        shape = shape,
        effects = {
            vibrancy()
            blur(2.dp.toPx())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(12.dp.toPx(), 20.dp.toPx())
            }
        },
        onDrawSurface = { drawRect(surfaceColor) }
    )
}
