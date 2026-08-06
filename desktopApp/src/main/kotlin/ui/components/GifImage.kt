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
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode

private const val MAX_GIF_FRAME_COUNT = 500
private const val MAX_GIF_CANVAS_PIXELS = 16_000_000L

// ---------------------------------------------------------------------------
// 数据模型
// ---------------------------------------------------------------------------

/** GIF 的单帧数据 */
internal data class GifFrame(
    val bitmap: ImageBitmap,
    val delayMs: Long       // 帧延迟，毫秒（最低 20ms）
)

// ---------------------------------------------------------------------------
// 全局 GIF 帧解码缓存（IO 线程异步解码，按 ByteArray 标识去重）
// ---------------------------------------------------------------------------

private sealed interface CachedGif {
    class Frames(val value: List<GifFrame>) : CachedGif
    data object NoFrames : CachedGif
}

private class GifCacheKey private constructor(private val digest: ByteArray) {
    private val hashCode = digest.contentHashCode()

    override fun equals(other: Any?): Boolean =
        other is GifCacheKey && digest.contentEquals(other.digest)

    override fun hashCode(): Int = hashCode

    companion object {
        fun from(bytes: ByteArray) = GifCacheKey(MessageDigest.getInstance("SHA-256").digest(bytes))
    }
}

private object GifFrameCache {
    private const val MAX_ENTRIES = 32
    private const val MAX_ESTIMATED_BYTES = 64L * 1024L * 1024L
    private const val ENTRY_OVERHEAD_BYTES = 256L

    private data class Entry(val value: CachedGif, val estimatedBytes: Long)

    private val lock = Any()
    private val entries = object : LinkedHashMap<GifCacheKey, Entry>(16, 0.75f, true) {}
    private val inFlight = ConcurrentHashMap<GifCacheKey, CompletableDeferred<CachedGif>>()
    private var estimatedBytes = 0L

    suspend fun getOrDecode(key: GifCacheKey, decode: suspend () -> CachedGif): CachedGif {
        get(key)?.let { return it }

        val pending = CompletableDeferred<CachedGif>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) return existing.await()

        try {
            get(key)?.let {
                pending.complete(it)
                return it
            }

            val decoded = decode()
            put(key, decoded)
            pending.complete(decoded)
            return decoded
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(key, pending)
        }
    }

    private fun get(key: GifCacheKey): CachedGif? = synchronized(lock) {
        entries[key]?.value
    }

    private fun put(key: GifCacheKey, value: CachedGif) = synchronized(lock) {
        val entryBytes = estimateBytes(value)
        if (entryBytes > MAX_ESTIMATED_BYTES) return@synchronized

        entries.put(key, Entry(value, entryBytes))?.let { estimatedBytes -= it.estimatedBytes }
        estimatedBytes += entryBytes

        val iterator = entries.entries.iterator()
        while ((entries.size > MAX_ENTRIES || estimatedBytes > MAX_ESTIMATED_BYTES) && iterator.hasNext()) {
            estimatedBytes -= iterator.next().value.estimatedBytes
            iterator.remove()
        }
    }

    private fun estimateBytes(value: CachedGif): Long {
        if (value === CachedGif.NoFrames) return ENTRY_OVERHEAD_BYTES

        return (value as CachedGif.Frames).value.fold(ENTRY_OVERHEAD_BYTES) { total, frame ->
            val bitmapBytes = frame.bitmap.width.toLong() * frame.bitmap.height.toLong() * 4L
            if (Long.MAX_VALUE - total < bitmapBytes) Long.MAX_VALUE else total + bitmapBytes
        }
    }

    fun isWithinDecodeBudget(width: Int, height: Int, frameCount: Int): Boolean =
        isGifDecodeBudgetAllowed(width, height, frameCount)
}

internal fun isGifDecodeBudgetAllowed(width: Int, height: Int, frameCount: Int): Boolean {
    if (width <= 0 || height <= 0 || frameCount !in 2..MAX_GIF_FRAME_COUNT) return false
    return width.toLong() * height.toLong() <= MAX_GIF_CANVAS_PIXELS
}

/**
 * 在 IO 线程解码 GIF 所有帧并合成完整画面。
 * - 单帧或解码失败使用负缓存，由调用方回退到静态图
 * - 缓存使用内容摘要作为键，避免缓存帧时同时长期保留原始字节
 */
