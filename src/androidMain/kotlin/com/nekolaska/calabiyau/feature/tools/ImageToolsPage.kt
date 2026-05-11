package com.nekolaska.calabiyau.feature.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.media.gif.AnimatedGifEncoder
import com.nekolaska.calabiyau.core.media.gif.GifDecoder
import com.nekolaska.calabiyau.core.media.gif.StandardGifDecoder
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.core.ui.smoothCapsuleShape
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class ExportFormat(val label: String, val extension: String) {
    JPG("JPG", "jpg"),
    PNG("PNG", "png"),
    WEBP("WEBP", "webp")
}

internal data class ToolOutput(
    val title: String,
    val message: String,
    val files: List<File> = emptyList(),
    val directory: File? = null
)

internal data class PickedInput(
    val file: File? = null,
    val uri: Uri? = null
)

internal fun List<String>.toPickedFileInputs(): List<PickedInput> = map { path ->
    PickedInput(file = File(path))
}

internal fun List<Uri>.toPickedUriInputs(): List<PickedInput> = map { uri ->
    PickedInput(uri = uri)
}

private enum class ImagePreviewMode {
    COMPRESS,
    NINE_GRID,
    CROP
}

private enum class StitchDirection(val label: String) {
    HORIZONTAL("横向"),
    VERTICAL("纵向")
}

private enum class GifComposeScaleMode(val label: String) {
    STRETCH("拉伸适配"),
    FIT_LETTERBOX("等比黑边"),
    CENTER_CROP("等比填充")
}

private val CROP_PRESETS = listOf(
    "1:1" to (1 to 1),
    "1:2" to (1 to 2),
    "16:9" to (16 to 9),
    "4:3" to (4 to 3)
)
private val CropPreviewNestedScrollBlocker = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = available

    override suspend fun onPreFling(available: Velocity): Velocity = available
}

private data class ImagePreviewSheetState(
    val mode: ImagePreviewMode,
    val items: List<PickedInput>,
    val initialIndex: Int = 0,
    val cropPreset: String = "1:1",
    val cropPreviewStates: MutableMap<Int, CropPreviewState> = mutableStateMapOf()
)

private data class CropPreviewState(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
    val viewportSize: IntSize = IntSize.Zero,
    val imageSize: IntSize = IntSize.Zero
)

private data class CompressPreviewData(
    val previewBytes: ByteArray,
    val originalSizeBytes: Long?,
    val compressedSizeBytes: Int
)

private object DefaultGifBitmapProvider : GifDecoder.BitmapProvider {
    override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap =
        createBitmap(width, height, config)

