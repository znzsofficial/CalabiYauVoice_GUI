package ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode

// ---------------------------------------------------------------------------
// 数据模型
// ---------------------------------------------------------------------------

/** GIF 的单帧数据 */
private data class GifFrame(
    val bitmap: ImageBitmap,
    val delayMs: Long       // 帧延迟，毫秒（最低 20ms）
)

// ---------------------------------------------------------------------------
// 全局 GIF 帧解码缓存（IO 线程异步解码，按 ByteArray 标识去重）
// ---------------------------------------------------------------------------

private val gifFrameCache = ConcurrentHashMap<ByteArray, List<GifFrame>?>()

/**
 * 在 IO 线程解码 GIF 所有帧并合成完整画面。
 * - 单帧 GIF 返回 null，由调用方回退到静态图
 * - 结果按 ByteArray 对象引用缓存（上层 ImageLoader 已保证同 URL 复用同一 ByteArray）
 */
private suspend fun decodeGifFramesAsync(bytes: ByteArray): List<GifFrame>? =
    withContext(Dispatchers.IO) {
        gifFrameCache[bytes]?.let { return@withContext it }

        val result = runCatching {
            val stream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
            val reader = ImageIO.getImageReadersByFormatName("gif").next()
                ?: return@runCatching null
            reader.input = stream

            val frameCount = reader.getNumImages(true)
            if (frameCount <= 1) return@runCatching null

            val frames = mutableListOf<GifFrame>()
            var canvas: BufferedImage? = null

            for (i in 0 until frameCount) {
                val frame = reader.read(i)
                val meta = reader.getImageMetadata(i)

                var delayCs = 10
                var offsetX = 0
                var offsetY = 0
                var disposalMethod = "none"

                val root = meta.getAsTree(meta.nativeMetadataFormatName) as? IIOMetadataNode
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

                val delayMs = maxOf(delayCs * 10L, 20L)

                if (canvas == null) {
                    canvas = BufferedImage(reader.getWidth(0), reader.getHeight(0), BufferedImage.TYPE_INT_ARGB)
                }

                val g = canvas.createGraphics()
                g.drawImage(frame, offsetX, offsetY, null)
                g.dispose()

                frames.add(GifFrame(canvas.toComposeImageBitmap(), delayMs))

                if (disposalMethod == "restoreToBackgroundColor") {
                    val g2 = canvas.createGraphics()
                    g2.clearRect(offsetX, offsetY, frame.width, frame.height)
                    g2.dispose()
                }
            }

            reader.dispose()
            stream.close()
            frames.takeIf { it.isNotEmpty() }
        }.getOrNull()

        gifFrameCache[bytes] = result
        result
    }

// ---------------------------------------------------------------------------
// 全局 GIF 动画调度器
// 所有 AnimatedGifImage 实例共用一个协程 tick，避免 N 个独立 delay 协程堆积。
// ---------------------------------------------------------------------------

private object GifAnimationClock {
    // 当前全局时间戳（毫秒），每 ~16ms 更新一次（≈60fps 上限）
    private val _tickMs = MutableStateFlow(System.currentTimeMillis())
    val tickMs: StateFlow<Long> = _tickMs.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        scope.launch {
            while (true) {
                delay(16L)
                _tickMs.value = System.currentTimeMillis()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

/**
 * 带动画的 GIF 显示组件。
 * - 在 IO 线程异步解码帧，不阻塞主线程
 * - 所有实例共用全局时钟驱动帧切换，无额外协程开销
 * - 若解码失败或为单帧 GIF，显示 [placeholder]
 */
@Composable
fun AnimatedGifImage(
    bytes: ByteArray?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: @Composable (() -> Unit)? = null
) {
    // 异步解码：loading=true 时先显示占位符
    val frames by produceState<List<GifFrame>?>(initialValue = null, bytes) {
        value = if (bytes != null) decodeGifFramesAsync(bytes) else null
    }

    if (frames == null || frames!!.isEmpty()) {
        placeholder?.invoke()
        return
    }

    val frameList = frames!!

    // 用全局 tick 驱动帧索引（不再需要每个 GIF 独立 delay）
    val tickMs by GifAnimationClock.tickMs.collectAsState()
    val frameIndex by remember(frameList) {
        // 计算累计帧时间边界，用于从全局时间戳定位当前帧
        val totalMs = frameList.sumOf { it.delayMs }
        val boundaries = buildList {
            var acc = 0L
            for (f in frameList) { acc += f.delayMs; add(acc) }
        }
        // 返回一个派生 State，根据 tickMs 计算帧索引
        derivedStateOf {
            if (totalMs == 0L) return@derivedStateOf 0
            val pos = tickMs % totalMs
            boundaries.indexOfFirst { pos < it }.takeIf { it >= 0 } ?: 0
        }
    }

    val currentBitmap = frameList[frameIndex].bitmap

    Canvas(modifier = modifier) {
        val bw = currentBitmap.width.toFloat()
        val bh = currentBitmap.height.toFloat()
        val cw = size.width
        val ch = size.height

        val scale = when (contentScale) {
            ContentScale.Crop    -> maxOf(cw / bw, ch / bh)
            ContentScale.FillBounds -> 1f
            ContentScale.FillWidth  -> cw / bw
            ContentScale.FillHeight -> ch / bh
            ContentScale.Inside  -> minOf(1f, minOf(cw / bw, ch / bh))
            else                 -> minOf(cw / bw, ch / bh) // Fit
        }

        val scaledW = if (contentScale == ContentScale.FillBounds) cw else bw * scale
        val scaledH = if (contentScale == ContentScale.FillBounds) ch else bh * scale
        val dx = (cw - scaledW) / 2f
        val dy = (ch - scaledH) / 2f

        drawImage(
            image = currentBitmap,
            dstOffset = IntOffset(dx.toInt(), dy.toInt()),
            dstSize = IntSize(scaledW.toInt(), scaledH.toInt())
        )
    }
}
