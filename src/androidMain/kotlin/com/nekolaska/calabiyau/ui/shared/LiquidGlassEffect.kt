package com.nekolaska.calabiyau.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle

// ════════════════════════════════════════════════════════
//  液态玻璃效果 & G2 连续圆角 工具集
// ════════════════════════════════════════════════════════

/** 液态玻璃是否启用 */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { mutableStateOf(false) }

/**
 * 根据 App 当前生效主题（非系统主题）判断是否处于暗色模式。
 *
 * 从 [MaterialTheme.colorScheme] 的 surface 亮度反推——AppTheme 已经根据
 * themeMode (LIGHT/DARK/SYSTEM) 解析好 colorScheme，所以这个判断跟随 app 设置，
 * 不会被 `isSystemInDarkTheme()` 那种系统级读取绕过。
 */
@Composable
@ReadOnlyComposable
private fun isAppInDarkTheme(): Boolean =
    MaterialTheme.colorScheme.surface.luminance() < 0.5f

private data class LiquidGlassTuning(
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val surfaceColor: Color
)

@Composable
private fun rememberLiquidGlassTuning(surfaceAlpha: Float, isLightVariant: Boolean): LiquidGlassTuning {
    val isDark = isAppInDarkTheme()
    return remember(isDark, surfaceAlpha, isLightVariant) {
        if (isDark) {
            LiquidGlassTuning(
                brightness = if (isLightVariant) -0.02f else -0.04f,
                contrast = if (isLightVariant) 1.04f else 1.08f,
                saturation = if (isLightVariant) 1.10f else 1.16f,
                surfaceColor = Color.Black.copy(alpha = surfaceAlpha)
            )
        } else {
            LiquidGlassTuning(
                brightness = if (isLightVariant) 0.03f else 0.05f,
                contrast = if (isLightVariant) 0.98f else 0.96f,
                saturation = if (isLightVariant) 1.06f else 1.10f,
                surfaceColor = Color.White.copy(alpha = surfaceAlpha)
            )
        }
    }
}

// ────────────────────────────────────────────
//  G2 连续圆角 Shape 工具
// ────────────────────────────────────────────

/**
 * 返回统一使用的连续圆角 Shape。
 */
@Composable
fun smoothCornerShape(radius: Dp): Shape {
    return remember(radius) { ContinuousRoundedRectangle(radius) }
}

/** 胶囊形状 */
@Composable
fun smoothCapsuleShape(): Shape {
    return remember { ContinuousCapsule }
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
    val tuning = rememberLiquidGlassTuning(surfaceAlpha = surfaceAlpha, isLightVariant = false)

    return this.drawBackdrop(
        backdrop = backdrop,
        shape = shape,
        effects = {
            colorControls(
                brightness = tuning.brightness,
                contrast = tuning.contrast,
                saturation = tuning.saturation
            )
            blur(blurRadius.toPx())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(lensHeight.toPx(), lensAmount.toPx())
            }
        },
        onDrawSurface = { drawRect(tuning.surfaceColor) }
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
    val tuning = rememberLiquidGlassTuning(
        surfaceAlpha = if (isAppInDarkTheme()) 0.22f else 0.26f,
        isLightVariant = true
    )

    return this.drawBackdrop(
        backdrop = backdrop,
        shape = shape,
        effects = {
            colorControls(
                brightness = tuning.brightness,
                contrast = tuning.contrast,
                saturation = tuning.saturation
            )
            blur(2.dp.toPx())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(12.dp.toPx(), 20.dp.toPx())
            }
        },
        onDrawSurface = { drawRect(tuning.surfaceColor) }
    )
}
