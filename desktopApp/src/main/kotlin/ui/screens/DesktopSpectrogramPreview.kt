package ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Slider
import io.github.composefluent.component.Text
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import util.DesktopWavMeta

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFluentApi::class)
@Composable
internal fun SpectrogramPreview(
    image: BufferedImage,
    meta: DesktopWavMeta?,
    zoomPercent: String,
    onZoomPercentChange: (String) -> Unit,
    cutoffHz: Int,
    previewHeight: Float,
    onPreviewHeightChange: (Float) -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val bitmap = remember(image) { image.toComposeImageBitmap() }
    var hover by remember(image) { mutableStateOf<SpectrogramHoverInfo?>(null) }
    var pendingScroll by remember(image) { mutableStateOf<Int?>(null) }
    var viewportWidthPxState by remember(image) { mutableStateOf(1f) }
    val previewHeightDp = previewHeight.dp
    val previewHeightPx = with(density) { previewHeightDp.toPx() }.coerceAtLeast(1f)
    val displayMaxHz = meta
        ?.let { if (cutoffHz > 0) min(cutoffHz, it.sampleRate / 2) else it.sampleRate / 2 }
        ?.coerceAtLeast(1)
        ?: 1

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints {
            val viewportWidthDp = maxWidth.coerceAtLeast(320.dp)
            val viewportWidthPx = with(density) { viewportWidthDp.toPx() }.coerceAtLeast(1f)
            val previewWidthPx = viewportWidthPx * (zoomPercent.toIntOrNull()?.coerceIn(50, 1500) ?: 180) / 50f
            val previewWidthDp = with(density) { previewWidthPx.toDp() }
            LaunchedEffect(viewportWidthPx) { viewportWidthPxState = viewportWidthPx }
            LaunchedEffect(previewWidthPx, pendingScroll) {
                pendingScroll?.let { target ->
                    scrollState.scrollTo(target.coerceIn(0, scrollState.maxValue))
                    pendingScroll = null
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeightDp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(androidx.compose.ui.graphics.Color.Black)
                    .horizontalScroll(scrollState)
                    .onPointerEvent(PointerEventType.Scroll) { event ->
                        val pointer = event.changes.firstOrNull()
                        val delta = pointer?.scrollDelta?.y ?: 0f
                        if (delta != 0f) {
                            val current = zoomPercent.toIntOrNull()?.coerceIn(50, 1500) ?: 180
                            val next = (current + if (delta < 0f) 25 else -25).coerceIn(50, 1500)
                            if (next != current) {
                                val pointerX = pointer?.position?.x ?: 0f
                                val currentWidth = viewportWidthPx * current / 50f
                                val nextWidth = viewportWidthPx * next / 50f
                                val anchorRatio = ((scrollState.value + pointerX) / currentWidth).coerceIn(0f, 1f)
                                pendingScroll = (anchorRatio * nextWidth - pointerX).roundToInt().coerceAtLeast(0)
                                onZoomPercentChange(next.toString())
                            }
                        }
                        event.changes.forEach { it.consume() }
                    }
                    .pointerInput(image) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            scrollState.dispatchRawDelta(-dragAmount.x)
                        }
                    }
                    .pointerInput(image) {
                        detectTapGestures(onDoubleTap = { onZoomPercentChange("100") })
                    }
                    .onPointerEvent(PointerEventType.Move) { event ->
                        val pos = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                        val imageX = ((pos.x + scrollState.value) / previewWidthPx * image.width).toInt().coerceIn(0, image.width - 1)
                        val imageY = (pos.y / previewHeightPx * image.height).toInt().coerceIn(0, image.height - 1)
                        hover = buildSpectrogramHoverInfo(image, imageX, imageY, meta, displayMaxHz)
                    }
                    .onPointerEvent(PointerEventType.Exit) { hover = null }
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.width(previewWidthDp).height(previewHeightDp)
                )
                SpectrogramGridOverlay(
                    imageWidth = previewWidthDp,
                    previewHeight = previewHeightDp,
                    meta = meta
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val maxScroll = scrollState.maxValue
            val scrollRatio = if (maxScroll > 0) (scrollState.value.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f) else 0f
            val centerRatio = meta?.durationMs?.takeIf { it > 0 }?.let {
                val contentWidthPx = viewportWidthPxState * (zoomPercent.toIntOrNull()?.coerceIn(50, 1500) ?: 180) / 50f
                ((scrollState.value + viewportWidthPxState / 2f) / contentWidthPx).coerceIn(0f, 1f)
            } ?: 0f
            Text("时间", fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
            Slider(
                value = scrollRatio,
                onValueChange = { value ->
                    if (maxScroll > 0) scope.launch { scrollState.scrollTo((value.coerceIn(0f, 1f) * maxScroll).roundToInt()) }
                },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                enabled = maxScroll > 0
            )
            Text(
                meta?.durationMs?.let { formatDuration((it * centerRatio).toLong()) } ?: "--:--",
                fontSize = 12.sp,
                color = FluentTheme.colors.text.text.secondary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("高度", fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
            Slider(
                value = previewHeight,
                onValueChange = onPreviewHeightChange,
                valueRange = 120f..720f,
                modifier = Modifier.weight(1f)
            )
            Text("${previewHeight.roundToInt()}dp", fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
        }
        Text(
            hover?.label ?: "滚轮缩放，左键拖拽平移，双击复位，可用滑块调节高度",
            fontSize = 12.sp,
            color = FluentTheme.colors.text.text.secondary
        )
    }
}

@Composable
private fun SpectrogramGridOverlay(
    imageWidth: Dp,
    previewHeight: Dp,
    meta: DesktopWavMeta?
) {
    Canvas(modifier = Modifier.width(imageWidth).height(previewHeight)) {
        val gridColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f)
        val channelDividerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f)
        val channels = meta?.channels?.coerceAtLeast(1) ?: 1
        val channelHeight = size.height / channels
        val durationMs = meta?.durationMs ?: 0L
        if (durationMs > 0) {
            val timeStepMs = chooseTimeGridStep(durationMs)
            var t = timeStepMs
            while (t < durationMs) {
                val x = size.width * t / durationMs.toFloat()
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                t += timeStepMs
            }
        }
        repeat(channels) { channel ->
            val top = channel * channelHeight
            if (channel > 0) drawLine(channelDividerColor, Offset(0f, top), Offset(size.width, top), strokeWidth = 1f)
        }
    }
}

private fun chooseTimeGridStep(durationMs: Long): Long = when {
    durationMs <= 10_000L -> 1_000L
    durationMs <= 30_000L -> 5_000L
    durationMs <= 120_000L -> 10_000L
    durationMs <= 600_000L -> 30_000L
    else -> 60_000L
}

private data class SpectrogramHoverInfo(val label: String)

private fun buildSpectrogramHoverInfo(
    image: BufferedImage,
    x: Int,
    y: Int,
    meta: DesktopWavMeta?,
    displayMaxHz: Int
): SpectrogramHoverInfo {
    val channelCount = meta?.channels?.coerceAtLeast(1) ?: 1
    val channelHeight = max(1, image.height / channelCount)
    val channel = (y / channelHeight).coerceIn(0, channelCount - 1)
    val yInChannel = (y - channel * channelHeight).coerceIn(0, channelHeight - 1)
    val frequency = displayMaxHz * (channelHeight - 1 - yInChannel).coerceAtLeast(0) / max(1, channelHeight - 1)
    val timeMs = meta?.durationMs?.let { duration -> duration * x / max(1, image.width - 1) }
    val rgb = image.getRGB(x.coerceIn(0, image.width - 1), y.coerceIn(0, image.height - 1))
    val red = (rgb ushr 16) and 0xff
    val green = (rgb ushr 8) and 0xff
    val blue = rgb and 0xff
    val intensity = ((red + green + blue) / 3.0 / 255.0 * 100.0).roundToInt()
    val label = buildString {
        if (timeMs != null) append("时间 ${formatDuration(timeMs)}  ·  ")
        append("频率 ${frequency} Hz")
        if (channelCount > 1) append("  ·  声道 ${channel + 1}")
        append("  ·  亮度 ${intensity}%")
    }
    return SpectrogramHoverInfo(label)
}