internal suspend fun decodeGifFramesAsync(bytes: ByteArray): List<GifFrame>? =
    withContext(Dispatchers.IO) {
        val key = GifCacheKey.from(bytes)
        val cached = GifFrameCache.getOrDecode(key) {
            try {
                val stream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
                    ?: return@getOrDecode CachedGif.NoFrames

                stream.use {
                    val readers = ImageIO.getImageReadersByFormatName("gif")
                    if (!readers.hasNext()) return@getOrDecode CachedGif.NoFrames

                    val reader = readers.next()
                    try {
                        reader.input = stream
                        val frameCount = reader.getNumImages(true)
                        if (frameCount <= 1) return@getOrDecode CachedGif.NoFrames
                        val (canvasWidth, canvasHeight) = readGifCanvasSize(reader)
                        if (!GifFrameCache.isWithinDecodeBudget(canvasWidth, canvasHeight, frameCount)) {
                            return@getOrDecode CachedGif.NoFrames
                        }

                        val frames = mutableListOf<GifFrame>()
                        var canvas: BufferedImage? = null

                        for (i in 0 until frameCount) {
                            currentCoroutineContext().ensureActive()
                            val meta = reader.getImageMetadata(i)

                            var delayCs = 10
                            var offsetX = 0
                            var offsetY = 0
                            var disposalMethod = "none"

                            val root = meta.getAsTree("javax_imageio_gif_image_1.0") as? IIOMetadataNode
                            val gce = root?.childElements()?.firstOrNull { it.nodeName == "GraphicControlExtension" }
                            if (gce != null) {
                                delayCs = gce.getAttribute("delayTime").toIntOrNull() ?: 10
                                disposalMethod = gce.getAttribute("disposalMethod").ifBlank { "none" }
                            }
                            val descriptor = root?.childElements()?.firstOrNull { it.nodeName == "ImageDescriptor" }
                            if (descriptor != null) {
                                offsetX = descriptor.getAttribute("imageLeftPosition").toIntOrNull() ?: 0
                                offsetY = descriptor.getAttribute("imageTopPosition").toIntOrNull() ?: 0
                            }

                            val frameWidth = reader.getWidth(i)
                            val frameHeight = reader.getHeight(i)
                            val framePixels = frameWidth.toLong() * frameHeight.toLong()
                            if (frameWidth <= 0 || frameHeight <= 0 || framePixels > MAX_GIF_CANVAS_PIXELS ||
                                offsetX < 0 || offsetY < 0 || offsetX.toLong() + frameWidth > canvasWidth ||
                                offsetY.toLong() + frameHeight > canvasHeight
                            ) {
                                return@getOrDecode CachedGif.NoFrames
                            }
                            val frame = reader.read(i)

                            val delayMs = maxOf(delayCs * 10L, 20L)
                            if (canvas == null) {
                                canvas = BufferedImage(
                                    canvasWidth,
                                    canvasHeight,
                                    BufferedImage.TYPE_INT_ARGB
                                )
                            }

                            val graphics = canvas.createGraphics()
                            try {
                                graphics.drawImage(frame, offsetX, offsetY, null)
                            } finally {
                                graphics.dispose()
                            }

                            frames.add(GifFrame(canvas.toComposeImageBitmap(), delayMs))

                            if (disposalMethod == "restoreToBackgroundColor") {
                                val disposalGraphics = canvas.createGraphics()
                                try {
                                    disposalGraphics.clearRect(offsetX, offsetY, frame.width, frame.height)
                                } finally {
                                    disposalGraphics.dispose()
                                }
                            }
                        }

                        if (frames.isEmpty()) CachedGif.NoFrames else CachedGif.Frames(frames)
                    } finally {
                        reader.dispose()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                CachedGif.NoFrames
            }
        }
        when (cached) {
            is CachedGif.Frames -> cached.value
            CachedGif.NoFrames -> null
        }
    }

private fun readGifCanvasSize(reader: javax.imageio.ImageReader): Pair<Int, Int> {
    val root = reader.streamMetadata
        ?.getAsTree("javax_imageio_gif_stream_1.0") as? IIOMetadataNode
    val descriptor = root?.childElements()?.firstOrNull { it.nodeName == "LogicalScreenDescriptor" }
    val width = descriptor?.getAttribute("logicalScreenWidth")?.toIntOrNull()
        ?.takeIf { it > 0 } ?: reader.getWidth(0)
    val height = descriptor?.getAttribute("logicalScreenHeight")?.toIntOrNull()
        ?.takeIf { it > 0 } ?: reader.getHeight(0)
    return width to height
}

private fun IIOMetadataNode.childElements(): List<IIOMetadataNode> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? IIOMetadataNode }

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

    // The ticker exists only while this GIF is in composition.
    val tickMs by produceState(System.currentTimeMillis(), frameList) {
        while (true) {
            delay(16L)
            value = System.currentTimeMillis()
        }
    }
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
