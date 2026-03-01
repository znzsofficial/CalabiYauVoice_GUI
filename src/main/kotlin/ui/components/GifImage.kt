package ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream

/** GIF 的单帧数据 */
private data class GifFrame(
    val bitmap: ImageBitmap,
    val delayMs: Long   // 帧延迟，毫秒
)

/**
 * 解码 GIF 字节为帧列表。
 * 若只有一帧（或解码失败），返回 null（由调用方回退到普通 Image）。
 */
private fun decodeGifFrames(bytes: ByteArray): List<GifFrame>? {
    val frames = mutableListOf<GifFrame>()
    try {
        val stream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        val reader = ImageIO.getImageReadersByFormatName("gif").next() ?: return null
        reader.input = stream

        val frameCount = reader.getNumImages(true)
        if (frameCount <= 1) return null   // 静态图，不需要动画路径

        // 合成画布（GIF 帧可能比画布小，需要叠加前一帧）
        var canvas: BufferedImage? = null

        for (i in 0 until frameCount) {
            val frame = reader.read(i)
            val meta = reader.getImageMetadata(i)

            // 读取帧延迟和偏移
            var delayCs = 10   // centiseconds (default 100ms)
            var offsetX = 0
            var offsetY = 0
            var disposalMethod = "none"

            val formatName = meta.nativeMetadataFormatName
            val root = meta.getAsTree(formatName) as? IIOMetadataNode
            if (root != null) {
                val gce = root.getElementsByTagName("GraphicControlExtension")
                if (gce.length > 0) {
                    val node = gce.item(0) as IIOMetadataNode
                    delayCs = node.getAttribute("delayTime").toIntOrNull() ?: 10
                    disposalMethod = node.getAttribute("disposalMethod") ?: "none"
                }
                val iDesc = root.getElementsByTagName("ImageDescriptor")
                if (iDesc.length > 0) {
                    val node = iDesc.item(0) as IIOMetadataNode
                    offsetX = node.getAttribute("imageLeftPosition").toIntOrNull() ?: 0
                    offsetY = node.getAttribute("imageTopPosition").toIntOrNull() ?: 0
                }
            }

            val delayMs = maxOf(delayCs * 10L, 20L)  // 最低 20ms

            // 初始化画布大小
            if (canvas == null) {
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            }

            // 将当前帧绘制到合成画布
            val g = canvas.createGraphics()
            g.drawImage(frame, offsetX, offsetY, null)
            g.dispose()

            // 转换为 Compose ImageBitmap
            val bitmap = canvas.toComposeImageBitmap()
            frames.add(GifFrame(bitmap, delayMs))

            // 处置方法：restoreToBackgroundColor → 清除当前帧区域
            if (disposalMethod == "restoreToBackgroundColor") {
                val g2 = canvas.createGraphics()
                g2.clearRect(offsetX, offsetY, frame.width, frame.height)
                g2.dispose()
            }
        }
        reader.dispose()
        stream.close()
    } catch (_: Exception) {
        return null
    }
    return frames.takeIf { it.isNotEmpty() }
}

/**
 * 带动画的 GIF 显示组件。
 * - 自动解码帧并按帧延迟循环播放
 * - 若 [bytes] 为 null 或解码失败，显示 [placeholder]
 */
@Composable
fun AnimatedGifImage(
    bytes: ByteArray?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: @Composable (() -> Unit)? = null
) {
    val frames = remember(bytes) {
        if (bytes != null) decodeGifFrames(bytes) else null
    }

    if (frames == null || frames.isEmpty()) {
        placeholder?.invoke()
        return
    }

    var currentFrameIndex by remember { mutableStateOf(0) }

    LaunchedEffect(frames) {
        while (true) {
            delay(frames[currentFrameIndex].delayMs)
            currentFrameIndex = (currentFrameIndex + 1) % frames.size
        }
    }

    val currentBitmap = frames[currentFrameIndex].bitmap

    Canvas(modifier = modifier) {
        val bitmapWidth = currentBitmap.width.toFloat()
        val bitmapHeight = currentBitmap.height.toFloat()
        val canvasWidth = size.width
        val canvasHeight = size.height

        val scale = when (contentScale) {
            ContentScale.Crop -> maxOf(canvasWidth / bitmapWidth, canvasHeight / bitmapHeight)
            ContentScale.FillBounds -> 1f // handled below with explicit dst
            ContentScale.FillWidth -> canvasWidth / bitmapWidth
            ContentScale.FillHeight -> canvasHeight / bitmapHeight
            ContentScale.Inside -> minOf(1f, minOf(canvasWidth / bitmapWidth, canvasHeight / bitmapHeight))
            else -> minOf(canvasWidth / bitmapWidth, canvasHeight / bitmapHeight) // ContentScale.Fit
        }

        val scaledW = if (contentScale == ContentScale.FillBounds) canvasWidth else bitmapWidth * scale
        val scaledH = if (contentScale == ContentScale.FillBounds) canvasHeight else bitmapHeight * scale
        val dx = (canvasWidth - scaledW) / 2f
        val dy = (canvasHeight - scaledH) / 2f

        drawImage(
            image = currentBitmap,
            dstOffset = androidx.compose.ui.unit.IntOffset(dx.toInt(), dy.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(scaledW.toInt(), scaledH.toInt())
        )
    }
}

