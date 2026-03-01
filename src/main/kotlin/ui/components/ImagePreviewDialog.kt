package ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import data.WikiEngine
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.Text
import kotlin.math.roundToInt

private const val SCALE_MIN = 0.5f
private const val SCALE_MAX = 8f
private const val SCALE_STEP = 0.15f

/**
 * 全屏图片预览弹窗。
 * - 点击背景或按 ESC 关闭
 * - 滚轮缩放（以鼠标位置为中心）
 * - 拖拽平移（缩放后）
 * - 双击重置缩放与位置
 * 支持静态图（PNG/JPG/WebP）和动态 GIF。
 */
@Composable
fun ImagePreviewDialog(url: String, name: String, onClose: () -> Unit) {
    val isGif = remember(url) {
        url.substringAfterLast('.', "").lowercase().substringBefore('?') == "gif"
    }

    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var gifBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        isLoading = true
        loadFailed = false
        if (isGif) {
            val bytes = WikiEngine.loadRawBytes(url)
            gifBytes = bytes
            loadFailed = bytes == null
        } else {
            val bmp = WikiEngine.loadNetworkImage(url)
            bitmap = bmp
            loadFailed = bmp == null
        }
        isLoading = false
    }

    // 缩放与平移状态
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun resetTransform() { scale = 1f; offset = Offset.Zero }

    DialogWindow(
        onCloseRequest = onClose,
        title = name,
        state = rememberDialogState(width = 900.dp, height = 700.dp),
        onKeyEvent = { keyEvent ->
            when {
                keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown -> {
                    onClose(); true
                }
                keyEvent.key == Key.Zero && keyEvent.type == KeyEventType.KeyDown -> {
                    resetTransform(); true
                }
                else -> false
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xDD000000))
                // 背景直接用 clickable，无延迟；缩放后点背景不关闭，避免误触
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { if (scale <= 1f && offset == Offset.Zero) onClose() },
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> ProgressRing(size = 48.dp)
                loadFailed -> Text("图片加载失败", color = Color.Gray)
                else -> {
                    val imageModifier = Modifier
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.88f)
                        .clip(RoundedCornerShape(8.dp))
                        // 应用缩放与平移变换
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        // 滚轮缩放（以鼠标位置为中心）
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Scroll) {
                                        val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                        if (scrollY != 0f) {
                                            val oldScale = scale
                                            val newScale = (oldScale - scrollY * SCALE_STEP)
                                                .coerceIn(SCALE_MIN, SCALE_MAX)
                                            // 以鼠标为中心缩放：调整偏移
                                            val mousePos = event.changes.firstOrNull()?.position ?: Offset.Zero
                                            val centerX = size.width / 2f
                                            val centerY = size.height / 2f
                                            val pivotX = mousePos.x - centerX
                                            val pivotY = mousePos.y - centerY
                                            val scaleFactor = newScale / oldScale
                                            offset = Offset(
                                                x = pivotX - (pivotX - offset.x) * scaleFactor,
                                                y = pivotY - (pivotY - offset.y) * scaleFactor
                                            )
                                            scale = newScale
                                            // 缩放回 1 时自动归位
                                            if (newScale <= 1f) offset = Offset.Zero
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                        }
                        // 拖拽平移（缩放后才生效）
                        .pointerInput(Unit) {
                            detectDragGestures { _, dragAmount ->
                                if (scale > 1f) {
                                    offset = Offset(
                                        x = offset.x + dragAmount.x,
                                        y = offset.y + dragAmount.y
                                    )
                                }
                            }
                        }
                        // 双击重置（图片上双击）
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { resetTransform() })
                        }

                    if (isGif) {
                        AnimatedGifImage(
                            bytes = gifBytes,
                            contentDescription = name,
                            modifier = imageModifier,
                            contentScale = ContentScale.Fit,
                            placeholder = {
                                val staticBitmap = remember(gifBytes) {
                                    runCatching {
                                        val img = javax.imageio.ImageIO.read(gifBytes!!.inputStream())
                                        if (img != null) {
                                            val out = java.io.ByteArrayOutputStream()
                                            javax.imageio.ImageIO.write(img, "png", out)
                                            org.jetbrains.skia.Image.makeFromEncoded(out.toByteArray())
                                                .toComposeImageBitmap()
                                        } else null
                                    }.getOrNull()
                                }
                                if (staticBitmap != null) {
                                    Image(
                                        bitmap = staticBitmap,
                                        contentDescription = name,
                                        modifier = imageModifier,
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Text("图片加载失败", color = Color.Gray)
                                }
                            }
                        )
                    } else if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!,
                            contentDescription = name,
                            modifier = imageModifier,
                            contentScale = ContentScale.Fit
                        )
                    }

                    // 底部说明
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                name,
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            val hint = buildString {
                                append("滚轮缩放  拖拽平移  双击重置")
                                if (scale > 1f) append("  （${(scale * 100).roundToInt()}%）")
                                append("  |  点击背景或按 ESC 关闭")
                            }
                            Text(hint, color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
