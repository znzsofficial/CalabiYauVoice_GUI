package ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.nekolaska.calabiyau.core.media.gif.AnimatedGifEncoder
import com.formdev.flatlaf.util.SystemFileChooser
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.*
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.*
import io.github.composefluent.surface.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.components.ComboBox
import ui.components.StyledWindow
import util.*
import java.awt.Desktop
import java.awt.Color
import java.awt.datatransfer.DataFlavor
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.File
import java.io.StringReader
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt

private enum class AssetToolTab(val title: String) { IMAGE("图片"), TEXT("文本"), AUDIO("音频") }
private enum class ImageToolMode(val label: String) { COMPRESS("压缩/转换"), NINE_GRID("九宫格"), CROP("居中裁切"), GIF_SPLIT("GIF 分解"), GIF_COMPOSE("GIF 合成") }
private enum class ImageExportFormat(val label: String, val ext: String) { JPG("JPG", "jpg"), PNG("PNG", "png") }
private enum class GifComposeScaleMode(val label: String) {
    FIT_LETTERBOX("适应（留黑边）"),
    FILL_CROP("填充（裁切）"),
    STRETCH("拉伸")
}
private enum class TimelineExportFormat(val label: String, val ext: String) { SRT("SRT", "srt"), LRC("LRC", "lrc"), VTT("VTT", "vtt"), ASS("ASS", "ass"), SSA("SSA", "ssa") }

private data class AudioToolInput(val source: File, val wavFile: File, val isTemporary: Boolean, val wavData: PcmWavData)

private enum class SpectrogramPaletteOption(
    val label: String,
    val lowColorArgb: Int,
    val highColorArgb: Int
) {
    Ocean("海蓝", 0xFF0F172A.toInt(), 0xFF38BDF8.toInt()),
    Fire("火焰", 0xFF1F0303.toInt(), 0xFFFFA726.toInt()),
    Violet("紫罗兰", 0xFF120B2E.toInt(), 0xFFC084FC.toInt()),
    Mono("单色", 0xFF050505.toInt(), 0xFFFFFFFF.toInt())
}

private data class TimelineClip(val id: Long, val startMs: Long, val endMs: Long, val text: String)
private data class ParsedTimeline(val clips: List<TimelineClip>, val truncated: Boolean)

private const val MIN_CLIP_MS = 100L
private const val MAX_CLIPS = 8000
private const val AUDIO_DECODE_TIMEOUT_SECONDS = 45L

private suspend inline fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }
}