    override fun release(bitmap: Bitmap) {
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    override fun obtainByteArray(size: Int): ByteArray = ByteArray(size)

    override fun release(bytes: ByteArray) = Unit

    override fun obtainIntArray(size: Int): IntArray = IntArray(size)

    override fun release(array: IntArray) = Unit
}


@Composable
private fun ImagePreviewStrip(
    items: List<PickedInput>,
    onPreviewClick: (Int) -> Unit
) {
    if (items.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth().height(88.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂未选择素材图片", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "预览 ${items.size} 张",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.take(6).forEachIndexed { index, item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    AsyncImage(
                        model = item.file ?: item.uri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(88.dp)
                            .clickable { onPreviewClick(index) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImagePreviewBottomSheet(
    state: ImagePreviewSheetState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    compressQuality: Float = 0.82f,
    onCompressQualityChange: (Float) -> Unit = {},
    compressFormat: ExportFormat = ExportFormat.JPG,
    onCompressFormatChange: (ExportFormat) -> Unit = {}
) {
    val previewHeight = 320.dp
    var currentIndex by remember(state) { mutableIntStateOf(state.initialIndex.coerceIn(0, state.items.lastIndex)) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentItem = state.items.getOrNull(currentIndex)
    val confirmLabel = when (state.mode) {
        ImagePreviewMode.COMPRESS -> "导出压缩图片"
        ImagePreviewMode.NINE_GRID -> "确认切图"
        ImagePreviewMode.CROP -> "确认裁切"
    }
    ModalBottomSheet(
        sheetState = bottomSheetState,
        onDismissRequest = onDismiss,
        shape = smoothCornerShape(28.dp),
        sheetGesturesEnabled = false,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("处理前预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                currentItem?.let { item ->
                    when (state.mode) {
                        ImagePreviewMode.COMPRESS -> {
                            Surface(
                                shape = smoothCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AsyncImage(
                                    model = item.file ?: item.uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(previewHeight)
                                )
                            }
                            Text(
                                "输出质量：${(compressQuality * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = compressQuality,
                                onValueChange = onCompressQualityChange,
                                valueRange = 0.05f..1f
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ExportFormat.entries.forEach { format ->
                                    AssistChip(
                                        onClick = { onCompressFormatChange(format) },
                                        shape = smoothCapsuleShape(),
                                        label = { Text(format.label) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (format == compressFormat) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                            }
                                        )
                                    )
                                }
                            }
                            CompressRealtimePreview(
                                item = item,
                                quality = compressQuality,
                                format = compressFormat,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                            )
                        }
                        ImagePreviewMode.NINE_GRID -> {
                            OverlayImagePreview(
                                item = item,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(previewHeight),
                                overlay = { size ->
                                    NineGridOverlay(size = size)
                                }
                            )
                        }
                        ImagePreviewMode.CROP -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                InteractiveCropPreview(
                                    item = item,
                                    preset = state.cropPreset,
                                    previewState = state.cropPreviewStates[currentIndex] ?: CropPreviewState(),
                                    onPreviewStateChange = { updated ->
                                        state.cropPreviewStates[currentIndex] = updated
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(previewHeight)
                                )
                                Text(
                                    text = "双击可快速放大/还原，拖动可微调构图。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        inputDisplayName(LocalContext.current, item) ?: "未命名图片",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.items.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { currentIndex = (currentIndex - 1).coerceAtLeast(0) },
                            enabled = currentIndex > 0,
                            shape = smoothCornerShape(20.dp)
                        ) { Text("上一张") }
                        Text("${currentIndex + 1} / ${state.items.size}")
                        OutlinedButton(
                            onClick = { currentIndex = (currentIndex + 1).coerceAtMost(state.items.lastIndex) },
                            enabled = currentIndex < state.items.lastIndex,
                            shape = smoothCornerShape(20.dp)
                        ) { Text("下一张") }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                FilledTonalButton(onClick = onConfirm, shape = smoothCornerShape(24.dp)) {
                    Text(confirmLabel)
                }
            }
        }
    }
}

@Composable
private fun CompressRealtimePreview(
    item: PickedInput,
    quality: Float,
    format: ExportFormat,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewData by produceState<CompressPreviewData?>(initialValue = null, item, quality, format) {
        value = null
        delay(120)
        value = withContext(Dispatchers.IO) {
            buildCompressPreviewData(context, item, format, quality)
        }
    }

    Surface(
        shape = smoothCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        when (val data = previewData) {
            null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "压缩后预览",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val ratioText = data.originalSizeBytes?.takeIf { it > 0 }?.let {
                        "约 ${(data.compressedSizeBytes * 100 / it.toFloat()).toInt()}%"
                    } ?: ""
                    Text(
                        text = "${format.label}  ${formatFileSize(data.compressedSizeBytes.toLong())} $ratioText",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AsyncImage(
                        model = data.previewBytes,
                        contentDescription = "压缩后预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayImagePreview(
    item: PickedInput,
    modifier: Modifier = Modifier,
    overlay: @Composable (IntSize) -> Unit
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    Surface(
        shape = smoothCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { size = it }
        ) {
            AsyncImage(
                model = item.file ?: item.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            if (size != IntSize.Zero) {
                overlay(size)
            }
        }
    }
}

@Composable
private fun NineGridOverlay(size: IntSize) {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        val stroke = 1.5.dp.toPx()
        repeat(2) { index ->
            val x = width * (index + 1) / 3f
            val y = height * (index + 1) / 3f
            drawLine(Color.White.copy(alpha = 0.85f), Offset(x, 0f), Offset(x, height), strokeWidth = stroke)
            drawLine(Color.White.copy(alpha = 0.85f), Offset(0f, y), Offset(width, y), strokeWidth = stroke)
        }
    }
}

@Composable
private fun InteractiveCropPreview(
    item: PickedInput,
    preset: String,
    previewState: CropPreviewState,
    onPreviewStateChange: (CropPreviewState) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var imageSize by remember(item) { mutableStateOf(IntSize.Zero) }
    var interactionScale by remember(item, preset) { mutableFloatStateOf(previewState.scale.coerceAtLeast(1f)) }
    var interactionOffset by remember(item, preset) { mutableStateOf(previewState.offset) }

    LaunchedEffect(item) {
        imageSize = withContext(Dispatchers.IO) { readImageSize(context, item) ?: IntSize.Zero }
    }
    var viewportSize by remember(item, preset) { mutableStateOf(IntSize.Zero) }

    Surface(
        shape = smoothCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { viewportSize = it }
        ) {
            if (viewportSize != IntSize.Zero) {
                val cropRect = remember(viewportSize, preset) { buildCropOverlayRect(viewportSize, preset) }
                val baseSize = calculateBaseDisplaySize(viewportSize, imageSize)
                val minScale = calculateMinScaleToCoverCrop(baseSize, cropRect)
                val currentScale = interactionScale.coerceIn(minScale, 5f)
                fun clampOffset(offset: Offset, scale: Float): Offset = clampCropOffset(
                    offset = offset,
                    scale = scale,
                    viewportSize = viewportSize,
                    imageSize = imageSize,
                    cropRect = cropRect
                )

                fun updatePreview(scale: Float, offset: Offset) {
                    onPreviewStateChange(
                        previewState.copy(
                            scale = scale,
                            offset = offset,
                            viewportSize = viewportSize,
                            imageSize = imageSize
                        )
                    )
                }

                val clampedOffset = clampOffset(interactionOffset, currentScale)

                LaunchedEffect(viewportSize, imageSize, cropRect, currentScale, clampedOffset, minScale) {
                    interactionScale = currentScale
                    interactionOffset = clampedOffset
                    val normalized = previewState.copy(
                        scale = currentScale,
                        offset = clampedOffset,
                        viewportSize = viewportSize,
                        imageSize = imageSize
                    )
                    if (normalized != previewState) {
                        onPreviewStateChange(normalized)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(CropPreviewNestedScrollBlocker)
                        .pointerInput(viewportSize, imageSize, cropRect, minScale) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                if (imageSize == IntSize.Zero) return@detectTransformGestures
                                val nextScale = (interactionScale * zoom).coerceIn(minScale, 5f)
                                val nextOffset = clampOffset(interactionOffset + pan, nextScale)
                                interactionScale = nextScale
                                interactionOffset = nextOffset
                                updatePreview(nextScale, nextOffset)
                            }
                        }
                        .pointerInput(viewportSize, imageSize, cropRect, minScale, interactionScale, interactionOffset) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    val nextScale = if (interactionScale <= minScale + 0.06f) {
                                        (max(2f, minScale * 2f)).coerceAtMost(5f)
                                    } else {
                                        minScale
                                    }
                                    val nextOffset = if (nextScale == minScale) {
                                        Offset.Zero
                                    } else {
                                        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                                        val scaleFactor = if (interactionScale == 0f) 1f else nextScale / interactionScale
                                        val zoomTowardTapOffset = interactionOffset + (center - tapOffset) * (scaleFactor - 1f)
                                        clampOffset(zoomTowardTapOffset, nextScale)
                                    }
                                    interactionScale = nextScale
                                    interactionOffset = nextOffset
                                    updatePreview(nextScale, nextOffset)
                                }
                            )
                        }
                ) {
                    if (baseSize != IntSize.Zero) {
                        AsyncImage(
                            model = item.file ?: item.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(
                                    with(density) { baseSize.width.toDp() },
                                    with(density) { baseSize.height.toDp() }
                                )
                                .graphicsLayer {
                                    scaleX = currentScale
                                    scaleY = currentScale
                                    translationX = clampedOffset.x
                                    translationY = clampedOffset.y
                                }
                        )
                    }
                    CropOverlay(cropRect = cropRect)
                }
            }
        }
    }
}

@Composable
private fun CropOverlay(cropRect: Rect) {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val overlayColor = Color.Black.copy(alpha = 0.45f)
        val fullPath = Path().apply { addRect(Rect(Offset.Zero, size)) }
        val cutoutPath = Path().apply { addRoundRect(RoundRect(cropRect, 22.dp.toPx(), 22.dp.toPx())) }
        val maskPath = Path.combine(PathOperation.Difference, fullPath, cutoutPath)
        drawPath(maskPath, overlayColor)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.95f),
            topLeft = cropRect.topLeft,
            size = cropRect.size,
            cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

private fun buildCropOverlayRect(viewportSize: IntSize, preset: String): Rect {
    val (ratioW, ratioH) = presetToRatio(preset)
    val maxWidth = viewportSize.width * 0.84f
    val maxHeight = viewportSize.height * 0.72f
    val targetRatio = ratioW / ratioH
    val width: Float
    val height: Float
    if (maxWidth / maxHeight > targetRatio) {
        height = maxHeight
        width = height * targetRatio
    } else {
        width = maxWidth
        height = width / targetRatio
    }
    val left = (viewportSize.width - width) / 2f
    val top = (viewportSize.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun calculateBaseDisplaySize(viewportSize: IntSize, imageSize: IntSize): IntSize {
    if (viewportSize == IntSize.Zero || imageSize == IntSize.Zero) return viewportSize
    val viewportRatio = viewportSize.width.toFloat() / viewportSize.height.toFloat()
    val imageRatio = imageSize.width.toFloat() / imageSize.height.toFloat()
    return if (imageRatio > viewportRatio) {
        IntSize(viewportSize.width, (viewportSize.width / imageRatio).toInt())
    } else {
        IntSize((viewportSize.height * imageRatio).toInt(), viewportSize.height)
    }
}

private fun clampCropOffset(
    offset: Offset,
    scale: Float,
    viewportSize: IntSize,
    imageSize: IntSize,
    cropRect: Rect
): Offset {
    if (viewportSize == IntSize.Zero || imageSize == IntSize.Zero) return offset
    val baseSize = calculateBaseDisplaySize(viewportSize, imageSize)
    val scaledWidth = baseSize.width * scale
    val scaledHeight = baseSize.height * scale
    val horizontalSlack = ((scaledWidth - cropRect.width) / 2f).coerceAtLeast(0f)
    val verticalSlack = ((scaledHeight - cropRect.height) / 2f).coerceAtLeast(0f)
    return Offset(
        x = offset.x.coerceIn(-horizontalSlack, horizontalSlack),
        y = offset.y.coerceIn(-verticalSlack, verticalSlack)
    )
}

private fun calculateMinScaleToCoverCrop(baseSize: IntSize, cropRect: Rect): Float {
    if (baseSize == IntSize.Zero) return 1f
    val byWidth = if (baseSize.width == 0) 1f else cropRect.width / baseSize.width.toFloat()
    val byHeight = if (baseSize.height == 0) 1f else cropRect.height / baseSize.height.toFloat()
    return max(1f, max(byWidth, byHeight))
}

private fun presetToRatio(preset: String): Pair<Float, Float> {
    val parts = preset.split(':')
    if (parts.size != 2) return 1f to 1f
    val w = parts[0].toFloatOrNull() ?: return 1f to 1f
    val h = parts[1].toFloatOrNull() ?: return 1f to 1f
    if (w <= 0f || h <= 0f) return 1f to 1f
    return w to h
}

@Composable
internal fun ImageToolsPage(
    outputPath: String,
    isBusy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onResult: (ToolOutput) -> Unit,
    onPickFilesFromFileManager: (
        initialPath: String?,
        title: String,
        description: String,
        allowMultiSelect: Boolean,
        onOpenSystemPicker: () -> Unit,
        onPicked: (List<String>) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val showSnack = rememberSnackbarLauncher()
    val scope = rememberCoroutineScope()
    var imageQuality by rememberSaveable { mutableFloatStateOf(0.82f) }
    var exportFormat by rememberSaveable { mutableStateOf(ExportFormat.JPG) }
    var cropPreset by rememberSaveable { mutableStateOf("1:1") }
    var cropRatioWText by rememberSaveable { mutableStateOf("1") }
    var cropRatioHText by rememberSaveable { mutableStateOf("1") }
    var stitchDirection by rememberSaveable { mutableStateOf(StitchDirection.HORIZONTAL) }
    var stitchUpscaleSmall by rememberSaveable { mutableStateOf(false) }
    var gifFps by rememberSaveable { mutableIntStateOf(10) }
    var gifLoopCount by rememberSaveable { mutableIntStateOf(0) }
    var gifQuantizeSample by rememberSaveable { mutableIntStateOf(10) }
    var gifDitherEnabled by rememberSaveable { mutableStateOf(false) }
    var gifComposeScaleMode by rememberSaveable { mutableStateOf(GifComposeScaleMode.FIT_LETTERBOX) }
    var showGifComposeSheet by remember { mutableStateOf(false) }
    var showStitchSheet by remember { mutableStateOf(false) }
    var compressSources by remember { mutableStateOf<List<PickedInput>>(emptyList()) }
    var batchConvertSources by remember { mutableStateOf<List<PickedInput>>(emptyList()) }
    var batchConvertFormat by rememberSaveable { mutableStateOf(ExportFormat.WEBP) }
    var batchConvertQuality by rememberSaveable { mutableFloatStateOf(0.9f) }
    var nineGridSources by remember { mutableStateOf<List<PickedInput>>(emptyList()) }
    var cropSources by remember { mutableStateOf<List<PickedInput>>(emptyList()) }
    var stitchSources by remember { mutableStateOf<List<PickedInput>>(emptyList()) }
    var gifComposeSources by remember { mutableStateOf<List<PickedInput>>(emptyList()) }
    var gifDecomposeSources by remember { mutableStateOf<List<PickedInput>>(emptyList()) }
    var exifCleanupSources by remember { mutableStateOf<List<PickedInput>>(emptyList()) }
    val cropPreviewStates = remember { mutableStateMapOf<Int, CropPreviewState>() }
    var previewSheetState by remember { mutableStateOf<ImagePreviewSheetState?>(null) }

    fun isPositiveRatioText(value: String): Boolean =
        value.isNotBlank() && value.any { it != '0' }

    fun updateCropPresetFromText(width: String, height: String) {
        if (!isPositiveRatioText(width) || !isPositiveRatioText(height)) return
        cropPreset = "$width:$height"
        cropPreviewStates.clear()
    }

    fun setCropRatio(width: Int, height: Int) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        cropRatioWText = safeW.toString()
        cropRatioHText = safeH.toString()
        updateCropPresetFromText(cropRatioWText, cropRatioHText)
    }

    fun updateCropRatioInput(isWidth: Boolean, input: String) {
        val filtered = input.filter(Char::isDigit)
        if (isWidth) cropRatioWText = filtered else cropRatioHText = filtered
        if (isWidth) {
            updateCropPresetFromText(filtered, cropRatioHText)
        } else {
            updateCropPresetFromText(cropRatioWText, filtered)
        }
    }

    fun showPreview(
        mode: ImagePreviewMode,
        items: List<PickedInput>,
        index: Int = 0,
        preset: String = cropPreset,
        sharedCropStates: MutableMap<Int, CropPreviewState> = cropPreviewStates
    ) {
        if (items.isNotEmpty()) {
            previewSheetState = ImagePreviewSheetState(
                mode = mode,
                items = items,
                initialIndex = index,
                cropPreset = preset,
                cropPreviewStates = sharedCropStates
            )
        }
    }

    fun updatePreviewItems(
        mode: ImagePreviewMode,
        items: List<PickedInput>,
        onUpdate: (List<PickedInput>) -> Unit,
        preset: String = cropPreset
    ) {
        onUpdate(items)
        if (items.size == 1) {
            showPreview(mode, items, preset = preset)
        }
    }

    fun runCompress(inputs: List<PickedInput>) {
        if (inputs.isEmpty()) return
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "图片工具/图片压缩")
                    val created = inputs.mapNotNull { input ->
                        val name = inputDisplayName(context, input)?.substringBeforeLast('.') ?: "image_${System.currentTimeMillis()}"
                        val target = buildUniqueFile(outputDir, sanitizeFileName(name), exportFormat.extension)
                        compressOrConvertImage(context, input, target, exportFormat, (imageQuality * 100).toInt())
                    }
                    scanMediaLibrary(context, created)
                    ToolOutput(
                        title = "图片处理完成",
                        message = "已生成 ${created.size} 个文件，输出到 ${outputDir.name}",
                        files = created,
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
                compressSources = emptyList()
            }.onFailure {
                showSnack("图片处理失败：${it.message ?: "未知错误"}")
            }
            onBusyChange(false)
        }
    }

    fun runBatchConvert(inputs: List<PickedInput>) {
        if (inputs.isEmpty()) return
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "图片工具/批量格式转换")
                    var success = 0
                    var failed = 0
                    val created = mutableListOf<File>()
                    inputs.forEach { input ->
                        val name = inputDisplayName(context, input)?.substringBeforeLast('.') ?: "image_${System.currentTimeMillis()}"
                        val target = buildUniqueFile(outputDir, sanitizeFileName(name), batchConvertFormat.extension)
                        val result = compressOrConvertImage(
                            context = context,
                            input = input,
                            target = target,
                            format = batchConvertFormat,
                            quality = (batchConvertQuality * 100).toInt()
                        )
                        if (result != null) {
                            success++
                            created += result
                        } else {
                            failed++
                        }
                    }
                    scanMediaLibrary(context, created)
                    ToolOutput(
                        title = "批量格式转换完成",
                        message = "成功 $success，失败 $failed，输出到 ${outputDir.name}",
                        files = created,
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
                batchConvertSources = emptyList()
            }.onFailure {
                showSnack("批量格式转换失败：${it.message ?: "未知错误"}")
            }
            onBusyChange(false)
        }
    }

    fun runNineGrid(inputs: List<PickedInput>) {
        if (inputs.isEmpty()) return
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "图片工具/九宫格")
                    val created = mutableListOf<File>()
                    inputs.forEach { input ->
                        created += splitToNineGrid(context, input, outputDir)
                    }
                    scanMediaLibrary(context, created)
                    ToolOutput(
                        title = "九宫格切图完成",
                        message = "共导出 ${created.size} 张切片",
                        files = created,
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
                nineGridSources = emptyList()
            }.onFailure {
                showSnack("九宫格切图失败")
            }
            onBusyChange(false)
        }
    }

