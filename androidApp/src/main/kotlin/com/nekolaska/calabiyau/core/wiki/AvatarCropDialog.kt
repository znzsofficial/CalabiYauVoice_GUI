package com.nekolaska.calabiyau.core.wiki

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.Bitmap
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 方形头像裁切弹窗：图片可拖动、双指缩放，中间固定方形取景框。
 * 确认时把取景框映射回原图坐标，裁出方形 Bitmap。
 */
@Composable
internal fun AvatarCropDialog(
    source: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    var boxSize by remember { mutableStateOf(android.util.Size(0, 0)) }
    // 按源图记忆：重新选图后弹窗复用组合位置，缩放/偏移需归零
    var scale by remember(source) { mutableFloatStateOf(1f) }
    var offset by remember(source) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    val fit: Float = if (boxSize.width == 0 || boxSize.height == 0) {
        0f
    } else {
        min(boxSize.width.toFloat() / source.width, boxSize.height.toFloat() / source.height)
    }
    val squarePx = min(boxSize.width, boxSize.height).toFloat()

    fun clampOffset() {
        if (squarePx <= 0f || fit <= 0f) return
        val displayedW = source.width * fit * scale
        val displayedH = source.height * fit * scale
        val maxX = ((displayedW - squarePx) / 2f).coerceAtLeast(0f)
        val maxY = ((displayedH - squarePx) / 2f).coerceAtLeast(0f)
        offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    fun croppedBitmap(): Bitmap? {
        // 取景框尚未测量时无法反算，直接放弃（确认按钮此刻不可达）
        if (fit <= 0f || squarePx <= 0f) return null
        val scaleTotal = fit * scale
        val imgLeft = boxSize.width / 2f + offset.x - source.width * scaleTotal / 2f
        val imgTop = boxSize.height / 2f + offset.y - source.height * scaleTotal / 2f
        val cropSizeF = (squarePx / scaleTotal).coerceAtMost(min(source.width, source.height).toFloat())
        val left = (((boxSize.width - squarePx) / 2f - imgLeft) / scaleTotal)
            .coerceIn(0f, (source.width - cropSizeF).coerceAtLeast(0f))
        val top = (((boxSize.height - squarePx) / 2f - imgTop) / scaleTotal)
            .coerceIn(0f, (source.height - cropSizeF).coerceAtLeast(0f))
        val side = min(
            cropSizeF.roundToInt(),
            min(source.width - left.roundToInt(), source.height - top.roundToInt())
        ).coerceAtLeast(1)
        return Bitmap.createBitmap(source, left.roundToInt(), top.roundToInt(), side, side)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 取景区
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { boxSize = android.util.Size(it.width, it.height) }
                    .pointerInput(source) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 8f)
                            offset += pan
                            clampOffset()
                        }
                    }
            ) {
                if (fit > 0f) {
                    Image(
                        bitmap = source.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(
                                with(density) { (source.width * fit).toDp() },
                                with(density) { (source.height * fit).toDp() }
                            )
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                }
                // 方形取景框 + 外部压暗
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val side = min(size.width, size.height)
                    val left = (size.width - side) / 2f
                    val top = (size.height - side) / 2f
                    // 四周压暗
                    drawRect(Color.Black.copy(alpha = 0.55f), size = Size(size.width, top))
                    drawRect(
                        Color.Black.copy(alpha = 0.55f),
                        topLeft = Offset(0f, top + side),
                        size = Size(size.width, size.height - top - side)
                    )
                    drawRect(
                        Color.Black.copy(alpha = 0.55f),
                        topLeft = Offset(0f, top),
                        size = Size(left, side)
                    )
                    drawRect(
                        Color.Black.copy(alpha = 0.55f),
                        topLeft = Offset(left + side, top),
                        size = Size(size.width - left - side, side)
                    )
                    // 边框
                    drawRect(
                        Color.White.copy(alpha = 0.9f),
                        topLeft = Offset(left, top),
                        size = Size(side, side),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }
            }
            Text(
                text = "拖动或双指缩放，把头像调整进方框内",
                color = Color.White.copy(alpha = 0.75f),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            // 操作行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = Color.White)
                }
                Button(
                    onClick = { croppedBitmap()?.let(onConfirm) },
                    enabled = fit > 0f && squarePx > 0f
                ) {
                    Text("裁切并使用")
                }
            }
        }
    }
}