@OptIn(ExperimentalFluentApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AssetToolsWindow(onCloseRequest: () -> Unit) {
    var outputPath by remember { mutableStateOf(AppPrefs.assetToolsOutputPath) }
    var currentTab by remember { mutableStateOf(AssetToolTab.IMAGE) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val windowState = rememberWindowState(width = 1200.dp, height = 780.dp, position = WindowPosition(Alignment.Center))

    fun log(message: String) {
        logLines = (logLines + message).takeLast(200)
    }

    StyledWindow(
        title = "素材工具",
        onCloseRequest = onCloseRequest,
        state = windowState,
        onKeyEvent = { event ->
            if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                onCloseRequest(); true
            } else false
        }
    ) {
        Row(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 左侧边栏 (导航与全局设置)
            Card(Modifier.width(220.dp).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("素材工作库", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("图像、文本与音频统一处理", fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    
                    AssetToolTab.entries.forEach { tab ->
                        val selected = currentTab == tab
                        val bg = if (selected) FluentTheme.colors.fillAccent.default else androidx.compose.ui.graphics.Color.Transparent
                        val contentColor = if (selected) FluentTheme.colors.text.onAccent.primary else FluentTheme.colors.text.text.primary
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(bg)
                                .clickable { currentTab = tab }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when (tab) {
                                AssetToolTab.IMAGE -> Icons.Regular.Image
                                AssetToolTab.TEXT -> Icons.Regular.DocumentText
                                AssetToolTab.AUDIO -> Icons.Regular.MusicNote1
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = contentColor)
                            Spacer(Modifier.width(12.dp))
                            Text(tab.title, color = contentColor, fontSize = 14.sp, fontWeight = if(selected) FontWeight.SemiBold else FontWeight.Normal)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    
                    Spacer(Modifier.weight(1f))
                    
                    if (isBusy) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProgressRing(size = 18.dp)
                            Text("处理中...", fontSize = 13.sp, color = FluentTheme.colors.text.text.secondary)
                        }
                    }
                    
                    Box(Modifier.fillMaxWidth().height(1.dp).background(FluentTheme.colors.stroke.surface.default))
                    Spacer(Modifier.height(16.dp))
                    
                    Text("输出目录", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            outputPath,
                            fontSize = 11.sp,
                            color = FluentTheme.colors.text.text.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            iconOnly = true,
                            onClick = { 
                                chooseDirectory(outputPath)?.let { 
                                    outputPath = it.absolutePath
                                    AppPrefs.assetToolsOutputPath = it.absolutePath 
                                } 
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Regular.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                        Button(
                            iconOnly = true,
                            onClick = { openDirectory(File(outputPath)) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Regular.Open, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // 右侧主工作区
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (currentTab) {
                        AssetToolTab.IMAGE -> ImageToolsTab(outputPath, isBusy, { isBusy = it }, ::log, scope)
                        AssetToolTab.TEXT -> TextToolsTab(outputPath, isBusy, { isBusy = it }, ::log, scope)
                        AssetToolTab.AUDIO -> AudioToolsTab(outputPath, isBusy, { isBusy = it }, ::log, scope)
                    }
                }

                if (logLines.isNotEmpty()) LogPanel(logLines, onClear = { logLines = emptyList() })
            }
        }
    }
}

@Composable
private fun ToolSectionCard(title: String, modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
                }
            }
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalFluentApi::class)
@Composable
private fun ImageToolsTab(
    outputPath: String,
    isBusy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onLog: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var isDragging by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(ImageToolMode.COMPRESS) }
    var format by remember { mutableStateOf(ImageExportFormat.JPG) }
    var quality by remember { mutableStateOf("82") }
    var cropRatio by remember { mutableStateOf("1:1") }
    var gifFps by remember { mutableStateOf("10") }
    var gifLoopCount by remember { mutableStateOf("0") }
    var gifQuantizeSample by remember { mutableStateOf("10") }
    var gifDitherEnabled by remember { mutableStateOf(false) }
    var gifScaleMode by remember { mutableStateOf(GifComposeScaleMode.FIT_LETTERBOX) }
    val imageExts = setOf("jpg", "jpeg", "png", "bmp", "gif")

    fun runImageTool() {
        if (files.isEmpty()) return
        val selectedFiles = files
        val selectedMode = mode
        val selectedFormat = format
        val selectedQuality = quality.toIntOrNull()?.coerceIn(1, 100) ?: 82
        val selectedRatio = cropRatio
        val selectedGifFps = gifFps.toIntOrNull()?.coerceIn(1, 60) ?: 10
        val selectedGifLoopCount = gifLoopCount.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val selectedGifQuantizeSample = gifQuantizeSample.toIntOrNull()?.coerceIn(1, 30) ?: 10
        scope.launch {
            onBusyChange(true)
            runCatchingCancellable {
                withContext(Dispatchers.IO) {
                    val outDir = File(outputPath, "图片工具/${selectedMode.label}").also { it.mkdirs() }
                    var count = 0
                    selectedFiles.forEach { file ->
                        when (selectedMode) {
                            ImageToolMode.COMPRESS -> {
                                val out = uniqueOutputFile(outDir, file.nameWithoutExtension, selectedFormat.ext)
                                writeImage(readImage(file), out, selectedFormat, selectedQuality / 100f)
                                count++
                            }
                            ImageToolMode.NINE_GRID -> {
                                exportNineGrid(file, outDir, selectedFormat, selectedQuality / 100f)
                                count += 9
                            }
                            ImageToolMode.CROP -> {
                                val ratio = parseRatio(selectedRatio)
                                val out = uniqueOutputFile(outDir, file.nameWithoutExtension + "_crop", selectedFormat.ext)
                                writeImage(centerCrop(readImage(file), ratio.first, ratio.second), out, selectedFormat, selectedQuality / 100f)
                                count++
                            }
                            ImageToolMode.GIF_SPLIT -> {
                                count += exportGifFrames(file, outDir)
                            }
                            ImageToolMode.GIF_COMPOSE -> Unit
                        }
                    }
                    if (selectedMode == ImageToolMode.GIF_COMPOSE) {
                        val out = uniqueOutputFile(outDir, "composed_${System.currentTimeMillis()}", "gif")
                        composeGif(
                            files = selectedFiles,
                            out = out,
                            fps = selectedGifFps,
                            loopCount = selectedGifLoopCount,
                            quantizeSample = selectedGifQuantizeSample,
                            ditherEnabled = gifDitherEnabled,
                            scaleMode = gifScaleMode
                        )
                        count = 1
                    }
                    outDir to count
                }
            }.onSuccess { (dir, count) ->
                onLog("图片工具完成：输出 $count 个文件 -> ${dir.absolutePath}")
                openDirectory(dir)
            }.onFailure { onLog("图片工具失败：${it.message ?: it::class.simpleName}") }
            onBusyChange(false)
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolSectionCard(title = "图片处理", subtitle = "压缩、九宫格、居中裁切、GIF 分解与合成") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                ComboBox(header = "模式", items = ImageToolMode.entries.map { it.label }, selected = mode.ordinal, onSelectionChange = { i, _ -> mode = ImageToolMode.entries[i] })
                ComboBox(header = "格式", items = ImageExportFormat.entries.map { it.label }, selected = format.ordinal, onSelectionChange = { i, _ -> format = ImageExportFormat.entries[i] })
                if (mode in setOf(ImageToolMode.COMPRESS, ImageToolMode.NINE_GRID, ImageToolMode.CROP)) {
                    TextField(value = quality, onValueChange = { if (it.all(Char::isDigit)) quality = it }, header = { Text("质量 1-100", fontSize = 12.sp) }, modifier = Modifier.width(120.dp), singleLine = true)
                }
                if (mode == ImageToolMode.CROP) {
                    TextField(value = cropRatio, onValueChange = { cropRatio = it }, header = { Text("比例", fontSize = 12.sp) }, modifier = Modifier.width(120.dp), singleLine = true)
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { chooseFiles(imageExts, multi = true)?.let { files = (files + it).distinctBy(File::getAbsolutePath) } }, disabled = isBusy) { Text("选择图片") }
                Button(onClick = ::runImageTool, disabled = isBusy || files.isEmpty()) { Text("开始处理") }
            }
            if (mode == ImageToolMode.GIF_COMPOSE) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    TextField(value = gifFps, onValueChange = { if (it.all(Char::isDigit)) gifFps = it }, header = { Text("帧速率 1-60", fontSize = 12.sp) }, modifier = Modifier.width(120.dp), singleLine = true)
                    TextField(value = gifLoopCount, onValueChange = { if (it.all(Char::isDigit)) gifLoopCount = it }, header = { Text("循环次数（0=无限）", fontSize = 12.sp) }, modifier = Modifier.width(160.dp), singleLine = true)
                    TextField(value = gifQuantizeSample, onValueChange = { if (it.all(Char::isDigit)) gifQuantizeSample = it }, header = { Text("量化质量 1-30", fontSize = 12.sp) }, modifier = Modifier.width(140.dp), singleLine = true)
                    ComboBox(header = "缩放模式", items = GifComposeScaleMode.entries.map { it.label }, selected = gifScaleMode.ordinal, onSelectionChange = { i, _ -> gifScaleMode = GifComposeScaleMode.entries[i] })
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(" ", fontSize = 12.sp)
                        Switcher(checked = gifDitherEnabled, onCheckStateChange = { gifDitherEnabled = it }, textBefore = true, text = "有序抖动")
                    }
                }
            }
        }
        FileDropList(
            files = files,
            isDragging = isDragging,
            onDraggingChange = { isDragging = it },
            onDropFiles = { dropped -> files = (files + collectFiles(dropped, imageExts)).distinctBy(File::getAbsolutePath) },
            onClear = { files = emptyList() },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun TextToolsTab(
    outputPath: String,
    isBusy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onLog: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var inputFile by remember { mutableStateOf<File?>(null) }
    var clips by remember { mutableStateOf<List<TimelineClip>>(emptyList()) }
    var format by remember { mutableStateOf(TimelineExportFormat.SRT) }
    var shiftMs by remember { mutableStateOf("0") }
    var normalize by remember { mutableStateOf(true) }
    var skipEmpty by remember { mutableStateOf(false) }

    fun importTimeline(file: File) {
        scope.launch {
            onBusyChange(true)
            runCatchingCancellable { withContext(Dispatchers.IO) { parseTimelineFile(file) } }
                .onSuccess {
                    inputFile = file
                    clips = it.clips
                    onLog("已导入 ${it.clips.size} 条时间轴${if (it.truncated) "（已截断）" else ""}: ${file.name}")
                }
                .onFailure { onLog("导入失败：${it.message ?: it::class.simpleName}") }
            onBusyChange(false)
        }
    }

    fun exportTimeline() {
        val sourceClips = clips
        if (sourceClips.isEmpty()) return
        val selectedFormat = format
        val selectedShift = shiftMs.toIntOrNull() ?: 0
        val doNormalize = normalize
        val doSkipEmpty = skipEmpty
        scope.launch {
            onBusyChange(true)
            runCatchingCancellable {
                withContext(Dispatchers.IO) {
                    val outDir = File(outputPath, "文本工具/时间轴").also { it.mkdirs() }
                    val out = uniqueOutputFile(outDir, inputFile?.nameWithoutExtension ?: "timeline", selectedFormat.ext)
                    val prepared = prepareTimeline(sourceClips, selectedShift, doNormalize, doSkipEmpty)
                    out.writeText(serializeTimeline(prepared, selectedFormat))
                    out
                }
            }.onSuccess {
                onLog("时间轴导出完成：${it.absolutePath}")
                openDirectory(it.parentFile)
            }.onFailure { onLog("导出失败：${it.message ?: it::class.simpleName}") }
            onBusyChange(false)
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolSectionCard(title = "时间轴转换", subtitle = "导入字幕或时间轴后可统一调整并导出") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Button(onClick = { chooseFiles(setOf("srt", "lrc", "vtt", "ass", "ssa"), multi = false)?.firstOrNull()?.let(::importTimeline) }, disabled = isBusy) { Text("导入字幕/时间轴") }
                ComboBox(header = "导出格式", items = TimelineExportFormat.entries.map { it.label }, selected = format.ordinal, onSelectionChange = { i, _ -> format = TimelineExportFormat.entries[i] })
                TextField(value = shiftMs, onValueChange = { if (it.matches(Regex("-?\\d*"))) shiftMs = it }, header = { Text("整体偏移 ms", fontSize = 12.sp) }, modifier = Modifier.width(140.dp), singleLine = true)
                Spacer(Modifier.weight(1f))
                Button(onClick = ::exportTimeline, disabled = isBusy || clips.isEmpty()) { Text("导出") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Switcher(checked = normalize, onCheckStateChange = { normalize = it }, textBefore = true, text = "规整排序")
                Switcher(checked = skipEmpty, onCheckStateChange = { skipEmpty = it }, textBefore = true, text = "跳过空文本")
            }
            Text(inputFile?.absolutePath ?: "未导入文件", fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TimelinePreview(clips, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun AudioToolsTab(
    outputPath: String,
    isBusy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onLog: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var input by remember { mutableStateOf<AudioToolInput?>(null) }
    var meta by remember { mutableStateOf<DesktopWavMeta?>(null) }
    var trimThreshold by remember { mutableStateOf("1.5") }
    var minSilenceMs by remember { mutableStateOf("120") }
    var trimMode by remember { mutableStateOf(DesktopWavTrimMode.BOTH) }
    var channelMode by remember { mutableStateOf(DesktopWavChannelMode.STEREO_TO_MONO) }
    var volumeMode by remember { mutableStateOf(DesktopWavVolumeMode.NORMALIZE) }
    var gainPercent by remember { mutableStateOf("120") }
    var targetPeak by remember { mutableStateOf("90") }
    var spectrogram by remember { mutableStateOf<BufferedImage?>(null) }
    var spectrogramWindowSize by remember { mutableStateOf("1024") }
    var spectrogramHopPercent by remember { mutableStateOf("25") }
    var spectrogramTimeBins by remember { mutableStateOf("720") }
    var spectrogramFrequencyBins by remember { mutableStateOf("256") }
    var spectrogramCutoffHz by remember { mutableStateOf("0") }
    var spectrogramZoomPercent by remember { mutableStateOf("180") }
    var spectrogramPalette by remember { mutableStateOf(SpectrogramPaletteOption.Ocean) }

    DisposableEffect(Unit) {
        onDispose {
            input?.takeIf { it.isTemporary }?.wavFile?.delete()
            runCatching { File(outputPath, "音频工具/_preview").takeIf { it.isDirectory }?.deleteRecursively() }
        }
    }

    fun currentSpectrogramConfig(): DesktopSpectrogramConfig = DesktopSpectrogramConfig(
        windowSize = spectrogramWindowSize.toIntOrNull()?.coerceIn(256, 8192) ?: 1024,
        hopRatio = (spectrogramHopPercent.toIntOrNull()?.coerceIn(5, 100) ?: 25) / 100f,
        maxTimeBins = spectrogramTimeBins.toIntOrNull()?.coerceIn(120, 4000) ?: 720,
        maxFrequencyBins = spectrogramFrequencyBins.toIntOrNull()?.coerceIn(64, 1024) ?: 256,
        cutoffFrequencyHz = spectrogramCutoffHz.toIntOrNull()?.coerceIn(0, 384000) ?: 0,
        lowColorArgb = spectrogramPalette.lowColorArgb,
        highColorArgb = spectrogramPalette.highColorArgb
    )

    fun regenerateSpectrogram() {
        val source = input ?: return
        val config = currentSpectrogramConfig()
        scope.launch {
            onBusyChange(true)
            runCatchingCancellable {
                withContext(Dispatchers.Default) { buildDesktopSpectrogramImage(source.wavData, config) }
            }.onSuccess {
                spectrogram = it
                onLog("频谱图已重新生成：${it.width} x ${it.height}")
            }.onFailure {
                onLog("频谱图生成失败：${it.message ?: it::class.simpleName}")
            }
            onBusyChange(false)
        }
    }

    fun load(file: File) {
        scope.launch {
            onBusyChange(true)
            val specConfig = currentSpectrogramConfig()
            runCatchingCancellable {
                withContext(Dispatchers.Default) {
                    val prepared = prepareAudioInput(file, File(outputPath, "音频工具/_preview"))
                    Triple(prepared, inspectDesktopWav(prepared.wavData), buildDesktopSpectrogramImage(prepared.wavData, specConfig))
                }
            }
                .onSuccess { (prepared, nextMeta, nextSpectrogram) ->
                    input?.takeIf { it.isTemporary }?.wavFile?.delete()
                    input = prepared
                    meta = nextMeta
                    spectrogram = nextSpectrogram
                    channelMode = if (nextMeta.channels == 1) DesktopWavChannelMode.MONO_TO_STEREO else DesktopWavChannelMode.STEREO_TO_MONO
                    onLog("已读取音频：${file.name}，${nextMeta.channels} 声道 / ${nextMeta.sampleRate} Hz / ${nextMeta.bitsPerSample} bit")
                }
                .onFailure { onLog("读取失败：${it.message ?: it::class.simpleName}") }
            onBusyChange(false)
        }
    }

    fun runAudio(action: String, block: (File, File) -> File) {
        val source = input ?: return
        scope.launch {
            onBusyChange(true)
            val specConfig = currentSpectrogramConfig()
            var nextWav: PcmWavData? = null
            runCatchingCancellable {
                withContext(Dispatchers.Default) {
                    val output = block(source.wavFile, File(outputPath, "音频工具/$action").also { it.mkdirs() })
                    val loadedWav = readPcmWav(output) ?: error("仅支持 PCM WAV")
                    nextWav = loadedWav
                    Triple(output, inspectDesktopWav(loadedWav), buildDesktopSpectrogramImage(loadedWav, specConfig))
                }
            }
                .onSuccess { (output, next, nextSpectrogram) ->
                    onLog("$action 完成：${output.absolutePath}")
                    openDirectory(output.parentFile)
                    input?.takeIf { previous -> previous.isTemporary }?.wavFile?.delete()
                    meta = next
                    spectrogram = nextSpectrogram
                    input = AudioToolInput(output, output, false, nextWav ?: error("仅支持 PCM WAV"))
                }
                .onFailure { onLog("$action 失败：${it.message ?: it::class.simpleName}") }
            onBusyChange(false)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolSectionCard(title = "音频输入", subtitle = "支持 WAV / MP3 / FLAC，自动转为临时 WAV 进行处理") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { chooseFiles(setOf("wav", "mp3", "flac"), multi = false)?.firstOrNull()?.let(::load) }, disabled = isBusy) { Text("选择音频") }
                Text(input?.source?.absolutePath ?: "未选择 WAV/MP3/FLAC", modifier = Modifier.weight(1f), fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            meta?.let {
                Text("${it.channels} 声道 / ${it.sampleRate} Hz / ${it.bitsPerSample} bit / ${formatDuration(it.durationMs)} / 峰值 ${"%.1f".format(it.peakPercent)}%", fontSize = 12.sp)
            }
        }
        spectrogram?.let { image ->
            ToolSectionCard(title = "频谱图", subtitle = "支持时间轴缩放、横向滚动和参数化生成") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${image.width} x ${image.height}", fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = ::regenerateSpectrogram, disabled = isBusy || input == null) { Text("重新生成") }
                    Button(
                        onClick = {
                            scope.launch {
                                onBusyChange(true)
                                runCatchingCancellable {
                                    withContext(Dispatchers.IO) {
                                        val outDir = File(outputPath, "音频工具/频谱图").also { it.mkdirs() }
                                        val out = uniqueOutputFile(outDir, input?.source?.nameWithoutExtension?.plus("_spectrogram") ?: "spectrogram", "png")
                                        ImageIO.write(image, "png", out)
                                        out
                                    }
                                }.onSuccess {
                                    onLog("频谱图已导出：${it.absolutePath}")
                                    openDirectory(it.parentFile)
                                }.onFailure {
                                    onLog("频谱图导出失败：${it.message ?: it::class.simpleName}")
                                }
                                onBusyChange(false)
                            }
                        },
                        disabled = isBusy
                    ) { Text("导出 PNG") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    TextField(value = spectrogramWindowSize, onValueChange = { if (it.all(Char::isDigit)) spectrogramWindowSize = it }, header = { Text("窗口", fontSize = 12.sp) }, modifier = Modifier.width(90.dp), singleLine = true)
                    TextField(value = spectrogramHopPercent, onValueChange = { if (it.all(Char::isDigit)) spectrogramHopPercent = it }, header = { Text("Hop %", fontSize = 12.sp) }, modifier = Modifier.width(90.dp), singleLine = true)
                    TextField(value = spectrogramTimeBins, onValueChange = { if (it.all(Char::isDigit)) spectrogramTimeBins = it }, header = { Text("时间格", fontSize = 12.sp) }, modifier = Modifier.width(90.dp), singleLine = true)
                    TextField(value = spectrogramFrequencyBins, onValueChange = { if (it.all(Char::isDigit)) spectrogramFrequencyBins = it }, header = { Text("频率格", fontSize = 12.sp) }, modifier = Modifier.width(90.dp), singleLine = true)
                    TextField(value = spectrogramCutoffHz, onValueChange = { if (it.all(Char::isDigit)) spectrogramCutoffHz = it }, header = { Text("截止 Hz", fontSize = 12.sp) }, modifier = Modifier.width(100.dp), singleLine = true)
                    TextField(value = spectrogramZoomPercent, onValueChange = { if (it.all(Char::isDigit)) spectrogramZoomPercent = it }, header = { Text("缩放 %", fontSize = 12.sp) }, modifier = Modifier.width(90.dp), singleLine = true)
                    ComboBox(header = "配色", items = SpectrogramPaletteOption.entries.map { it.label }, selected = spectrogramPalette.ordinal, onSelectionChange = { i, _ -> spectrogramPalette = SpectrogramPaletteOption.entries[i] })
                }
                val zoom = (spectrogramZoomPercent.toIntOrNull()?.coerceIn(50, 1000) ?: 180) / 100f
                val previewHeight = 180.dp
                val previewWidth = (image.width * zoom).coerceAtLeast(320f).dp
                val bitmap = remember(image) { image.toComposeImageBitmap() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(previewHeight + 18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(androidx.compose.ui.graphics.Color.Black)
                        .horizontalScroll(rememberScrollState())
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.width(previewWidth).height(previewHeight)
                    )
                }
            }
        }
        ToolSectionCard(title = "静音裁剪", subtitle = "按阈值移除前后静音，保留中间有效内容") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                ComboBox(header = "模式", items = DesktopWavTrimMode.entries.map { it.label }, selected = trimMode.ordinal, onSelectionChange = { i, _ -> trimMode = DesktopWavTrimMode.entries[i] })
                TextField(value = trimThreshold, onValueChange = { trimThreshold = it }, header = { Text("阈值 %", fontSize = 12.sp) }, modifier = Modifier.width(110.dp), singleLine = true)
                TextField(value = minSilenceMs, onValueChange = { if (it.all(Char::isDigit)) minSilenceMs = it }, header = { Text("最短静音 ms", fontSize = 12.sp) }, modifier = Modifier.width(140.dp), singleLine = true)
                Button(onClick = { runAudio("静音裁剪") { f, d -> trimDesktopWavSilence(f, d, (trimThreshold.toDoubleOrNull() ?: 1.5) / 100.0, minSilenceMs.toIntOrNull() ?: 120, trimMode) } }, disabled = isBusy || input == null) { Text("执行") }
            }
        }
        ToolSectionCard(title = "声道与音量", subtitle = "支持声道转换、增益调整与目标峰值归一化") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                ComboBox(header = "声道", items = DesktopWavChannelMode.entries.map { it.label }, selected = channelMode.ordinal, onSelectionChange = { i, _ -> channelMode = DesktopWavChannelMode.entries[i] })
                Button(onClick = { runAudio("声道转换") { f, d -> convertDesktopWavChannels(f, d, channelMode) } }, disabled = isBusy || input == null) { Text("转换声道") }
                Spacer(Modifier.width(16.dp))
                ComboBox(header = "音量", items = DesktopWavVolumeMode.entries.map { it.label }, selected = volumeMode.ordinal, onSelectionChange = { i, _ -> volumeMode = DesktopWavVolumeMode.entries[i] })
                TextField(value = gainPercent, onValueChange = { if (it.all(Char::isDigit)) gainPercent = it }, header = { Text("增益 %", fontSize = 12.sp) }, modifier = Modifier.width(100.dp), singleLine = true)
                TextField(value = targetPeak, onValueChange = { if (it.all(Char::isDigit)) targetPeak = it }, header = { Text("目标峰值 %", fontSize = 12.sp) }, modifier = Modifier.width(120.dp), singleLine = true)
                Button(onClick = { runAudio("音量处理") { f, d -> adjustDesktopWavVolume(f, d, volumeMode, gainPercent.toIntOrNull() ?: 120, targetPeak.toIntOrNull() ?: 90) } }, disabled = isBusy || input == null) { Text("处理音量") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalFluentApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun FileDropList(
    files: List<File>,
    isDragging: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    onDropFiles: (List<File>) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isDragging) FluentTheme.colors.fillAccent.default else FluentTheme.colors.stroke.card.default
    Card(modifier) {
        Box(
            Modifier.fillMaxSize().border(2.dp, borderColor, RoundedCornerShape(6.dp)).dragAndDropTarget(
                shouldStartDragAndDrop = { true },
                target = remember {
                    object : DragAndDropTarget {
                        override fun onStarted(event: DragAndDropEvent) = onDraggingChange(true)
                        override fun onEnded(event: DragAndDropEvent) = onDraggingChange(false)
                        override fun onDrop(event: DragAndDropEvent): Boolean {
                            onDraggingChange(false)
                            val drop = event.nativeEvent as? java.awt.dnd.DropTargetDropEvent ?: return false
                            drop.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY)
                            @Suppress("UNCHECKED_CAST")
                            val dropped = runCatching { drop.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File> }.getOrNull() ?: return false
                            onDropFiles(dropped)
                            return true
                        }
                    }
                }
            )
        ) {
            if (files.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Regular.ArrowDownload, contentDescription = null, modifier = Modifier.size(32.dp), tint = FluentTheme.colors.text.text.secondary)
                    Text("拖入图片文件或文件夹", color = FluentTheme.colors.text.text.secondary)
                }
            } else {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${files.size} 个文件", fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
                        Spacer(Modifier.weight(1f))
                        Button(onClick = onClear, modifier = Modifier.height(26.dp)) { Text("清空", fontSize = 11.sp) }
                    }
                    Spacer(Modifier.height(6.dp))
                    val state = rememberLazyListState()
                    ScrollbarContainer(adapter = rememberScrollbarAdapter(state), modifier = Modifier.fillMaxSize()) {
                        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxSize()) {
                            items(files) { file ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(FluentTheme.colors.control.secondary).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Regular.Document, contentDescription = null, modifier = Modifier.size(14.dp), tint = FluentTheme.colors.fillAccent.default)
                                    Spacer(Modifier.width(6.dp))
                                    Text(file.name, modifier = Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("%.1f KB".format(file.length() / 1024.0), fontSize = 11.sp, color = FluentTheme.colors.text.text.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun TimelinePreview(clips: List<TimelineClip>, modifier: Modifier = Modifier) {
    Card(modifier) {
        val state = rememberLazyListState()
        ScrollbarContainer(adapter = rememberScrollbarAdapter(state), modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = state, contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (clips.isEmpty()) {
                    item { Text("导入后将在这里预览时间轴", color = FluentTheme.colors.text.text.secondary) }
                } else {
                    items(clips.take(300)) { clip ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(FluentTheme.colors.control.secondary).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${formatShortTime(clip.startMs)} - ${formatShortTime(clip.endMs)}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.width(150.dp))
                            Text(clip.text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun LogPanel(logLines: List<String>, onClear: () -> Unit) {
    Card(Modifier.fillMaxWidth().height(120.dp)) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("日志", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Button(onClick = onClear, modifier = Modifier.height(24.dp)) { Text("清空", fontSize = 11.sp) }
            }
            val state = rememberLazyListState()
            LaunchedEffect(logLines.size) { if (logLines.isNotEmpty()) state.animateScrollToItem(logLines.size - 1) }
            ScrollbarContainer(adapter = rememberScrollbarAdapter(state), modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                    items(logLines) { Text(it, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                }
            }
        }
    }
}

private fun chooseDirectory(initialPath: String): File? = SystemFileChooser(initialPath.ifBlank { home }).apply {
    fileSelectionMode = SystemFileChooser.DIRECTORIES_ONLY
}.takeIf { it.showOpenDialog(null) == SystemFileChooser.APPROVE_OPTION }?.selectedFile

private fun chooseFiles(exts: Set<String>, multi: Boolean): List<File>? = SystemFileChooser(home).apply {
    fileSelectionMode = SystemFileChooser.FILES_ONLY
    isMultiSelectionEnabled = multi
}.takeIf { it.showOpenDialog(null) == SystemFileChooser.APPROVE_OPTION }?.let { chooser ->
    val files = if (multi) chooser.selectedFiles.toList() else listOf(chooser.selectedFile)
    files.filter { it.isFile && it.extension.lowercase() in exts }
}

private fun collectFiles(files: List<File>, exts: Set<String>): List<File> = files.flatMap { file ->
    if (file.isDirectory) file.walkTopDown().filter { it.isFile && it.extension.lowercase() in exts }.toList()
    else listOf(file).filter { it.isFile && it.extension.lowercase() in exts }
}

private fun prepareAudioInput(file: File, tempDir: File): AudioToolInput {
    require(file.isFile) { "音频文件不存在" }
    val ext = file.extension.lowercase()
    require(ext in setOf("wav", "mp3", "flac")) { "不支持的音频格式：${file.extension}" }
    if (ext == "wav") {
        readPcmWav(file)?.let { wavData ->
            return AudioToolInput(file, file, false, wavData)
        }
        tempDir.mkdirs()
        val wav = uniqueOutputFile(tempDir, file.nameWithoutExtension + "_pcm", "wav")
        runAudioDecodeWithTimeout { decodeAudioToPcmWav(file, wav) }
        val wavData = readPcmWav(wav) ?: error("无法解码此 WAV 文件")
        return AudioToolInput(file, wav, true, wavData)
    }
    tempDir.mkdirs()
    val wav = uniqueOutputFile(tempDir, file.nameWithoutExtension + "_pcm", "wav")
    runAudioDecodeWithTimeout { decodeAudioToPcmWav(file, wav) }
    val wavData = readPcmWav(wav) ?: error("仅支持 PCM WAV")
    return AudioToolInput(file, wav, true, wavData)
}

private fun runAudioDecodeWithTimeout(block: () -> Unit) {
    val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "asset-tools-audio-decode").apply { isDaemon = true }
    }
    try {
        val future = executor.submit(Callable { block() })
        try {
            future.get(AUDIO_DECODE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            error("音频解码超时，请尝试先用音频转换工具转为 WAV")
        }
    } finally {
        executor.shutdownNow()
    }
}

private fun decodeAudioToPcmWav(source: File, target: File) {
    val sourceFormat = openAudioInputStream(source).use { input -> input.format }
    val sampleRate = sourceFormat.sampleRate.takeIf { it.isFinite() && it > 0f } ?: 44100f
    val channels = sourceFormat.channels.takeIf { it > 0 } ?: 2
    val bits = sourceFormat.sampleSizeInBits.takeIf { it in setOf(8, 16, 24, 32) } ?: 16
    val pcmFormat = AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        sampleRate,
        bits,
        channels,
        channels * (bits / 8),
        sampleRate,
        false
    )
    runCatching {
        writeDecodedPcm(source, pcmFormat, target)
    }.getOrElse { firstError ->
        val fallback = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate,
            16,
            channels,
            channels * 2,
            sampleRate,
            false
        )
        runCatching { target.delete() }
        runCatching {
            writeDecodedPcm(source, fallback, target)
        }.getOrElse { throw firstError }
    }
}

private fun writeDecodedPcm(source: File, format: AudioFormat, target: File) {
    openAudioInputStream(source).use { input ->
        AudioSystem.getAudioInputStream(format, input).use { pcm ->
            target.parentFile?.mkdirs()
            AudioSystem.write(pcm, AudioFileFormat.Type.WAVE, target)
        }
    }
}

private fun openAudioInputStream(source: File): javax.sound.sampled.AudioInputStream {
    return AudioSystem.getAudioInputStream(source)
}

private fun openDirectory(dir: File?) {
    val target = dir?.let { if (it.exists()) it else it.parentFile } ?: return
    runCatching { Desktop.getDesktop().open(target) }
}

private fun uniqueOutputFile(dir: File, baseName: String, extension: String): File {
    dir.mkdirs()
    var candidate = File(dir, "$baseName.$extension")
    var index = 2
    while (candidate.exists()) {
        candidate = File(dir, "$baseName ($index).$extension")
        index++
    }
    return candidate
}

private fun readImage(file: File): BufferedImage = ImageIO.read(file) ?: error("无法读取图片：${file.name}")

private fun writeImage(source: BufferedImage, out: File, format: ImageExportFormat, quality: Float) {
    val image = if (format == ImageExportFormat.JPG && source.type != BufferedImage.TYPE_INT_RGB) {
        BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB).also { rgb ->
            val g = rgb.createGraphics()
            g.color = java.awt.Color.WHITE
            g.fillRect(0, 0, rgb.width, rgb.height)
            g.drawImage(source, 0, 0, null)
            g.dispose()
        }
    } else source
    if (format == ImageExportFormat.JPG) {
        val writer: ImageWriter = ImageIO.getImageWritersByFormatName("jpg").next()
        ImageIO.createImageOutputStream(out).use { ios ->
            writer.output = ios
            val params = writer.defaultWriteParam
            params.compressionMode = ImageWriteParam.MODE_EXPLICIT
            params.compressionQuality = quality.coerceIn(0.01f, 1f)
            writer.write(null, IIOImage(image, null, null), params)
        }
        writer.dispose()
    } else {
        ImageIO.write(image, "png", out)
    }
}

private fun exportNineGrid(file: File, outDir: File, format: ImageExportFormat, quality: Float) {
    val image = readImage(file)
    val cellW = image.width / 3
    val cellH = image.height / 3
    if (cellW <= 0 || cellH <= 0) error("图片尺寸过小：${file.name}")
    for (y in 0 until 3) {
        for (x in 0 until 3) {
            val w = if (x == 2) image.width - x * cellW else cellW
            val h = if (y == 2) image.height - y * cellH else cellH
            val part = image.getSubimage(x * cellW, y * cellH, w, h)
            val out = uniqueOutputFile(outDir, "${file.nameWithoutExtension}_${y * 3 + x + 1}", format.ext)
            writeImage(part, out, format, quality)
        }
    }
}

private fun exportGifFrames(file: File, outDir: File): Int {
    require(file.extension.equals("gif", ignoreCase = true)) { "请选择 GIF 文件" }
    val reader = ImageIO.getImageReadersByFormatName("gif").asSequence().firstOrNull() ?: error("当前环境不支持 GIF 解码")
    ImageIO.createImageInputStream(file).use { input ->
        reader.input = input
        val count = reader.getNumImages(true)
        for (index in 0 until count) {
            val frame = reader.read(index)
            val out = uniqueOutputFile(outDir, "${file.nameWithoutExtension}_%03d".format(index + 1), "png")
            ImageIO.write(frame, "png", out)
        }
        reader.dispose()
        return count
    }
}

private fun composeGif(
    files: List<File>,
    out: File,
    fps: Int,
    loopCount: Int = 0,
    quantizeSample: Int = 10,
    ditherEnabled: Boolean = false,
    scaleMode: GifComposeScaleMode = GifComposeScaleMode.FIT_LETTERBOX
) {
    val images = files.filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "bmp") }
        .sortedBy { it.name.lowercase() }
        .map(::readImage)
    require(images.isNotEmpty()) { "请选择用于合成 GIF 的静态图片" }
    val width = images.first().width
    val height = images.first().height
    val encoder = AnimatedGifEncoder()
    encoder.setSize(width, height)
    encoder.setDelay((1000 / fps.coerceIn(1, 60)).coerceAtLeast(1))
    encoder.setRepeat(loopCount.coerceAtLeast(0))
    encoder.setQuality(quantizeSample.coerceIn(1, 30))
    if (!encoder.start(out.absolutePath)) error("GIF 编码器启动失败")
    var writtenFrames = 0
    var skippedFrames = 0
    images.forEach { image ->
        val normalized = normalizeGifFrame(image, width, height, scaleMode)
        val prepared = if (ditherEnabled) applyOrderedDither(normalized) else normalized
        val pixels = IntArray(prepared.width * prepared.height)
        prepared.getRGB(0, 0, prepared.width, prepared.height, pixels, 0, prepared.width)
        if (encoder.addFrame(pixels, prepared.width, prepared.height)) writtenFrames++ else skippedFrames++
    }
    if (writtenFrames <= 0) error("未写入任何帧，已跳过 $skippedFrames 帧")
    if (!encoder.finish()) error("GIF 结束写入失败")
}

private fun normalizeGifFrame(source: BufferedImage, targetWidth: Int, targetHeight: Int, mode: GifComposeScaleMode): BufferedImage {
    if (source.width == targetWidth && source.height == targetHeight) return source
    return when (mode) {
        GifComposeScaleMode.STRETCH -> scaleBufferedImage(source, targetWidth, targetHeight)
        GifComposeScaleMode.FIT_LETTERBOX -> {
            val scale = min(targetWidth.toFloat() / source.width.toFloat(), targetHeight.toFloat() / source.height.toFloat())
            val drawW = max(1, (source.width * scale).roundToInt())
            val drawH = max(1, (source.height * scale).roundToInt())
            val scaled = scaleBufferedImage(source, drawW, drawH)
            BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB).also { out ->
                val g = out.createGraphics()
                try {
                    g.color = Color.BLACK
                    g.fillRect(0, 0, targetWidth, targetHeight)
                    val left = ((targetWidth - drawW) / 2f).roundToInt()
                    val top = ((targetHeight - drawH) / 2f).roundToInt()
                    g.drawImage(scaled, left, top, null)
                } finally {
                    g.dispose()
                }
            }
        }
        GifComposeScaleMode.FILL_CROP -> {
            val scale = max(targetWidth.toFloat() / source.width.toFloat(), targetHeight.toFloat() / source.height.toFloat())
            val drawW = max(1, (source.width * scale).roundToInt())
            val drawH = max(1, (source.height * scale).roundToInt())
            val scaled = scaleBufferedImage(source, drawW, drawH)
            val x = ((drawW - targetWidth) / 2).coerceAtLeast(0)
            val y = ((drawH - targetHeight) / 2).coerceAtLeast(0)
            BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB).also { out ->
                val g = out.createGraphics()
                try {
                    g.drawImage(scaled, 0, 0, targetWidth, targetHeight, x, y, x + targetWidth, y + targetHeight, null)
                } finally {
                    g.dispose()
                }
            }
        }
    }
}

private fun applyOrderedDither(source: BufferedImage): BufferedImage {
    val width = source.width
    val height = source.height
    val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val matrix = arrayOf(
        intArrayOf(0, 8, 2, 10),
        intArrayOf(12, 4, 14, 6),
        intArrayOf(3, 11, 1, 9),
        intArrayOf(15, 7, 13, 5)
    )
    for (y in 0 until height) {
        for (x in 0 until width) {
            val color = source.getRGB(x, y)
            val threshold = matrix[y and 3][x and 3] - 8
            val alpha = color ushr 24 and 0xff
            val red = ((color ushr 16) and 0xff + threshold * 2).coerceIn(0, 255)
            val green = ((color ushr 8) and 0xff + threshold * 2).coerceIn(0, 255)
            val blue = ((color) and 0xff + threshold * 2).coerceIn(0, 255)
            out.setRGB(x, y, (alpha shl 24) or (red shl 16) or (green shl 8) or blue)
        }
    }
    return out
}

private fun scaleBufferedImage(source: BufferedImage, targetWidth: Int, targetHeight: Int): BufferedImage {
    if (source.width == targetWidth && source.height == targetHeight) return source
    val out = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    try {
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(source, 0, 0, targetWidth, targetHeight, null)
    } finally {
        g.dispose()
    }
    return out
}

private fun BufferedImage.toArgb(): BufferedImage {
    if (type == BufferedImage.TYPE_INT_ARGB) return this
    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { out ->
        val g = out.createGraphics()
        g.drawImage(this, 0, 0, null)
        g.dispose()
    }
}

private fun centerCrop(image: BufferedImage, ratioW: Int, ratioH: Int): BufferedImage {
    val targetRatio = ratioW.toDouble() / ratioH
    val currentRatio = image.width.toDouble() / image.height
    val cropW: Int
    val cropH: Int
    if (currentRatio > targetRatio) {
        cropH = image.height
        cropW = (cropH * targetRatio).roundToInt().coerceAtLeast(1)
    } else {
        cropW = image.width
        cropH = (cropW / targetRatio).roundToInt().coerceAtLeast(1)
    }
    val x = (image.width - cropW) / 2
    val y = (image.height - cropH) / 2
    return image.getSubimage(x, y, cropW, cropH)
}

private fun parseRatio(value: String): Pair<Int, Int> {
    val parts = value.split(':', '/', 'x', 'X').mapNotNull { it.trim().toIntOrNull() }
    return if (parts.size >= 2 && parts[0] > 0 && parts[1] > 0) parts[0] to parts[1] else 1 to 1
}

private fun parseTimelineFile(file: File): ParsedTimeline {
    val text = file.readText()
    return when (file.extension.lowercase()) {
        "lrc" -> parseLrcStream(BufferedReader(StringReader(text)))
        "ass", "ssa" -> parseAssStream(BufferedReader(StringReader(text)))
        else -> parseSrtStream(BufferedReader(StringReader(text)))
    }.let { ParsedTimeline(it.first, it.second) }
}

private fun parseSrtStream(reader: BufferedReader): Pair<List<TimelineClip>, Boolean> {
    val clips = mutableListOf<TimelineClip>()
    var id = 1L
    var truncated = false
    val block = mutableListOf<String>()
    fun flush() {
        val timeLine = block.firstOrNull { it.contains("-->") } ?: return
        val parts = timeLine.split("-->")
        if (parts.size < 2) return
        val timeIndex = block.indexOf(timeLine)
        val text = block.drop(timeIndex + 1).joinToString("\n").trim()
        clips += TimelineClip(id++, parseSrtTime(parts[0].trim()), parseSrtTime(parts[1].trim()), text)
    }
    reader.lineSequence().forEach { line ->
        if (clips.size >= MAX_CLIPS) { truncated = true; return@forEach }
        if (line.isBlank()) { flush(); block.clear() } else block += line
    }
    if (block.isNotEmpty() && clips.size < MAX_CLIPS) flush()
    return clips to truncated
}

private fun parseLrcStream(reader: BufferedReader): Pair<List<TimelineClip>, Boolean> {
    val entries = mutableListOf<Pair<Long, String>>()
    val regex = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]")
    var truncated = false
    reader.lineSequence().forEach { line ->
        if (entries.size >= MAX_CLIPS) { truncated = true; return@forEach }
        val matches = regex.findAll(line).toList()
        if (matches.isNotEmpty()) {
            val text = line.substringAfterLast(']').trim()
            matches.forEach { match ->
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val frac = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                entries += ((min * 60 + sec) * 1000 + frac) to text
            }
        }
    }
    val sorted = entries.sortedBy { it.first }.take(MAX_CLIPS)
    return sorted.mapIndexed { index, entry ->
        val end = sorted.getOrNull(index + 1)?.first ?: (entry.first + 2000)
        TimelineClip(1L + index, entry.first, max(entry.first + MIN_CLIP_MS, end), entry.second)
    } to truncated
}

private fun parseAssStream(reader: BufferedReader): Pair<List<TimelineClip>, Boolean> {
    val clips = mutableListOf<TimelineClip>()
    var id = 1L
    var truncated = false
    reader.lineSequence().forEach { line ->
        if (clips.size >= MAX_CLIPS) { truncated = true; return@forEach }
        if (line.startsWith("Dialogue:", ignoreCase = true)) {
            val payload = line.substringAfter(':').trim()
            val parts = payload.split(',', limit = 10)
            if (parts.size >= 10) clips += TimelineClip(id++, parseAssTime(parts[1]), parseAssTime(parts[2]), parts[9].replace("\\N", "\n"))
        }
    }
    return clips to truncated
}

private fun prepareTimeline(source: List<TimelineClip>, shiftMs: Int, normalize: Boolean, skipEmpty: Boolean): List<TimelineClip> {
    var clips = if (skipEmpty) source.filter { it.text.isNotBlank() } else source
    if (clips.isEmpty()) error("没有可导出的片段")
    clips = clips.sortedBy { it.startMs }
    if (normalize) {
        var cursor = 0L
        clips = clips.map { clip ->
            val start = max(cursor, max(0L, clip.startMs))
            val end = max(start + MIN_CLIP_MS, clip.endMs)
            cursor = end
            clip.copy(startMs = start, endMs = end)
        }
    }
    return clips.map {
        val start = max(0L, it.startMs + shiftMs)
        val end = max(start + MIN_CLIP_MS, it.endMs + shiftMs)
        it.copy(startMs = start, endMs = end)
    }
}

private fun serializeTimeline(clips: List<TimelineClip>, format: TimelineExportFormat): String = when (format) {
    TimelineExportFormat.SRT -> buildString { clips.forEachIndexed { i, c -> appendLine(i + 1); appendLine("${formatSrt(c.startMs)} --> ${formatSrt(c.endMs)}"); appendLine(c.text); appendLine() } }
    TimelineExportFormat.LRC -> buildString { clips.forEach { appendLine("[${lrcTime(it.startMs)}]${it.text.replace('\n', ' ')}") } }
    TimelineExportFormat.VTT -> buildString { appendLine("WEBVTT"); appendLine(); clips.forEach { appendLine("${formatVttTime(it.startMs)} --> ${formatVttTime(it.endMs)}"); appendLine(it.text); appendLine() } }
    TimelineExportFormat.ASS -> buildString { appendLine("[Script Info]"); appendLine("ScriptType: v4.00+"); appendLine("[Events]"); appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"); clips.forEach { appendLine("Dialogue: 0,${formatAssTime(it.startMs)},${formatAssTime(it.endMs)},Default,,0,0,0,,${it.text.replace('\n', ' ')}") } }
    TimelineExportFormat.SSA -> buildString { appendLine("[Script Info]"); appendLine("ScriptType: v4.00"); appendLine("[Events]"); appendLine("Format: Marked, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"); clips.forEach { appendLine("Dialogue: Marked=0,${formatAssTime(it.startMs)},${formatAssTime(it.endMs)},Default,,0,0,0,,${it.text.replace('\n', ' ')}") } }
}

private fun parseSrtTime(value: String): Long {
    val match = Regex("(\\d+):(\\d{2}):(\\d{2})[,.](\\d{1,3})").find(value) ?: return 0L
    return match.groupValues[1].toLong() * 3_600_000 + match.groupValues[2].toLong() * 60_000 + match.groupValues[3].toLong() * 1000 + match.groupValues[4].padEnd(3, '0').take(3).toLong()
}

private fun parseAssTime(value: String): Long {
    val match = Regex("(\\d+):(\\d{2}):(\\d{2})[.](\\d{1,2})").find(value.trim()) ?: return 0L
    return match.groupValues[1].toLong() * 3_600_000 + match.groupValues[2].toLong() * 60_000 + match.groupValues[3].toLong() * 1000 + match.groupValues[4].padEnd(2, '0').take(2).toLong() * 10
}

private fun formatSrt(ms: Long): String = "%02d:%02d:%02d,%03d".format(ms / 3_600_000, (ms / 60_000) % 60, (ms / 1000) % 60, ms % 1000)
private fun formatVttTime(ms: Long): String = formatSrt(ms).replace(',', '.')
private fun lrcTime(ms: Long): String = "%02d:%02d.%02d".format(ms / 60_000, (ms / 1000) % 60, (ms % 1000) / 10)
private fun formatAssTime(ms: Long): String = "%d:%02d:%02d.%02d".format(ms / 3_600_000, (ms / 60_000) % 60, (ms / 1000) % 60, (ms % 1000) / 10)
private fun formatShortTime(ms: Long): String = "%02d:%02d.%03d".format((ms / 60_000) % 60, (ms / 1000) % 60, ms % 1000)
private fun formatDuration(ms: Long): String = "%02d:%02d.%03d".format(ms / 60_000, (ms / 1000) % 60, ms % 1000)
