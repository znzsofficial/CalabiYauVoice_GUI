package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nekolaska.calabiyau.core.media.audio.PcmWavData
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Button
import io.github.composefluent.component.ComboBox
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import util.*
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.imageio.ImageIO
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val assetAudioDecodeExecutor: ExecutorService by lazy {
    Executors.newSingleThreadExecutor { task ->
        Thread(task, "asset-tools-audio-decode").apply { isDaemon = true }
    }
}

internal enum class SpectrogramPaletteOption(
    val label: String,
    val lowColorArgb: Int,
    val highColorArgb: Int
) {
    Ocean("海蓝", 0xFF01030A.toInt(), 0xFF38BDF8.toInt()),
    Fire("火焰", 0xFF070000.toInt(), 0xFFFFA726.toInt()),
    Violet("紫罗兰", 0xFF03010A.toInt(), 0xFFC084FC.toInt()),
    Mono("单色", 0xFF050505.toInt(), 0xFFFFFFFF.toInt())
}

private const val AUDIO_DECODE_TIMEOUT_SECONDS = 45L

@OptIn(ExperimentalFluentApi::class)
@Composable
internal fun AudioToolsTab(
    outputPath: String,
    isBusy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onLog: (String) -> Unit,
    scope: CoroutineScope
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
    var gateThreshold by remember { mutableStateOf("2") }
    var gateReductionDb by remember { mutableStateOf("24") }
    var fadeInMs by remember { mutableStateOf("10") }
    var fadeOutMs by remember { mutableStateOf("50") }
    var phaseMode by remember { mutableStateOf(DesktopWavPhaseMode.INVERT_ALL) }
    var spectrogram by remember { mutableStateOf<BufferedImage?>(null) }
    var spectrogramWindowSize by remember { mutableStateOf("1024") }
    var spectrogramHopPercent by remember { mutableStateOf("25") }
    var spectrogramTimeBins by remember { mutableStateOf("720") }
    var spectrogramFrequencyBins by remember { mutableStateOf("256") }
    var spectrogramCutoffHz by remember { mutableStateOf("0") }
    var spectrogramZoomPercent by remember { mutableStateOf("180") }
    var spectrogramPreviewHeight by remember { mutableStateOf(180f) }
    var spectrogramGainDb by remember { mutableStateOf("6") }
    var spectrogramGamma by remember { mutableStateOf("1.2") }
    var spectrogramFloorDb by remember { mutableStateOf("-72") }
    var autoSpectrogramTuning by remember { mutableStateOf(true) }
    var spectrogramPalette by remember { mutableStateOf(SpectrogramPaletteOption.Ocean) }
    val audioPreviewWorkDir = remember { File(System.getProperty("java.io.tmpdir"), "CalabiYauVoice/audio_tool_current_${System.nanoTime()}") }
    val audioHistory = remember { DesktopAudioHistoryController(audioPreviewWorkDir) }

    DisposableEffect(Unit) {
        onDispose {
            audioHistory.cleanup(input)
            runCatching { File(outputPath, "音频工具/_preview").takeIf { it.isDirectory }?.deleteRecursively() }
            runCatching { File(System.getProperty("java.io.tmpdir"), "CalabiYauVoice/audio_tool_preview").takeIf { it.isDirectory }?.deleteRecursively() }
        }
    }

    fun setAudioStep(step: AudioHistoryStep) {
        input?.takeIf { it.isTemporary && it.wavFile != audioHistory.materializedInput?.wavFile }?.wavFile?.delete()
        input = audioHistory.materializedInput
        meta = step.meta
        spectrogram = step.spectrogram
        if (step.meta.sampleRate >= 96000 && spectrogramPreviewHeight < 360f) spectrogramPreviewHeight = 360f
        channelMode = if (step.meta.channels == 1) DesktopWavChannelMode.MONO_TO_STEREO else DesktopWavChannelMode.STEREO_TO_MONO
    }

    fun pushAudioHistory(label: String, nextInput: AudioToolInput, nextMeta: DesktopWavMeta, nextSpectrogram: BufferedImage?) {
        setAudioStep(audioHistory.push(label, nextInput, nextMeta, nextSpectrogram))
    }

    fun moveAudioHistory(delta: Int) {
        val step = audioHistory.select(audioHistory.nextIndex(delta)) ?: return
        setAudioStep(step)
        onLog("时间轴：${step.label}")
    }

    fun currentSpectrogramConfig(): DesktopSpectrogramConfig = DesktopSpectrogramConfig(
        windowSize = spectrogramWindowSize.toIntOrNull()?.coerceIn(256, 8192) ?: 1024,
        hopRatio = (spectrogramHopPercent.toIntOrNull()?.coerceIn(5, 100) ?: 25) / 100f,
        maxTimeBins = spectrogramTimeBins.toIntOrNull()?.coerceIn(120, 128000) ?: 2400,
        maxFrequencyBins = spectrogramFrequencyBins.toIntOrNull()?.coerceIn(64, 4096) ?: 1024,
        cutoffFrequencyHz = spectrogramCutoffHz.toIntOrNull()?.coerceIn(0, 384000) ?: 0,
        lowColorArgb = spectrogramPalette.lowColorArgb,
        highColorArgb = spectrogramPalette.highColorArgb,
        gainDb = spectrogramGainDb.toDoubleOrNull()?.coerceIn(-24.0, 36.0) ?: 6.0,
        gamma = spectrogramGamma.toDoubleOrNull()?.coerceIn(0.35, 3.0) ?: 1.2,
        floorDb = spectrogramFloorDb.toDoubleOrNull()?.coerceIn(-120.0, -24.0) ?: -72.0
    )

    fun applyAutoSpectrogramTuning(zoomPercent: Int, durationMs: Long? = meta?.durationMs) {
        val zoom = zoomPercent.coerceIn(50, 1500)
        val zoomRatio = zoom / 180f
        val durationSeconds = durationMs?.takeIf { it > 0 }?.let { it / 1000.0 } ?: 60.0
        val durationFactor = when {
            durationSeconds < 8.0 -> 2.2f
            durationSeconds < 20.0 -> 2.0f
            durationSeconds < 60.0 -> 1.8f
            durationSeconds < 180.0 -> 1.7f
            durationSeconds < 600.0 -> 1.6f
            else -> 1.45f
        }
        spectrogramWindowSize = when {
            zoom >= 720 -> "256"
            zoom >= 320 -> "384"
            zoom >= 140 -> "512"
            durationSeconds > 300.0 -> "2048"
            else -> "1024"
        }
        spectrogramHopPercent = when {
            zoom >= 720 -> "5"
            zoom >= 320 -> "5"
            zoom >= 140 -> "6"
            zoom >= 90 -> "10"
            else -> "14"
        }
        val targetTimeBins = (12000 * zoomRatio * durationFactor).roundToInt()
        val minTimeBins = when {
            durationSeconds < 15.0 -> 6000
            durationSeconds < 120.0 -> 9000
            durationSeconds < 600.0 -> 16000
            else -> 24000
        }
        spectrogramTimeBins = targetTimeBins.coerceIn(minTimeBins, 128000).toString()
        val frequencyFactor = when {
            durationSeconds < 20.0 -> 1.35f
            durationSeconds > 300.0 -> 1.1f
            else -> 1f
        }
        spectrogramFrequencyBins = (1024 * max(0.9f, min(3.0f, zoomRatio)) * frequencyFactor).roundToInt().coerceIn(512, 4096).toString()
    }

    fun setSpectrogramZoomPercent(value: String) {
        spectrogramZoomPercent = value
        val zoom = value.toIntOrNull()?.coerceIn(50, 1500) ?: return
        if (autoSpectrogramTuning) applyAutoSpectrogramTuning(zoom)
    }

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

    fun setSpectrogramPalette(option: SpectrogramPaletteOption) {
        spectrogramPalette = option
        val source = input ?: return
        scope.launch {
            onBusyChange(true)
            runCatchingCancellable {
                withContext(Dispatchers.Default) { buildDesktopSpectrogramImage(source.wavData, currentSpectrogramConfig()) }
            }.onSuccess {
                spectrogram = it
                onLog("频谱图配色已更新：${option.label}")
            }.onFailure {
                onLog("频谱图配色更新失败：${it.message ?: it::class.simpleName}")
            }
            onBusyChange(false)
        }
    }

    fun load(file: File) {
        scope.launch {
            onBusyChange(true)
            runCatchingCancellable {
                withContext(Dispatchers.Default) {
                    val prepared = prepareAudioInput(file, File(outputPath, "音频工具/_preview"))
                    val nextMeta = inspectDesktopWav(prepared.wavData)
                    Triple(prepared, nextMeta, nextMeta)
                }
            }
                .onSuccess { (prepared, nextMeta, _) ->
                    if (autoSpectrogramTuning) applyAutoSpectrogramTuning(spectrogramZoomPercent.toIntOrNull() ?: 180, nextMeta.durationMs)
                    val nextSpectrogram = withContext(Dispatchers.Default) { buildDesktopSpectrogramImage(prepared.wavData, currentSpectrogramConfig()) }
                    audioHistory.clear()
                    pushAudioHistory("导入", prepared, nextMeta, nextSpectrogram)
                    onLog("已读取音频：${file.name}，${nextMeta.channels} 声道 / ${nextMeta.sampleRate} Hz / ${nextMeta.bitsPerSample} bit")
                }
                .onFailure { onLog("读取失败：${audioDecodeFailureMessage(it)}") }
            onBusyChange(false)
        }
    }

    fun runAudio(action: String, block: (File, File) -> File) {
        val source = input ?: return
        scope.launch {
            onBusyChange(true)
            var nextWav: PcmWavData? = null
            runCatchingCancellable {
                withContext(Dispatchers.Default) {
                    val output = block(source.wavFile, File(System.getProperty("java.io.tmpdir"), "CalabiYauVoice/audio_tool_preview/$action").also { it.mkdirs() })
                    val loadedWav = readPcmWav(output) ?: error("仅支持 PCM WAV")
                    nextWav = loadedWav
                    Pair(output, inspectDesktopWav(loadedWav))
                }
            }
                .onSuccess { (output, next) ->
                    if (autoSpectrogramTuning) applyAutoSpectrogramTuning(spectrogramZoomPercent.toIntOrNull() ?: 180, next.durationMs)
                    val nextSpectrogram = withContext(Dispatchers.Default) {
                        buildDesktopSpectrogramImage(nextWav ?: error("仅支持 PCM WAV"), currentSpectrogramConfig())
                    }
                    onLog("$action 预览已更新")
                    pushAudioHistory(action, AudioToolInput(output, output, true, nextWav ?: error("仅支持 PCM WAV")), next, nextSpectrogram)
                }
                .onFailure { onLog("$action 失败：${it.message ?: it::class.simpleName}") }
            onBusyChange(false)
        }
    }

    fun exportCurrentAudio() {
        val source = input ?: return
        scope.launch {
            onBusyChange(true)
            runCatchingCancellable {
                withContext(Dispatchers.IO) {
                    val outDir = File(outputPath, "音频工具/导出音频").also { it.mkdirs() }
                    val out = uniqueOutputFile(outDir, source.source.nameWithoutExtension.ifBlank { "audio_preview" }, "wav")
                    source.wavFile.copyTo(out, overwrite = false)
                    out
                }
            }.onSuccess {
                onLog("当前音频已导出：${it.absolutePath}")
                openDirectory(it.parentFile)
            }.onFailure {
                onLog("音频导出失败：${it.message ?: it::class.simpleName}")
            }
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
                Button(onClick = ::exportCurrentAudio, disabled = isBusy || input == null) { Text("导出音频") }
                Text(input?.source?.absolutePath ?: "未选择 WAV/MP3/FLAC", modifier = Modifier.weight(1f), fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            meta?.let {
                Text("${it.channels} 声道 / ${it.sampleRate} Hz / ${it.bitsPerSample} bit / ${formatDuration(it.durationMs)} / 峰值 ${"%.1f".format(it.peakPercent)}%", fontSize = 12.sp)
            }
        }
        ToolSectionCard(title = "操作历史", subtitle = "选择历史节点可回到对应预览，导出会保存当前节点") {
            AudioHistoryTimeline(
                steps = audioHistory.steps,
                currentIndex = audioHistory.currentIndex,
                isBusy = isBusy,
                onUndo = { moveAudioHistory(-1) },
                onRedo = { moveAudioHistory(1) },
                onSelect = { index ->
                    val step = audioHistory.select(index) ?: return@AudioHistoryTimeline
                    setAudioStep(step)
                    onLog("时间轴：${step.label}")
                }
            )
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
                                        check(ImageIO.write(image, "png", out)) { "当前环境不支持 PNG 写出" }
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
                    TextField(value = spectrogramZoomPercent, onValueChange = { if (it.all(Char::isDigit)) setSpectrogramZoomPercent(it) }, header = { Text("缩放 %", fontSize = 12.sp) }, modifier = Modifier.width(90.dp), singleLine = true)
                    ComboBox(header = "配色", items = SpectrogramPaletteOption.entries.map { it.label }, selected = spectrogramPalette.ordinal, onSelectionChange = { i, _ -> setSpectrogramPalette(SpectrogramPaletteOption.entries[i]) })
                    Switcher(
                        checked = autoSpectrogramTuning,
                        onCheckStateChange = {
                            autoSpectrogramTuning = it
                            if (it) applyAutoSpectrogramTuning(spectrogramZoomPercent.toIntOrNull() ?: 180)
                        },
                        textBefore = true,
                        text = "自动调参"
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    TextField(value = spectrogramGainDb, onValueChange = { spectrogramGainDb = it.filter { ch -> ch.isDigit() || ch == '-' || ch == '.' } }, header = { Text("显示增益 dB", fontSize = 12.sp) }, modifier = Modifier.width(120.dp), singleLine = true)
                    TextField(value = spectrogramGamma, onValueChange = { spectrogramGamma = it.filter { ch -> ch.isDigit() || ch == '.' } }, header = { Text("伽马", fontSize = 12.sp) }, modifier = Modifier.width(90.dp), singleLine = true)
                    TextField(value = spectrogramFloorDb, onValueChange = { spectrogramFloorDb = it.filter { ch -> ch.isDigit() || ch == '-' || ch == '.' } }, header = { Text("底噪 dB", fontSize = 12.sp) }, modifier = Modifier.width(100.dp), singleLine = true)
                    Button(onClick = ::regenerateSpectrogram, disabled = isBusy || input == null) { Text("应用显示") }
                }
                SpectrogramPreview(
                    image = image,
                    meta = meta,
                    zoomPercent = spectrogramZoomPercent,
                    onZoomPercentChange = ::setSpectrogramZoomPercent,
                    cutoffHz = spectrogramCutoffHz.toIntOrNull() ?: 0,
                    previewHeight = spectrogramPreviewHeight,
                    onPreviewHeightChange = { spectrogramPreviewHeight = it.coerceIn(120f, 720f) }
                )
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
        ToolSectionCard(title = "修复处理", subtitle = "基础降噪、淡入淡出、DC 偏移和相位工具") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                TextField(value = gateThreshold, onValueChange = { gateThreshold = it.filter { ch -> ch.isDigit() || ch == '.' } }, header = { Text("门限 %", fontSize = 12.sp) }, modifier = Modifier.width(100.dp), singleLine = true)
                TextField(value = gateReductionDb, onValueChange = { gateReductionDb = it.filter { ch -> ch.isDigit() || ch == '.' } }, header = { Text("衰减 dB", fontSize = 12.sp) }, modifier = Modifier.width(110.dp), singleLine = true)
                Button(
                    onClick = { runAudio("降噪门") { f, d -> applyDesktopWavNoiseGate(f, d, gateThreshold.toDoubleOrNull() ?: 2.0, gateReductionDb.toDoubleOrNull() ?: 24.0) } },
                    disabled = isBusy || input == null
                ) { Text("应用门限") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                TextField(value = fadeInMs, onValueChange = { if (it.all(Char::isDigit)) fadeInMs = it }, header = { Text("淡入 ms", fontSize = 12.sp) }, modifier = Modifier.width(100.dp), singleLine = true)
                TextField(value = fadeOutMs, onValueChange = { if (it.all(Char::isDigit)) fadeOutMs = it }, header = { Text("淡出 ms", fontSize = 12.sp) }, modifier = Modifier.width(100.dp), singleLine = true)
                Button(
                    onClick = { runAudio("淡入淡出") { f, d -> applyDesktopWavFade(f, d, fadeInMs.toIntOrNull() ?: 10, fadeOutMs.toIntOrNull() ?: 50) } },
                    disabled = isBusy || input == null
                ) { Text("应用淡化") }
                Button(
                    onClick = { runAudio("DC偏移移除") { f, d -> removeDesktopWavDcOffset(f, d) } },
                    disabled = isBusy || input == null
                ) { Text("移除 DC") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                ComboBox(header = "相位", items = DesktopWavPhaseMode.entries.map { it.label }, selected = phaseMode.ordinal, onSelectionChange = { i, _ -> phaseMode = DesktopWavPhaseMode.entries[i] })
                Button(
                    onClick = { runAudio("相位处理") { f, d -> applyDesktopWavPhase(f, d, phaseMode) } },
                    disabled = isBusy || input == null
                ) { Text("应用相位") }
            }
        }
    }
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
        runAudioDecodeWithTimeout { decodeDesktopAudioToPcmWav(file, wav) }
        val wavData = readPcmWav(wav) ?: error("无法解码此 WAV 文件")
        return AudioToolInput(file, wav, true, wavData)
    }
    tempDir.mkdirs()
    val wav = uniqueOutputFile(tempDir, file.nameWithoutExtension + "_pcm", "wav")
    runAudioDecodeWithTimeout { decodeDesktopAudioToPcmWav(file, wav) }
    val wavData = readPcmWav(wav) ?: error("仅支持 PCM WAV")
    return AudioToolInput(file, wav, true, wavData)
}

private fun runAudioDecodeWithTimeout(block: () -> Unit) {
    val future = assetAudioDecodeExecutor.submit(Callable { block() })
    try {
        future.get(AUDIO_DECODE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    } catch (_: TimeoutException) {
        future.cancel(true)
        error("音频解码超时，请尝试先用音频转换工具转为 WAV")
    }
}

private fun audioDecodeFailureMessage(error: Throwable): String {
    val message = error.message ?: error::class.simpleName.orEmpty()
    return if (message.contains("音频转换工具")) message else "$message。可尝试先用音频转换工具转为 WAV。"
}

private suspend inline fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }
}
