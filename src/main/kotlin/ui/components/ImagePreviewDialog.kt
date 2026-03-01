package ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import data.WikiEngine
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.Text

/**
 * 全屏图片预览弹窗，点击背景或按 ESC 关闭。
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

    DialogWindow(
        onCloseRequest = onClose,
        title = name,
        state = rememberDialogState(width = 900.dp, height = 700.dp),
        onKeyEvent = { keyEvent ->
            if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                onClose()
                true
            } else false
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xDD000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() },
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    ProgressRing(size = 48.dp)
                }
                loadFailed -> {
                    Text("图片加载失败", color = Color.Gray)
                }
                isGif -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                    ) {
                        AnimatedGifImage(
                            bytes = gifBytes,
                            contentDescription = name,
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .fillMaxHeight(0.88f)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit,
                            placeholder = {
                                // 单帧 GIF 或解码失败时，回退为静态图显示
                                if (gifBytes != null) {
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
                                            modifier = Modifier
                                                .fillMaxWidth(0.92f)
                                                .fillMaxHeight(0.88f)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Text("图片加载失败", color = Color.Gray)
                                    }
                                } else {
                                    Text("图片加载失败", color = Color.Gray)
                                }
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            name,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {}
                                )
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "点击背景或按 ESC 关闭",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
                bitmap != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = bitmap!!,
                            contentDescription = name,
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .fillMaxHeight(0.88f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {}
                                ),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            name,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {}
                                )
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "点击背景或按 ESC 关闭",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
                else -> {
                    Text("图片加载失败", color = Color.Gray)
                }
            }
        }
    }
}



