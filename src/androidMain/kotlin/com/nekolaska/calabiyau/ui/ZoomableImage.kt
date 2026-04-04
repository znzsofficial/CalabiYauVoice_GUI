package com.nekolaska.calabiyau.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

// ════════════════════════════════════════════════════════
//  ZoomableImage — 支持双指缩放、双击放大/还原、拖拽平移
// ════════════════════════════════════════════════════════

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * 可缩放的图片组件。
 *
 * - 双指捏合缩放（1x ~ 5x）
 * - 双击切换 2.5x / 1x
 * - 缩放后可拖拽平移，边界限制
 * - 单击回调 [onClick]（仅在未缩放时触发）
 * - 长按回调 [onLongPress]
 *
 * @param model Coil 图片数据源（URL / File / Uri 等）
 * @param contentDescription 无障碍描述
 * @param modifier 外部 Modifier
 * @param onClick 单击回调（缩放状态下不触发，避免误关闭）
 * @param onLongPress 长按回调
 */
@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
) {
    val context = LocalContext.current

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    /** 限制平移范围，防止图片拖出可视区域 */
    fun clampOffset(newOffset: Offset, newScale: Float): Offset {
        if (containerSize == IntSize.Zero || newScale <= 1f) return Offset.Zero
        val maxX = (containerSize.width * (newScale - 1f)) / 2f
        val maxY = (containerSize.height * (newScale - 1f)) / 2f
        return Offset(
            x = newOffset.x.coerceIn(-maxX, maxX),
            y = newOffset.y.coerceIn(-maxY, maxY)
        )
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            // 缩放 + 平移手势
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    val newOffset = if (newScale > 1f) {
                        offset + pan * scale
                    } else {
                        Offset.Zero
                    }
                    scale = newScale
                    offset = clampOffset(newOffset, newScale)
                }
            }
            // 双击 + 单击 + 长按
            .pointerInput(onClick, onLongPress) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.1f) {
                            // 已缩放 → 还原
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            // 未缩放 → 放大到 2.5x
                            scale = DOUBLE_TAP_SCALE
                            // 以双击点为中心偏移
                            val centerX = containerSize.width / 2f
                            val centerY = containerSize.height / 2f
                            val newOffset = Offset(
                                x = (centerX - it.x) * (DOUBLE_TAP_SCALE - 1f),
                                y = (centerY - it.y) * (DOUBLE_TAP_SCALE - 1f)
                            )
                            offset = clampOffset(newOffset, DOUBLE_TAP_SCALE)
                        }
                    },
                    onTap = {
                        // 仅在未缩放时触发单击（避免缩放浏览时误关闭）
                        if (scale <= 1.1f) {
                            onClick?.invoke()
                        }
                    },
                    onLongPress = if (onLongPress != null) {
                        { onLongPress() }
                    } else null
                )
            }
    )
}