    fun runCrop(inputs: List<PickedInput>, cropPreviewStateMap: Map<Int, CropPreviewState> = emptyMap()) {
        if (inputs.isEmpty()) return
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "图片工具/比例裁切")
                    val created = inputs.mapIndexedNotNull { index, input ->
                        cropImageWithPreset(context, input, outputDir, cropPreset, cropPreviewStateMap[index])
                    }
                    scanMediaLibrary(context, created)
                    ToolOutput(
                        title = "比例裁切完成",
                        message = "已输出 ${created.size} 张 $cropPreset 裁切图片",
                        files = created,
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
                cropSources = emptyList()
                cropPreviewStates.clear()
            }.onFailure {
                showSnack("比例裁切失败")
            }
            onBusyChange(false)
        }
    }

    fun runStitch(inputs: List<PickedInput>) {
        if (inputs.size < 2) {
            showSnack("请至少选择 2 张图片")
            return
        }
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "图片工具/拼图")
                    val merged = stitchImages(
                        context = context,
                        inputs = inputs,
                        outputDir = outputDir,
                        direction = stitchDirection,
                        upscaleSmall = stitchUpscaleSmall
                    ) ?: throw IllegalStateException("拼图失败")
                    scanMediaLibrary(context, listOf(merged))
                    ToolOutput(
                        title = "拼图完成",
                        message = "已导出 ${merged.name}",
                        files = listOf(merged),
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
                stitchSources = emptyList()
            }.onFailure {
                showSnack("拼图失败：${it.message ?: "未知错误"}")
            }
            onBusyChange(false)
        }
    }

    fun runGifCompose(inputs: List<PickedInput>) {
        if (inputs.isEmpty()) {
            showSnack("请先选择要合成的图片")
            return
        }
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "图片工具/GIF合成")
                    val result = composeGifFromImages(
                        context = context,
                        inputs = inputs,
                        outputDir = outputDir,
                        fps = gifFps,
                        loopCount = gifLoopCount,
                        quantizeSample = gifQuantizeSample,
                        ditherEnabled = gifDitherEnabled,
                        scaleMode = gifComposeScaleMode
                    ) ?: throw IllegalStateException("GIF 合成失败")
                    val gif = result.file
                    scanMediaLibrary(context, listOf(gif))
                    ToolOutput(
                        title = "GIF 合成完成",
                        message = buildString {
                            append("已导出 ${gif.name}（写入 ${result.writtenFrames} 帧")
                            if (result.skippedFrames > 0) append("，跳过 ${result.skippedFrames} 帧")
                            append("）")
                        },
                        files = listOf(gif),
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
                gifComposeSources = emptyList()
            }.onFailure {
                showSnack("GIF 合成失败：${it.message ?: "未知错误"}")
            }
            onBusyChange(false)
        }
    }

    fun runExifCleanup(inputs: List<PickedInput>) {
        if (inputs.isEmpty()) return
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "图片工具/元数据清理")
                    var success = 0
                    var failed = 0
                    val created = mutableListOf<File>()
                    inputs.forEach { input ->
                        val sourceName = inputDisplayName(context, input) ?: "image_${System.currentTimeMillis()}.jpg"
                        val ext = inferImageExtension(context, input, sourceName)
                        val base = sanitizeFileName(sourceName.substringBeforeLast('.'))
                        val target = buildUniqueFile(outputDir, "${base}_clean", ext)
                        val copied = copyInputToFile(context, input, target)
                        if (!copied) {
                            failed++
                        } else {
                            val cleaned = ExifCleaner.cleanPrivacyFields(target)
                            if (cleaned) {
                                success++
                                created += target
                            } else {
                                failed++
                                runCatching { target.delete() }
                            }
                        }
                    }
                    scanMediaLibrary(context, created)
                    ToolOutput(
                        title = "元数据清理完成",
                        message = "成功 $success，失败 $failed，原图未覆盖",
                        files = created,
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
                exifCleanupSources = emptyList()
            }.onFailure {
                showSnack("元数据清理失败：${it.message ?: "未知错误"}")
            }
            onBusyChange(false)
        }
    }

    fun runGifDecompose(inputs: List<PickedInput>) {
        if (inputs.isEmpty()) {
            showSnack("请先选择 GIF 文件")
            return
        }
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "图片工具/GIF分解")
                    val created = inputs.flatMap { input ->
                        decomposeGifToFrames(context, input, outputDir)
                    }
                    if (created.isEmpty()) throw IllegalStateException("未解析到可导出帧")
                    scanMediaLibrary(context, created)
                    ToolOutput(
                        title = "GIF 分解完成",
                        message = "已导出 ${created.size} 张帧图片",
                        files = created,
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
                gifDecomposeSources = emptyList()
            }.onFailure {
                showSnack("GIF 分解失败：${it.message ?: "未知错误"}")
            }
            onBusyChange(false)
        }
    }

    val pickImagesForCompress = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        updatePreviewItems(
            mode = ImagePreviewMode.COMPRESS,
            items = uris.toPickedUriInputs(),
            onUpdate = { compressSources = it }
        )
    }

    val pickImagesForBatchConvert = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        batchConvertSources = uris.toPickedUriInputs()
    }

    val pickImagesForNineGrid = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        updatePreviewItems(
            mode = ImagePreviewMode.NINE_GRID,
            items = uris.toPickedUriInputs(),
            onUpdate = { nineGridSources = it }
        )
    }

    val pickImagesForCrop = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        updatePreviewItems(
            mode = ImagePreviewMode.CROP,
            items = uris.toPickedUriInputs(),
            onUpdate = { cropSources = it },
            preset = cropPreset
        )
    }

    val pickImagesForStitch = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        stitchSources = uris.toPickedUriInputs()
    }

    val pickImagesForGifCompose = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        gifComposeSources = uris.toPickedUriInputs()
    }

    val pickGifsForDecompose = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        gifDecomposeSources = uris.toPickedUriInputs()
    }

    val pickImagesForExifCleanup = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        exifCleanupSources = uris.toPickedUriInputs()
    }

    ToolPageColumn {
        ToolCard(
            title = "批量格式转换",
            subtitle = "多选图片并批量导出为指定格式",
            icon = Icons.Outlined.Transform
        ) {
            Text("输出格式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { format ->
                    AssistChip(
                        onClick = { batchConvertFormat = format },
                        shape = smoothCapsuleShape(),
                        label = { Text(format.label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (batchConvertFormat == format) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                }
            }
            Text(
                "质量：${(batchConvertQuality * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = batchConvertQuality,
                onValueChange = { batchConvertQuality = it.coerceIn(0.4f, 1f) },
                valueRange = 0.4f..1f
            )
            FilledTonalButton(
                onClick = {
                    onPickFilesFromFileManager(
                        AppPrefs.savePath,
                        "选择要转换的图片",
                        "可在文件管理中多选图片，也可改用系统选择器。",
                        true,
                        { pickImagesForBatchConvert.launch("image/*") }
                    ) { paths ->
                        batchConvertSources = paths.toPickedFileInputs()
                    }
                },
                enabled = !isBusy,
                shape = smoothCornerShape(24.dp)
            ) { Text("选择图片") }
            if (batchConvertSources.isNotEmpty()) {
                Text("已选 ${batchConvertSources.size} 张图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ImagePreviewStrip(items = batchConvertSources) { }
            if (batchConvertSources.isNotEmpty()) {
                FilledTonalButton(
                    onClick = { runBatchConvert(batchConvertSources) },
                    enabled = !isBusy,
                    shape = smoothCornerShape(24.dp)
                ) {
                    Text("开始转换")
                }
            }
        }

        ToolCard(
            title = "压缩图片",
            subtitle = "调整格式与质量后导出图片",
            icon = Icons.Outlined.Compress
        ) {
            FilledTonalButton(
                onClick = {
                    onPickFilesFromFileManager(
                        AppPrefs.savePath,
                        "选择要压缩的图片",
                        "可在文件管理中多选图片，也可以使用系统选择器。",
                        true,
                        { pickImagesForCompress.launch("image/*") }
                    ) { paths ->
                        updatePreviewItems(
                            mode = ImagePreviewMode.COMPRESS,
                            items = paths.toPickedFileInputs(),
                            onUpdate = { compressSources = it }
                        )
                    }
                },
                enabled = !isBusy,
                shape = smoothCornerShape(24.dp)
            ) { Text("选择图片") }
            if (compressSources.isNotEmpty()) {
                Text("已选 ${compressSources.size} 张图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ImagePreviewStrip(items = compressSources) { index ->
                showPreview(ImagePreviewMode.COMPRESS, compressSources, index)
            }
            if (compressSources.isNotEmpty()) {
                FilledTonalButton(onClick = { showPreview(ImagePreviewMode.COMPRESS, compressSources) }, enabled = !isBusy, shape = smoothCornerShape(
                    24.dp
                )
                ) {
                    Text("在弹窗中导出")
                }
            }
        }

        ToolCard(
            title = "按比例裁切",
            subtitle = "按比例裁切图片并导出",
            icon = Icons.Outlined.ContentCut
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("裁切比例：$cropPreset", style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(
                    onClick = {
                        val oldWidth = cropRatioWText
                        cropRatioWText = cropRatioHText
                        cropRatioHText = oldWidth
                        updateCropPresetFromText(cropRatioWText, cropRatioHText)
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("交换横纵比", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = cropRatioWText,
                    onValueChange = { updateCropRatioInput(isWidth = true, input = it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("横向比例") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = cropRatioHText,
                    onValueChange = { updateCropRatioInput(isWidth = false, input = it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("纵向比例") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CROP_PRESETS.forEach { (label, ratio) ->
                    AssistChip(
                        onClick = {
                            setCropRatio(ratio.first, ratio.second)
                        },
                        shape = smoothCapsuleShape(),
                        label = { Text(label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (cropPreset == label) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                }
            }
            FilledTonalButton(onClick = {
                onPickFilesFromFileManager(
                    AppPrefs.savePath,
                    "选择要裁切的图片",
                    "可在文件管理中多选图片，也可以使用系统选择器。",
                    true,
                    { pickImagesForCrop.launch("image/*") }
                ) { paths ->
                    cropPreviewStates.clear()
                    updatePreviewItems(
                        mode = ImagePreviewMode.CROP,
                        items = paths.toPickedFileInputs(),
                        onUpdate = { cropSources = it },
                        preset = cropPreset
                    )
                }
            }, enabled = !isBusy, shape = smoothCornerShape(24.dp)) {
                Text("选择图片")
            }
            if (cropSources.isNotEmpty()) {
                Text("已选 ${cropSources.size} 张图片，输出比例 $cropPreset", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ImagePreviewStrip(items = cropSources) { index ->
                showPreview(
                    mode = ImagePreviewMode.CROP,
                    items = cropSources,
                    index = index,
                    preset = cropPreset,
                    sharedCropStates = cropPreviewStates
                )
            }
            if (cropSources.isNotEmpty()) {
                FilledTonalButton(onClick = { runCrop(cropSources, cropPreviewStates) }, enabled = !isBusy, shape = smoothCornerShape(
                    24.dp
                )
                ) {
                    Text("开始裁切并导出")
                }
            }
        }

        ToolCard(
            title = "图片拼图",
            subtitle = "多图横向/纵向拼接并导出",
            icon = Icons.Outlined.ViewAgenda
        ) {
            FilledTonalButton(onClick = {
                onPickFilesFromFileManager(
                    AppPrefs.savePath,
                    "选择要拼图的图片",
                    "可在文件管理中多选图片，也可以使用系统选择器。",
                    true,
                    { pickImagesForStitch.launch("image/*") }
                ) { paths ->
                    stitchSources = paths.toPickedFileInputs()
                }
            }, enabled = !isBusy, shape = smoothCornerShape(24.dp)) {
                Text("选择图片")
            }
            if (stitchSources.isNotEmpty()) {
                Text(
                    "已选 ${stitchSources.size} 张图片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ImagePreviewStrip(items = stitchSources) { }
            if (stitchSources.isNotEmpty()) {
                FilledTonalButton(
                    onClick = { showStitchSheet = true },
                    enabled = !isBusy,
                    shape = smoothCornerShape(24.dp)
                ) {
                    Text("设置并开始拼图")
                }
            }
        }

        ToolCard(
            title = "九宫格切图",
            subtitle = "按 3×3 规则切图并导出",
            icon = Icons.Outlined.ImageSearch
        ) {
            FilledTonalButton(onClick = {
                onPickFilesFromFileManager(
                    AppPrefs.savePath,
                    "选择要切图的图片",
                    "可在文件管理中多选图片，也可以使用系统选择器。",
                    true,
                    { pickImagesForNineGrid.launch("image/*") }
                ) { paths ->
                    updatePreviewItems(
                        mode = ImagePreviewMode.NINE_GRID,
                        items = paths.toPickedFileInputs(),
                        onUpdate = { nineGridSources = it }
                    )
                }
            }, enabled = !isBusy, shape = smoothCornerShape(24.dp)) {
                Text("选择图片")
            }
            if (nineGridSources.isNotEmpty()) {
                Text("已选 ${nineGridSources.size} 张图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ImagePreviewStrip(items = nineGridSources) { index ->
                showPreview(ImagePreviewMode.NINE_GRID, nineGridSources, index)
            }
            if (nineGridSources.isNotEmpty()) {
                FilledTonalButton(onClick = { runNineGrid(nineGridSources) }, enabled = !isBusy, shape = smoothCornerShape(
                    24.dp
                )
                ) {
                    Text("开始切图")
                }
            }
        }

        ToolCard(
            title = "GIF 合成",
            subtitle = "将多张图片按顺序合成为 GIF",
            icon = Icons.Outlined.Animation
        ) {
            FilledTonalButton(onClick = {
                onPickFilesFromFileManager(
                    AppPrefs.savePath,
                    "选择要合成GIF的图片",
                    "可在文件管理中多选图片，也可改用系统选择器。",
                    true,
                    { pickImagesForGifCompose.launch("image/*") }
                ) { paths ->
                    gifComposeSources = paths.toPickedFileInputs()
                }
            }, enabled = !isBusy, shape = smoothCornerShape(24.dp)) {
                Text("选择图片")
            }

            if (gifComposeSources.isNotEmpty()) {
                Text(
                    "已选 ${gifComposeSources.size} 张图片（顺序按选择结果）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ImagePreviewStrip(items = gifComposeSources) { }
            if (gifComposeSources.isNotEmpty()) {
                FilledTonalButton(
                    onClick = { showGifComposeSheet = true },
                    enabled = !isBusy,
                    shape = smoothCornerShape(24.dp)
                ) {
                    Text("设置并合成GIF")
                }
            }
        }

        ToolCard(
            title = "GIF 分解",
            subtitle = "将 GIF 拆分为逐帧图片（PNG）",
            icon = Icons.Outlined.GifBox
        ) {
            FilledTonalButton(onClick = {
                onPickFilesFromFileManager(
                    AppPrefs.savePath,
                    "选择要分解的GIF",
                    "可在文件管理中多选 GIF，也可改用系统选择器。",
                    true,
                    { pickGifsForDecompose.launch("image/gif") }
                ) { paths ->
                    gifDecomposeSources = paths.toPickedFileInputs()
                }
            }, enabled = !isBusy, shape = smoothCornerShape(24.dp)) {
                Text("选择GIF")
            }
            if (gifDecomposeSources.isNotEmpty()) {
                Text(
                    "已选 ${gifDecomposeSources.size} 个GIF",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (gifDecomposeSources.isNotEmpty()) {
                FilledTonalButton(
                    onClick = { runGifDecompose(gifDecomposeSources) },
                    enabled = !isBusy,
                    shape = smoothCornerShape(24.dp)
                ) {
                    Text("开始分解GIF")
                }
            }
        }

        ToolCard(
            title = "EXIF 隐私字段清理",
            subtitle = "清理图片隐私信息并另存副本",
            icon = Icons.Outlined.NoPhotography
        ) {
            FilledTonalButton(
                onClick = {
                    onPickFilesFromFileManager(
                        AppPrefs.savePath,
                        "选择要清理元数据的图片",
                        "可在文件管理中多选图片，也可改用系统选择器。",
                        true,
                        { pickImagesForExifCleanup.launch("image/*") }
                    ) { paths ->
                        exifCleanupSources = paths.toPickedFileInputs()
                    }
                },
                enabled = !isBusy,
                shape = smoothCornerShape(24.dp)
            ) { Text("选择图片") }
            if (exifCleanupSources.isNotEmpty()) {
                Text("已选 ${exifCleanupSources.size} 张图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ImagePreviewStrip(items = exifCleanupSources) { }
            if (exifCleanupSources.isNotEmpty()) {
                FilledTonalButton(
                    onClick = { runExifCleanup(exifCleanupSources) },
                    enabled = !isBusy,
                    shape = smoothCornerShape(24.dp)
                ) {
                    Text("开始清理")
                }
            }
        }

    }

    previewSheetState?.let { sheetState ->
        ImagePreviewBottomSheet(
            state = sheetState,
            onDismiss = { previewSheetState = null },
            onConfirm = {
                when (sheetState.mode) {
                    ImagePreviewMode.COMPRESS -> runCompress(sheetState.items)
                    ImagePreviewMode.NINE_GRID -> runNineGrid(sheetState.items)
                    ImagePreviewMode.CROP -> runCrop(sheetState.items, sheetState.cropPreviewStates)
                }
                previewSheetState = null
            },
            compressQuality = imageQuality,
            onCompressQualityChange = { imageQuality = it.coerceIn(0.05f, 1f) },
            compressFormat = exportFormat,
            onCompressFormatChange = { exportFormat = it }
        )
    }

    if (showGifComposeSheet && gifComposeSources.isNotEmpty()) {
        GifComposeBottomSheet(
            items = gifComposeSources,
            fps = gifFps,
            onFpsChange = { gifFps = it },
            loopCount = gifLoopCount,
            onLoopCountChange = { gifLoopCount = it },
            quantizeSample = gifQuantizeSample,
            onQuantizeSampleChange = { gifQuantizeSample = it },
            ditherEnabled = gifDitherEnabled,
            onDitherEnabledChange = { gifDitherEnabled = it },
            scaleMode = gifComposeScaleMode,
            onScaleModeChange = { gifComposeScaleMode = it },
            onMove = { from, to -> gifComposeSources = gifComposeSources.move(from, to) },
            onDismiss = { showGifComposeSheet = false },
            onConfirm = {
                runGifCompose(gifComposeSources)
                showGifComposeSheet = false
            },
            isBusy = isBusy
        )
    }

    if (showStitchSheet && stitchSources.isNotEmpty()) {
        StitchBottomSheet(
            items = stitchSources,
            direction = stitchDirection,
            onDirectionChange = { stitchDirection = it },
            upscaleSmall = stitchUpscaleSmall,
            onUpscaleSmallChange = { stitchUpscaleSmall = it },
            onMove = { from, to -> stitchSources = stitchSources.move(from, to) },
            onDismiss = { showStitchSheet = false },
            onConfirm = {
                runStitch(stitchSources)
                showStitchSheet = false
            },
            isBusy = isBusy
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StitchBottomSheet(
    items: List<PickedInput>,
    direction: StitchDirection,
    onDirectionChange: (StitchDirection) -> Unit,
    upscaleSmall: Boolean,
    onUpscaleSmallChange: (Boolean) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isBusy: Boolean
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        sheetGesturesEnabled = false,
        shape = smoothCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("拼图设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StitchDirection.entries.forEach { mode ->
                    AssistChip(
                        onClick = { onDirectionChange(mode) },
                        shape = smoothCapsuleShape(),
                        label = { Text(mode.label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (direction == mode) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("放大小图到统一${if (direction == StitchDirection.HORIZONTAL) "高度" else "宽度"}")
                Switch(checked = upscaleSmall, onCheckedChange = onUpscaleSmallChange)
            }

            GifComposeOrderEditor(
                items = items,
                onMove = onMove,
                tip = "长按拖拽可调整拼图顺序",
                indexLabel = "张"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                FilledTonalButton(onClick = onConfirm, enabled = !isBusy, shape = smoothCornerShape(24.dp)) {
                    Text("开始拼图")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GifComposeBottomSheet(
    items: List<PickedInput>,
    fps: Int,
    onFpsChange: (Int) -> Unit,
    loopCount: Int,
    onLoopCountChange: (Int) -> Unit,
    quantizeSample: Int,
    onQuantizeSampleChange: (Int) -> Unit,
    ditherEnabled: Boolean,
    onDitherEnabledChange: (Boolean) -> Unit,
    scaleMode: GifComposeScaleMode,
    onScaleModeChange: (GifComposeScaleMode) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isBusy: Boolean
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        sheetGesturesEnabled = false,
        shape = smoothCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("GIF 合成设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Text("帧率：${fps} fps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = fps.toFloat(),
                onValueChange = { onFpsChange(it.roundToInt().coerceIn(1, 60)) },
                valueRange = 1f..60f,
                steps = 58
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("循环次数（0 = 无限）：$loopCount")
            }
            Slider(
                value = loopCount.toFloat(),
                onValueChange = { onLoopCountChange(it.roundToInt().coerceIn(0, 20)) },
                valueRange = 0f..20f,
                steps = 19
            )

            Text("量化采样：$quantizeSample", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = quantizeSample.toFloat(),
                onValueChange = { onQuantizeSampleChange(it.roundToInt().coerceIn(1, 30)) },
                valueRange = 1f..30f,
                steps = 28
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("抖动预处理")
                Switch(checked = ditherEnabled, onCheckedChange = onDitherEnabledChange)
            }

            Text("尺寸策略", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GifComposeScaleMode.entries.forEach { mode ->
                    AssistChip(
                        onClick = { onScaleModeChange(mode) },
                        shape = smoothCapsuleShape(),
                        label = { Text(mode.label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (scaleMode == mode) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                }
            }

            GifComposeOrderEditor(items = items, onMove = onMove)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                FilledTonalButton(onClick = onConfirm, enabled = !isBusy, shape = smoothCornerShape(24.dp)) {
                    Text("开始合成")
                }
            }
        }
    }
}


private fun openBitmap(context: Context, input: PickedInput): Bitmap? {
    input.file?.let { return BitmapFactory.decodeFile(it.absolutePath) }
    return input.uri?.let { uri ->
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }
}

private fun inputDisplayName(context: Context, input: PickedInput): String? {
    return input.file?.name ?: input.uri?.let(context::queryDisplayName)
}

private fun readBytesFromInput(context: Context, input: PickedInput): ByteArray? {
    return runCatching {
        input.file?.takeIf { it.isFile }?.readBytes()
            ?: input.uri?.let { uri ->
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
    }.getOrNull()
}

private fun copyInputToFile(context: Context, input: PickedInput, target: File): Boolean {
    target.parentFile?.mkdirs()
    return runCatching {
        when {
            input.file?.isFile == true -> input.file.copyTo(target, overwrite = true)
            input.uri != null -> {
                context.contentResolver.openInputStream(input.uri)?.use { inputStream ->
                    FileOutputStream(target).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: return false
            }
            else -> return false
        }
        true
    }.getOrElse { false }
}

private fun compressOrConvertImage(
    context: Context,
    input: PickedInput,
    target: File,
    format: ExportFormat,
    quality: Int
): File? {
    val bitmap = openBitmap(context, input) ?: return null
    target.parentFile?.mkdirs()
    FileOutputStream(target).use { out ->
        bitmap.compress(resolveCompressFormat(format), quality.coerceIn(1, 100), out)
    }
    bitmap.recycle()
    return target
}

private fun resolveCompressFormat(format: ExportFormat): Bitmap.CompressFormat {
    return when (format) {
        ExportFormat.JPG -> Bitmap.CompressFormat.JPEG
        ExportFormat.PNG -> Bitmap.CompressFormat.PNG
        ExportFormat.WEBP -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        }
    }
}

private fun buildCompressPreviewData(
    context: Context,
    input: PickedInput,
    format: ExportFormat,
    quality: Float
): CompressPreviewData? {
    val bitmap = openBitmap(context, input) ?: return null
    val output = ByteArrayOutputStream()
    bitmap.compress(resolveCompressFormat(format), (quality * 100).toInt().coerceIn(1, 100), output)
    val previewBytes = output.toByteArray()
    output.close()
    val originalSize = when {
        input.file != null -> input.file.length().takeIf { it > 0 }
        input.uri != null -> runCatching {
            context.contentResolver.openAssetFileDescriptor(input.uri, "r")?.use { fd ->
                fd.length.takeIf { it > 0 }
            }
        }.getOrNull()
        else -> null
    }
    bitmap.recycle()
    return CompressPreviewData(
        previewBytes = previewBytes,
        originalSizeBytes = originalSize,
        compressedSizeBytes = previewBytes.size
    )
}

private fun splitToNineGrid(context: Context, input: PickedInput, outputDir: File): List<File> {
    val bitmap = openBitmap(context, input) ?: return emptyList()
    val baseName = sanitizeFileName(inputDisplayName(context, input)?.substringBeforeLast('.') ?: "grid")
    val cellWidth = max(1, bitmap.width / 3)
    val cellHeight = max(1, bitmap.height / 3)
    val outputs = mutableListOf<File>()
    repeat(3) { row ->
        repeat(3) { col ->
            val x = min(bitmap.width - cellWidth, col * cellWidth)
            val y = min(bitmap.height - cellHeight, row * cellHeight)
            val piece = Bitmap.createBitmap(bitmap, x, y, min(cellWidth, bitmap.width - x), min(cellHeight, bitmap.height - y))
            val target = File(outputDir, "${baseName}_${row + 1}_${col + 1}.jpg")
            FileOutputStream(target).use { piece.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            piece.recycle()
            outputs += target
        }
    }
    bitmap.recycle()
    return outputs
}

private fun stitchImages(
    context: Context,
    inputs: List<PickedInput>,
    outputDir: File,
    direction: StitchDirection,
    upscaleSmall: Boolean
): File? {
    val bitmaps = inputs.mapNotNull { openBitmap(context, it) }
    if (bitmaps.size < 2) {
        bitmaps.forEach { it.recycle() }
        return null
    }

    val targetCross = when (direction) {
        StitchDirection.HORIZONTAL -> {
            val values = bitmaps.map { it.height }
            if (upscaleSmall) values.maxOrNull() ?: 0 else values.minOrNull() ?: 0
        }
        StitchDirection.VERTICAL -> {
            val values = bitmaps.map { it.width }
            if (upscaleSmall) values.maxOrNull() ?: 0 else values.minOrNull() ?: 0
        }
    }.coerceAtLeast(1)

    val scaledBitmaps = bitmaps.map { bitmap ->
        when (direction) {
            StitchDirection.HORIZONTAL -> {
                val scale = targetCross.toFloat() / bitmap.height.toFloat()
                val targetW = max(1, (bitmap.width * scale).roundToInt())
                val targetH = targetCross
                if (bitmap.width == targetW && bitmap.height == targetH) bitmap
                else bitmap.scale(targetW, targetH)
            }
            StitchDirection.VERTICAL -> {
                val scale = targetCross.toFloat() / bitmap.width.toFloat()
                val targetW = targetCross
                val targetH = max(1, (bitmap.height * scale).roundToInt())
                if (bitmap.width == targetW && bitmap.height == targetH) bitmap
                else bitmap.scale(targetW, targetH)
            }
        }
    }

    val (outputWidth, outputHeight) = when (direction) {
        StitchDirection.HORIZONTAL -> scaledBitmaps.sumOf { it.width } to targetCross
        StitchDirection.VERTICAL -> targetCross to scaledBitmaps.sumOf { it.height }
    }

    val merged = createBitmap(outputWidth, outputHeight)
    val canvas = Canvas(merged)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    var cursor = 0f
    scaledBitmaps.forEach { bmp ->
        when (direction) {
            StitchDirection.HORIZONTAL -> {
                canvas.drawBitmap(bmp, cursor, 0f, paint)
                cursor += bmp.width
            }
            StitchDirection.VERTICAL -> {
                canvas.drawBitmap(bmp, 0f, cursor, paint)
                cursor += bmp.height
            }
        }
    }

    outputDir.mkdirs()
    val target = File(outputDir, "拼图_${direction.label}_${System.currentTimeMillis()}.jpg")
    FileOutputStream(target).use { merged.compress(Bitmap.CompressFormat.JPEG, 94, it) }

    merged.recycle()
    scaledBitmaps.forEachIndexed { index, scaled ->
        if (scaled !== bitmaps[index]) scaled.recycle()
    }
    bitmaps.forEach { it.recycle() }
    return target
}

private data class GifComposeResult(
    val file: File,
    val writtenFrames: Int,
    val skippedFrames: Int
)

private fun composeGifFromImages(
    context: Context,
    inputs: List<PickedInput>,
    outputDir: File,
    fps: Int,
    loopCount: Int,
    quantizeSample: Int,
    ditherEnabled: Boolean,
    scaleMode: GifComposeScaleMode
): GifComposeResult? {
    if (inputs.isEmpty()) return null
    outputDir.mkdirs()
    val target = buildUniqueFile(outputDir, "gif_compose_${System.currentTimeMillis()}", "gif")
    val encoder = AnimatedGifEncoder()
    return runCatching {
        var writtenFrames = 0
        var skippedFrames = 0
        val firstBitmap = openBitmap(context, inputs.first()) ?: throw IllegalStateException("首帧读取失败")
        val baseWidth = firstBitmap.width.coerceAtLeast(1)
        val baseHeight = firstBitmap.height.coerceAtLeast(1)
        encoder.setSize(baseWidth, baseHeight)
        val started = encoder.start(target.absolutePath)
        if (!started) throw IllegalStateException("GIF 编码器启动失败")
        encoder.setFrameRate(fps.coerceIn(1, 60).toFloat())
        encoder.setRepeat(loopCount.coerceAtLeast(0))
        encoder.setQuality(quantizeSample.coerceIn(1, 30))

        fun writeFrame(bitmap: Bitmap) {
            val normalized = normalizeGifFrame(bitmap, baseWidth, baseHeight, scaleMode)
            val prepared = if (ditherEnabled) applyOrderedDither(normalized) else normalized
            val ok = encoder.addFrame(prepared)
            if (prepared !== normalized && !prepared.isRecycled) prepared.recycle()
            if (normalized !== bitmap && !normalized.isRecycled) normalized.recycle()
            if (!ok) throw IllegalStateException("写入帧失败")
            writtenFrames++
        }

        try {
            writeFrame(firstBitmap)
        } finally {
            if (!firstBitmap.isRecycled) firstBitmap.recycle()
        }

        inputs.drop(1).forEach { input ->
            val bitmap = openBitmap(context, input)
            if (bitmap == null) {
                skippedFrames++
                return@forEach
            }
            try {
                writeFrame(bitmap)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }

        if (writtenFrames <= 0) throw IllegalStateException("未写入任何帧")
        if (!encoder.finish()) throw IllegalStateException("GIF 结束写入失败")
        GifComposeResult(target, writtenFrames, skippedFrames)
    }.getOrNull()
}

private fun inferImageExtension(context: Context, input: PickedInput, sourceName: String): String {
    val fileExt = input.file?.extension?.lowercase()?.takeIf { it.isNotBlank() }
    if (fileExt != null) return fileExt

    val mimeExt = input.uri?.let { uri ->
        context.contentResolver.getType(uri)
            ?.substringAfter('/')
            ?.substringBefore(';')
            ?.lowercase()
            ?.let { raw ->
                when (raw) {
                    "jpeg" -> "jpg"
                    "heic", "heif", "png", "webp", "bmp", "gif", "jpg" -> raw
                    else -> MimeTypeMap.getSingleton().getExtensionFromMimeType("image/$raw")?.lowercase()
                }
            }
    }
    if (!mimeExt.isNullOrBlank()) return mimeExt

    val nameExt = sourceName.substringAfterLast('.', "").lowercase().takeIf { it.isNotBlank() }
    return nameExt ?: "jpg"
}

private fun applyOrderedDither(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height
    val out = createBitmap(width, height)
    val matrix = arrayOf(
        intArrayOf(0, 8, 2, 10),
        intArrayOf(12, 4, 14, 6),
        intArrayOf(3, 11, 1, 9),
        intArrayOf(15, 7, 13, 5)
    )
    val srcPixels = IntArray(width * height)
    val outPixels = IntArray(width * height)
    source.getPixels(srcPixels, 0, width, 0, 0, width, height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = y * width + x
            val color = srcPixels[index]
            val r = android.graphics.Color.red(color)
            val g = android.graphics.Color.green(color)
            val b = android.graphics.Color.blue(color)
            val a = android.graphics.Color.alpha(color)
            val threshold = matrix[y and 3][x and 3] - 8
            val nr = (r + threshold * 2).coerceIn(0, 255)
            val ng = (g + threshold * 2).coerceIn(0, 255)
            val nb = (b + threshold * 2).coerceIn(0, 255)
            outPixels[index] = android.graphics.Color.argb(a, nr, ng, nb)
        }
    }
    out.setPixels(outPixels, 0, width, 0, 0, width, height)
    return out
}

private fun normalizeGifFrame(
    source: Bitmap,
    targetWidth: Int,
    targetHeight: Int,
    mode: GifComposeScaleMode
): Bitmap {
    if (source.width == targetWidth && source.height == targetHeight) return source
    return when (mode) {
        GifComposeScaleMode.STRETCH -> source.scale(targetWidth, targetHeight)
        GifComposeScaleMode.FIT_LETTERBOX -> {
            val scale = min(
                targetWidth.toFloat() / source.width.toFloat(),
                targetHeight.toFloat() / source.height.toFloat()
            )
            val drawW = max(1, (source.width * scale).roundToInt())
            val drawH = max(1, (source.height * scale).roundToInt())
            val scaled = source.scale(drawW, drawH)
            val out = createBitmap(targetWidth, targetHeight)
            val canvas = Canvas(out)
            canvas.drawColor(android.graphics.Color.BLACK)
            val left = ((targetWidth - drawW) / 2f)
            val top = ((targetHeight - drawH) / 2f)
            canvas.drawBitmap(scaled, left, top, Paint(Paint.FILTER_BITMAP_FLAG))
            if (scaled !== source && !scaled.isRecycled) scaled.recycle()
            out
        }
        GifComposeScaleMode.CENTER_CROP -> {
            val scale = max(
                targetWidth.toFloat() / source.width.toFloat(),
                targetHeight.toFloat() / source.height.toFloat()
            )
            val drawW = max(1, (source.width * scale).roundToInt())
            val drawH = max(1, (source.height * scale).roundToInt())
            val scaled = source.scale(drawW, drawH)
            val x = ((drawW - targetWidth) / 2).coerceAtLeast(0)
            val y = ((drawH - targetHeight) / 2).coerceAtLeast(0)
            val out = Bitmap.createBitmap(scaled, x, y, targetWidth.coerceAtMost(scaled.width), targetHeight.coerceAtMost(scaled.height))
            if (scaled !== source && !scaled.isRecycled) scaled.recycle()
            out
        }
    }
}

private fun <T> List<T>.move(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex == toIndex || fromIndex !in indices || toIndex !in indices) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(toIndex, item)
    return mutable
}

@Composable
private fun GifComposeOrderEditor(
    items: List<PickedInput>,
    onMove: (from: Int, to: Int) -> Unit,
    tip: String = "长按拖拽可调整合成顺序",
    indexLabel: String = "帧"
) {
    if (items.size < 2) return

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val autoScrollEdgePx = with(density) { 84.dp.toPx() }
    val swapThresholdPx = with(density) { 10.dp.toPx() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOffsetY by remember { mutableFloatStateOf(0f) }
    var lastSwapAt by remember { mutableLongStateOf(0L) }

    Text(
        tip,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Surface(
        shape = smoothCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.nestedScroll(CropPreviewNestedScrollBlocker)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp),
            state = listState,
            userScrollEnabled = draggingIndex == null,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            itemsIndexed(
                items,
                key = { _, item -> item.file?.absolutePath ?: item.uri?.toString() ?: item.hashCode() }
            ) { index, item ->
                val isDragging = draggingIndex == index
                Surface(
                    shape = smoothCornerShape(12.dp),
                    color = if (isDragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    tonalElevation = if (isDragging) 4.dp else 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = if (isDragging) draggingOffsetY else 0f }
                        .pointerInput(items, index) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIndex = index
                                    draggingOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    draggingOffsetY = 0f
                                },
                                onDragEnd = {
                                    draggingIndex = null
                                    draggingOffsetY = 0f
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                draggingOffsetY += dragAmount.y

                                val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                                if (viewportHeight > 0) {
                                    val y = change.position.y
                                    val autoScrollDelta = when {
                                        y < autoScrollEdgePx -> ((y - autoScrollEdgePx) / autoScrollEdgePx) * 14f
                                        y > (viewportHeight - autoScrollEdgePx) -> ((y - (viewportHeight - autoScrollEdgePx)) / autoScrollEdgePx) * 14f
                                        else -> 0f
                                    }
                                    if (autoScrollDelta != 0f) {
                                        scope.launch {
                                            listState.scrollBy(autoScrollDelta)
                                        }
                                        draggingOffsetY += autoScrollDelta
                                    }
                                }

                                val currentInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == from }
                                    ?: return@detectDragGesturesAfterLongPress
                                val currentCenter = currentInfo.offset + (currentInfo.size / 2f) + draggingOffsetY
                                val targetInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                    if (info.index == from) return@firstOrNull false
                                    val targetCenter = info.offset + (info.size / 2f)
                                    if (info.index > from) {
                                        currentCenter > (targetCenter + swapThresholdPx)
                                    } else {
                                        currentCenter < (targetCenter - swapThresholdPx)
                                    }
                                }
                                if (targetInfo != null) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastSwapAt < 42L) return@detectDragGesturesAfterLongPress
                                    lastSwapAt = now
                                    onMove(from, targetInfo.index)
                                    draggingIndex = targetInfo.index
                                    draggingOffsetY += (currentInfo.offset - targetInfo.offset)
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AsyncImage(
                            model = item.file ?: item.uri,
                            contentDescription = null,
                            modifier = Modifier.size(46.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = inputDisplayName(LocalContext.current, item) ?: "未命名图片",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "第 ${index + 1} $indexLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.DragIndicator,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun decomposeGifToFrames(context: Context, input: PickedInput, outputDir: File): List<File> {
    val bytes = readBytesFromInput(context, input) ?: return emptyList()
    val decoder = StandardGifDecoder(DefaultGifBitmapProvider)
    return runCatching {
        val status = decoder.read(bytes)
        if (status != GifDecoder.STATUS_OK && status != GifDecoder.STATUS_PARTIAL_DECODE) {
            return@runCatching emptyList()
        }
        val frameCount = decoder.frameCount
        if (frameCount <= 0) return@runCatching emptyList()

        val baseName = sanitizeFileName(
            inputDisplayName(context, input)?.substringBeforeLast('.') ?: "gif"
        )
        val frameDir = File(outputDir, "${baseName}_frames_${System.currentTimeMillis()}").apply { mkdirs() }
        buildList {
            for (index in 0 until frameCount) {
                decoder.advance()
                val frame = decoder.nextFrame ?: continue
                val file = File(frameDir, "${baseName}_${(index + 1).toString().padStart(3, '0')}.png")
                FileOutputStream(file).use { out ->
                    frame.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                add(file)
            }
        }
    }.getOrElse { emptyList() }
        .also { decoder.clear() }
}

private fun cropImageWithPreset(
    context: Context,
    input: PickedInput,
    outputDir: File,
    preset: String,
    previewState: CropPreviewState? = null
): File? {
    val bitmap = openBitmap(context, input) ?: return null
    if (previewState != null && previewState.viewportSize != IntSize.Zero && previewState.imageSize != IntSize.Zero) {
        val cropRect = buildCropOverlayRect(previewState.viewportSize, preset)
        val baseSize = calculateBaseDisplaySize(previewState.viewportSize, previewState.imageSize)
        val displayWidth = baseSize.width * previewState.scale
        val displayHeight = baseSize.height * previewState.scale
        if (displayWidth > 0f && displayHeight > 0f) {
            val imageLeft = (previewState.viewportSize.width - displayWidth) / 2f + previewState.offset.x
            val imageTop = (previewState.viewportSize.height - displayHeight) / 2f + previewState.offset.y
            val srcLeft = ((cropRect.left - imageLeft) / displayWidth * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val srcTop = ((cropRect.top - imageTop) / displayHeight * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val srcRight = ((cropRect.right - imageLeft) / displayWidth * bitmap.width).toInt().coerceIn(srcLeft + 1, bitmap.width)
            val srcBottom = ((cropRect.bottom - imageTop) / displayHeight * bitmap.height).toInt().coerceIn(srcTop + 1, bitmap.height)
            val cropped = Bitmap.createBitmap(bitmap, srcLeft, srcTop, srcRight - srcLeft, srcBottom - srcTop)
            val baseName = sanitizeFileName(inputDisplayName(context, input)?.substringBeforeLast('.') ?: "crop")
            val target = File(outputDir, "${baseName}_${preset.replace(':', 'x')}.jpg")
            FileOutputStream(target).use { cropped.compress(Bitmap.CompressFormat.JPEG, 94, it) }
            cropped.recycle()
            bitmap.recycle()
            return target
        }
    }
    val (ratioW, ratioH) = presetToRatio(preset)
    val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
    val targetRatio = ratioW / ratioH
    val cropWidth: Int
    val cropHeight: Int
    if (srcRatio > targetRatio) {
        cropHeight = bitmap.height
        cropWidth = (cropHeight * targetRatio).toInt()
    } else {
        cropWidth = bitmap.width
        cropHeight = (cropWidth / targetRatio).toInt()
    }
    val x = max(0, (bitmap.width - cropWidth) / 2)
    val y = max(0, (bitmap.height - cropHeight) / 2)
    val cropped = Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
    val baseName = sanitizeFileName(inputDisplayName(context, input)?.substringBeforeLast('.') ?: "crop")
    val target = File(outputDir, "${baseName}_${preset.replace(':', 'x')}.jpg")
    FileOutputStream(target).use { cropped.compress(Bitmap.CompressFormat.JPEG, 94, it) }
    cropped.recycle()
    bitmap.recycle()
    return target
}

private fun readImageSize(context: Context, input: PickedInput): IntSize? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    when {
        input.file != null -> BitmapFactory.decodeFile(input.file.absolutePath, options)
        input.uri != null -> context.contentResolver.openInputStream(input.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        else -> return null
    }
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    return IntSize(options.outWidth, options.outHeight)
}

internal fun buildUniqueFile(directory: File, baseName: String, extension: String): File {
    directory.mkdirs()
    val ext = extension.trimStart('.')
    var candidate = File(directory, "$baseName.$ext")
    var index = 1
    while (candidate.exists()) {
        candidate = File(directory, "${baseName}_$index.$ext")
        index++
    }
    return candidate
}

internal fun sanitizeFileName(name: String): String {
    return name
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "untitled" }
}

