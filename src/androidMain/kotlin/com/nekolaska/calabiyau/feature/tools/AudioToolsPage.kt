package com.nekolaska.calabiyau.feature.tools

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.media.AudioPlayerManager
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private data class AudioProcessOutcome(
    val output: ToolOutput,
    val preview: LoadedWavAudio? = null
)

private data class AudioPreviewState(
    val asset: LoadedAudioAsset,
    val canPreviewWaveform: Boolean
)

@Composable
internal fun AudioToolsPage(
    outputPath: String,
    isBusy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onResult: (ToolOutput) -> Unit,
    onPickDirectoryFromFileManager: (
        initialPath: String?,
        title: String,
        description: String,
        onPicked: (String) -> Unit
    ) -> Unit,
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
    var currentAudio by remember { mutableStateOf<AudioPreviewState?>(null) }
    val undoStack = remember { mutableStateListOf<AudioPreviewState>() }
    val redoStack = remember { mutableStateListOf<AudioPreviewState>() }
    var trimThresholdPercent by remember { mutableStateOf("1.5") }
    var minimumSilenceMs by remember { mutableStateOf("120") }
    var trimMode by remember { mutableStateOf(WavTrimMode.BOTH) }
    var channelMode by remember { mutableStateOf(WavChannelMode.STEREO_TO_MONO) }
    var volumeMode by remember { mutableStateOf(WavVolumeMode.NORMALIZE) }
    var gainPercent by remember { mutableStateOf("120") }
    var targetPeakPercent by remember { mutableStateOf("90") }
    var playheadMs by rememberSaveable { mutableLongStateOf(0L) }
    var viewportStartMs by rememberSaveable { mutableFloatStateOf(0f) }
    var viewportDurationMs by rememberSaveable { mutableFloatStateOf(30_000f) }
    var spectrogramRequestId by remember { mutableLongStateOf(0L) }
    var spectrogramWindowSize by rememberSaveable { mutableStateOf(AppPrefs.audioSpectrogramWindowSize.toString()) }
    var spectrogramHopPercent by rememberSaveable { mutableStateOf(AppPrefs.audioSpectrogramHopPercent.toString()) }
    var spectrogramTimeBins by rememberSaveable { mutableStateOf(AppPrefs.audioSpectrogramTimeBins.toString()) }
    var spectrogramFrequencyBins by rememberSaveable { mutableStateOf(AppPrefs.audioSpectrogramFrequencyBins.toString()) }
    var spectrogramCutoffHz by rememberSaveable { mutableStateOf(AppPrefs.audioSpectrogramCutoffHz.toString()) }
    var spectrogramPalette by rememberSaveable { mutableStateOf(SpectrogramPalette.fromPref(AppPrefs.audioSpectrogramPalette)) }
    val isAudioPlaying by AudioPlayerManager.isPlaying
    val playingSource by AudioPlayerManager.playingSource

    @Suppress("UNUSED_EXPRESSION")
    onPickDirectoryFromFileManager

    fun pushUndoSnapshot(state: AudioPreviewState?) {
        if (state != null) {
            undoStack.add(state)
            if (undoStack.size > 20) {
                undoStack.removeAt(0)
            }
        }
        redoStack.clear()
    }

    fun setPreview(state: AudioPreviewState) {
        currentAudio = state
        playheadMs = 0L
        viewportStartMs = 0f
        val preview = state.asset.wav
        if (preview != null) {
            viewportDurationMs = min(30_000f, max(1_000f, preview.durationMs.toFloat()))
            channelMode = if (preview.data.channels == 1) WavChannelMode.MONO_TO_STEREO else WavChannelMode.STEREO_TO_MONO
        }
    }

    fun currentAudioSource(): String? = currentAudio?.asset?.wav?.file?.absolutePath

    fun currentSpectrogramConfig(): SpectrogramRenderConfig {
        return SpectrogramRenderConfig(
            windowSize = spectrogramWindowSize.toIntOrNull()?.coerceIn(256, 8192) ?: 1024,
            hopRatio = (spectrogramHopPercent.toIntOrNull()?.coerceIn(5, 80) ?: 25) / 100f,
            maxTimeBins = spectrogramTimeBins.toIntOrNull()?.coerceIn(120, 2000) ?: 720,
            maxFrequencyBins = spectrogramFrequencyBins.toIntOrNull()?.coerceIn(64, 1024) ?: 256,
            cutoffFrequencyHz = spectrogramCutoffHz.toIntOrNull()?.coerceIn(0, 96000) ?: 0,
            lowColorArgb = spectrogramPalette.lowColorArgb,
            highColorArgb = spectrogramPalette.highColorArgb
        )
    }

    fun persistSpectrogramPreferences() {
        AppPrefs.audioSpectrogramWindowSize = spectrogramWindowSize.toIntOrNull()?.coerceIn(256, 8192) ?: 1024
        AppPrefs.audioSpectrogramHopPercent = spectrogramHopPercent.toIntOrNull()?.coerceIn(5, 80) ?: 25
        AppPrefs.audioSpectrogramTimeBins = spectrogramTimeBins.toIntOrNull()?.coerceIn(120, 2000) ?: 720
        AppPrefs.audioSpectrogramFrequencyBins = spectrogramFrequencyBins.toIntOrNull()?.coerceIn(64, 1024) ?: 256
        AppPrefs.audioSpectrogramCutoffHz = spectrogramCutoffHz.toIntOrNull()?.coerceIn(0, 96000) ?: 0
        AppPrefs.audioSpectrogramPalette = spectrogramPalette.prefKey
    }

    fun isPreviewPlaying(): Boolean = isAudioPlaying && playingSource == currentAudioSource()

    fun togglePlayback(positionMs: Long = playheadMs) {
        val source = currentAudioSource() ?: return
        if (isPreviewPlaying()) {
            AudioPlayerManager.play(source)
        } else {
            AudioPlayerManager.playFrom(source, positionMs.toInt())
        }
    }

    fun applyPreview(state: AudioPreviewState) {
        pushUndoSnapshot(currentAudio)
        setPreview(state)
    }

    fun undoPreview() {
        if (undoStack.isEmpty()) return
        currentAudio?.let { redoStack.add(it) }
        setPreview(undoStack.removeAt(undoStack.lastIndex))
    }

    fun redoPreview() {
        if (redoStack.isEmpty()) return
        currentAudio?.let { undoStack.add(it) }
        setPreview(redoStack.removeAt(redoStack.lastIndex))
    }

    fun attachSpectrogramPreview(requestId: Long, wav: LoadedWavAudio) {
        if (spectrogramRequestId != requestId) return
        val current = currentAudio ?: return
        if (current.asset.wav?.file != wav.file) return
        currentAudio = current.copy(
            asset = current.asset.copy(
                wav = wav
            )
        )
    }

    fun scheduleSpectrogramPreview(
        wav: LoadedWavAudio,
        config: SpectrogramRenderConfig = currentSpectrogramConfig(),
        force: Boolean = false
    ) {
        if (!force && wav.spectrogramBitmap != null) return
        val requestId = ++spectrogramRequestId
        scope.launch {
            val updated = withContext(Dispatchers.IO) {
                wav.copy(spectrogramBitmap = buildSpectrogramBitmap(wav.data, config))
            }
            attachSpectrogramPreview(requestId, updated)
        }
    }

    fun loadInput(input: PickedInput) {
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    loadAudioAssetForPreview(context, input) ?: error("无法读取音频文件")
                }
            }.onSuccess { asset ->
                AudioPlayerManager.stop()
                applyPreview(AudioPreviewState(asset, asset.wav != null))
                asset.wav?.let { scheduleSpectrogramPreview(it, force = true) }
                showSnack("已导入 ${asset.meta.name}")
            }.onFailure {
                showSnack("导入音频失败：${it.message ?: "未知错误"}")
            }
            onBusyChange(false)
        }
    }

    fun processCurrent(title: String, block: (PickedInput, File) -> AudioProcessOutcome) {
        val audio = currentAudio?.asset?.wav ?: run {
            showSnack("请先导入 WAV 或 MP3 文件")
            return
        }
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    block(audio.input, resolveOutputDirectory(outputPath, title))
                }
            }.onSuccess { outcome ->
                onResult(outcome.output)
                outcome.preview?.let { preview ->
                    AudioPlayerManager.stop()
                    applyPreview(
                        AudioPreviewState(
                            LoadedAudioAsset(
                                input = preview.input,
                                file = preview.file,
                                meta = inspectAudioMeta(context, preview.input) ?: error("无法读取音频元数据"),
                                wav = preview
                            ),
                            canPreviewWaveform = true
                        )
                    )
                    scheduleSpectrogramPreview(preview, force = true)
                }
                showSnack(outcome.output.message)
            }.onFailure {
                showSnack("处理失败：${it.message ?: "未知错误"}")
            }
            onBusyChange(false)
        }
    }

    LaunchedEffect(currentAudio?.asset?.wav?.file?.absolutePath) {
        while (true) {
            val source = currentAudio?.asset?.wav?.file?.absolutePath ?: break
            if (playingSource == source && isAudioPlaying) {
                playheadMs = AudioPlayerManager.getCurrentPosition().toLong()
            }
            delay(120)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { loadInput(PickedInput(uri = it)) }
    }

    val audio = currentAudio
    val audioSourcePath = audio?.asset?.wav?.file?.absolutePath
    val playbackProgress = audio?.asset?.wav?.durationMs?.takeIf { it > 0L }?.let { durationMs ->
        (playheadMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } ?: 0f

    ToolPageColumn {
        Surface(
            shape = smoothCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = smoothCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Icon(
                            Icons.Outlined.AudioFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("音频预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("导入 WAV/MP3 预览波形与频谱", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = smoothCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(
                            text = if (audio == null) "未导入" else "已导入",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                AudioActionButton(
                    text = "导入音频",
                    icon = Icons.Outlined.AudioFile,
                    enabled = !isBusy
                ) {
                    onPickFilesFromFileManager(
                        AppPrefs.savePath,
                        "选择音频文件",
                        "选择 WAV/MP3 音频文件，可在文件管理中选文件，也可改用系统选择器。",
                        false,
                        { audioPicker.launch("audio/*") }
                    ) { paths ->
                        paths.toPickedFileInputs().firstOrNull()?.let(::loadInput)
                    }
                }

                if (audio == null) {
                    EmptyAudioPreview()
                } else {
                    AudioSummary(audio)
                    if (audio.canPreviewWaveform && audio.asset.wav != null) {
                        val wav = audio.asset.wav
                        AudioTimelineViewport(
                            audio = wav,
                            playheadMs = playheadMs,
                            viewportStartMs = viewportStartMs,
                            viewportDurationMs = viewportDurationMs,
                            onPlayheadChange = { newPlayhead ->
                                playheadMs = newPlayhead
                                if (playingSource == audioSourcePath && isAudioPlaying) {
                                    AudioPlayerManager.seekTo(newPlayhead.toInt())
                                }
                            },
                            onPanViewport = { delta ->
                                val maxStart = max(0f, wav.durationMs.toFloat() - viewportDurationMs)
                                viewportStartMs = (viewportStartMs + delta).coerceIn(0f, maxStart)
                            },
                            onZoomViewport = { zoom, focusRatio ->
                                val oldDuration = viewportDurationMs
                                val newDuration = (oldDuration / zoom).coerceIn(500f, max(500f, wav.durationMs.toFloat()))
                                val focusMs = viewportStartMs + oldDuration * focusRatio
                                val maxStart = max(0f, wav.durationMs.toFloat() - newDuration)
                                viewportDurationMs = newDuration
                                viewportStartMs = (focusMs - newDuration * focusRatio).coerceIn(0f, maxStart)
                            }
                        )
                        Surface(
                            shape = smoothCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        FilledIconButton(
                                            onClick = { togglePlayback() },
                                            enabled = !isBusy,
                                            shape = CircleShape,
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                if (isPreviewPlaying()) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = if (isPreviewPlaying()) "正在播放" else "已暂停",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${formatDuration(playheadMs)} / ${formatDuration(wav.durationMs)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    FilledTonalIconButton(
                                        onClick = {
                                            playheadMs = 0L
                                            if (playingSource == audioSourcePath && isAudioPlaying) {
                                                AudioPlayerManager.seekTo(0)
                                            }
                                        },
                                        enabled = !isBusy
                                    ) {
                                        Icon(Icons.Outlined.SkipPrevious, contentDescription = "回到开头")
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { playbackProgress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    strokeCap = StrokeCap.Round,
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                        Text(
                            if (wav.spectrogramBitmap == null) {
                                "频谱图后台生成中 · 拖拽与缩放仍可正常使用"
                            } else {
                                "拖拽平移 · 双指缩放 · 点击定位播放头"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AudioHistoryButtons(
                                isBusy = isBusy,
                                canUndo = undoStack.isNotEmpty(),
                                canRedo = redoStack.isNotEmpty(),
                                onUndo = { undoPreview() },
                                onRedo = { redoPreview() }
                            )
                        }
                    } else {
                        Surface(shape = smoothCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)) {
                            Text(
                                text = "当前文件无法生成波形预览，仅显示元数据。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(14.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AudioHistoryButtons(
                                isBusy = isBusy,
                                canUndo = undoStack.isNotEmpty(),
                                canRedo = redoStack.isNotEmpty(),
                                onUndo = { undoPreview() },
                                onRedo = { redoPreview() }
                            )
                        }
                    }
                }

            }
        }

        ToolCard(
            title = "静音裁剪",
            subtitle = "裁掉音频开头或结尾的静音片段",
            icon = Icons.Outlined.SpaceBar
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = trimThresholdPercent,
                        onValueChange = { trimThresholdPercent = it },
                        label = { Text("静音阈值 %") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = smoothCornerShape(16.dp)
                    )
                    OutlinedTextField(
                        value = minimumSilenceMs,
                        onValueChange = { minimumSilenceMs = it },
                        label = { Text("最短静音 ms") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = smoothCornerShape(16.dp)
                    )
                }
                ModeChips(WavTrimMode.entries, trimMode, { trimMode = it }, Icons.Outlined.SpaceBar) { it.label }
                AudioActionButton("裁剪当前音频", Icons.Outlined.SpaceBar, !isBusy && currentAudio?.asset?.wav != null) {
                    processCurrent("音频工具/WAV静音裁剪") { input, outputDir ->
                        val thresholdRatio = trimThresholdPercent.toDoubleOrNull()?.div(100.0)?.coerceIn(0.001, 0.2) ?: 0.015
                        val minSilence = minimumSilenceMs.toIntOrNull()?.coerceIn(10, 5000) ?: 120
                        val result = trimWavInputSilence(context, input, outputDir, thresholdRatio, minSilence, trimMode)
                        val preview = result?.outputFile?.let { loadWavAudioFileForPreview(it, includeSpectrogram = false) }
                        AudioProcessOutcome(
                            output = ToolOutput(
                                title = "静音裁剪完成",
                                message = result?.let { "已生成 ${it.outputFile.name}，去头 ${it.trimmedStartMs}ms / 去尾 ${it.trimmedEndMs}ms" } ?: "未检测到可裁剪静音",
                                files = result?.let { listOf(it.outputFile) } ?: emptyList(),
                                directory = outputDir
                            ),
                            preview = preview
                        )
                    }
                }
            }
        }

        ToolCard(
            title = "音量处理",
            subtitle = "调整音量或按峰值自动标准化",
            icon = Icons.Outlined.GraphicEq
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ModeChips(WavVolumeMode.entries, volumeMode, { volumeMode = it }, Icons.Outlined.GraphicEq) { it.label }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = gainPercent,
                        onValueChange = { gainPercent = it },
                        label = { Text("音量 %") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = smoothCornerShape(16.dp)
                    )
                    OutlinedTextField(
                        value = targetPeakPercent,
                        onValueChange = { targetPeakPercent = it },
                        label = { Text("目标峰值 %") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = smoothCornerShape(16.dp)
                    )
                }
                AudioActionButton("处理当前音频", Icons.Outlined.GraphicEq, !isBusy && currentAudio?.asset?.wav != null) {
                    processCurrent("音频工具/WAV音量处理") { input, outputDir ->
                        val gain = gainPercent.toIntOrNull()?.coerceIn(10, 400) ?: 120
                        val targetPeak = targetPeakPercent.toIntOrNull()?.coerceIn(10, 100) ?: 90
                        val result = adjustWavVolume(context, input, outputDir, volumeMode, gain, targetPeak)
                        val preview = result?.outputFile?.let { loadWavAudioFileForPreview(it, includeSpectrogram = false) }
                        AudioProcessOutcome(
                            output = ToolOutput(
                                title = "音量处理完成",
                                message = result?.let { "已生成 ${it.outputFile.name}：${it.sourcePeakPercent.format1()}% → ${it.targetPeakPercent.format1()}%" } ?: "没有可处理的音频数据",
                                files = result?.let { listOf(it.outputFile) } ?: emptyList(),
                                directory = outputDir
                            ),
                            preview = preview
                        )
                    }
                }
            }
        }

        ToolCard(
            title = "声道转换",
            subtitle = "在单声道和双声道之间转换",
            icon = Icons.Outlined.SurroundSound
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val channels = currentAudio?.asset?.wav?.data?.channels
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WavChannelMode.entries.forEach { mode ->
                        val supported = channels == null || (channels == 1 && mode == WavChannelMode.MONO_TO_STEREO) || (channels == 2 && mode == WavChannelMode.STEREO_TO_MONO)
                        FilterChip(
                            selected = channelMode == mode,
                            onClick = { if (supported) channelMode = mode },
                            enabled = supported,
                            label = { Text(mode.label) },
                            leadingIcon = if (channelMode == mode) {
                                { Icon(Icons.Outlined.SurroundSound, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
                AudioActionButton("转换当前音频", Icons.Outlined.SurroundSound, !isBusy && currentAudio?.asset?.wav != null) {
                    processCurrent("音频工具/WAV声道转换") { input, outputDir ->
                        val result = convertWavChannels(context, input, outputDir, channelMode)
                        val preview = result?.outputFile?.let { loadWavAudioFileForPreview(it, includeSpectrogram = false) }
                        AudioProcessOutcome(
                            output = ToolOutput(
                                title = "声道转换完成",
                                message = result?.let { "已生成 ${it.outputFile.name}：${it.sourceChannels} 声道 → ${it.targetChannels} 声道" } ?: "当前声道不支持所选转换",
                                files = result?.let { listOf(it.outputFile) } ?: emptyList(),
                                directory = outputDir
                            ),
                            preview = preview
                        )
                    }
                }
            }
        }

        ToolCard(
            title = "频谱图设置",
            subtitle = "调整频谱图精度、频率范围和配色",
            icon = Icons.Outlined.GraphicEq
        ) {
            SpectrogramSettingsPanel(
                windowSize = spectrogramWindowSize,
                onWindowSizeChange = { spectrogramWindowSize = it },
                hopPercent = spectrogramHopPercent,
                onHopPercentChange = { spectrogramHopPercent = it },
                timeBins = spectrogramTimeBins,
                onTimeBinsChange = { spectrogramTimeBins = it },
                frequencyBins = spectrogramFrequencyBins,
                onFrequencyBinsChange = { spectrogramFrequencyBins = it },
                cutoffHz = spectrogramCutoffHz,
                onCutoffHzChange = { spectrogramCutoffHz = it },
                palette = spectrogramPalette,
                onPaletteChange = { spectrogramPalette = it },
                enabled = audio?.asset?.wav != null,
                onApply = {
                    persistSpectrogramPreferences()
                    audio?.asset?.wav?.let { scheduleSpectrogramPreview(it, currentSpectrogramConfig(), force = true) }
                }
            )
        }
    }
}

@Composable
private fun RowScope.AudioHistoryButtons(
    isBusy: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    FilledTonalButton(
        onClick = onUndo,
        enabled = !isBusy && canUndo,
        shape = smoothCornerShape(20.dp)
    ) {
        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null)
        Spacer(Modifier.size(6.dp))
        Text("撤销")
    }
    FilledTonalButton(
        onClick = onRedo,
        enabled = !isBusy && canRedo,
        shape = smoothCornerShape(20.dp)
    ) {
        Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = null)
        Spacer(Modifier.size(6.dp))
        Text("重做")
    }
}

@Composable
private fun <T> ModeChips(
    modes: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: (T) -> String
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                label = { Text(label(mode)) },
                leadingIcon = if (selected == mode) {
                    { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}

@Composable
internal fun ColumnScope.AudioActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(24.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(text)
    }
}

@Composable
private fun SpectrogramSettingsPanel(
    windowSize: String,
    onWindowSizeChange: (String) -> Unit,
    hopPercent: String,
    onHopPercentChange: (String) -> Unit,
    timeBins: String,
    onTimeBinsChange: (String) -> Unit,
    frequencyBins: String,
    onFrequencyBinsChange: (String) -> Unit,
    cutoffHz: String,
    onCutoffHzChange: (String) -> Unit,
    palette: SpectrogramPalette,
    onPaletteChange: (SpectrogramPalette) -> Unit,
    enabled: Boolean,
    onApply: () -> Unit
) {
    Surface(
        shape = smoothCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("频谱图设置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("调整 FFT 窗长、步进和配色，点击重绘后生效", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = onApply, enabled = enabled, shape = smoothCornerShape(20.dp)) {
                    Text("重绘")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = windowSize,
                    onValueChange = onWindowSizeChange,
                    label = { Text("FFT 窗长") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = smoothCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = hopPercent,
                    onValueChange = onHopPercentChange,
                    label = { Text("步进 %") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = smoothCornerShape(16.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = timeBins,
                    onValueChange = onTimeBinsChange,
                    label = { Text("时间桶") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = smoothCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = frequencyBins,
                    onValueChange = onFrequencyBinsChange,
                    label = { Text("频率桶") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = smoothCornerShape(16.dp)
                )
            }

            OutlinedTextField(
                value = cutoffHz,
                onValueChange = onCutoffHzChange,
                label = { Text("截止频率 Hz") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("填 0 表示自动使用当前音频的 Nyquist 频率") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = smoothCornerShape(16.dp)
            )

            Text("颜色方案", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SpectrogramPalette.entries.forEach { option ->
                    FilterChip(
                        selected = palette == option,
                        onClick = { onPaletteChange(option) },
                        label = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = Color(option.highColorArgb), modifier = Modifier.size(10.dp)) {}
                                Surface(shape = CircleShape, color = Color(option.lowColorArgb), modifier = Modifier.size(10.dp)) {}
                                Text(option.label)
                            }
                        },
                        leadingIcon = if (palette == option) {
                            { Icon(Icons.Outlined.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }
    }
}

private enum class SpectrogramPalette(
    val label: String,
    val lowColorArgb: Int,
    val highColorArgb: Int
) {
    Ocean("海洋蓝", 0xFF0F172A.toInt(), 0xFF38BDF8.toInt()),
    Sunset("暮光橙", 0xFF1F2937.toInt(), 0xFFF97316.toInt()),
    Neon("霓虹紫", 0xFF111827.toInt(), 0xFFA78BFA.toInt()),
    Forest("森林绿", 0xFF052E16.toInt(), 0xFF4ADE80.toInt());

    val prefKey: String
        get() = name

    companion object {
        fun fromPref(value: String): SpectrogramPalette = entries.firstOrNull { it.name == value } ?: Ocean
    }
}

@Composable
private fun EmptyAudioPreview() {
    Surface(
        shape = smoothCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AudioFile,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "导入音频后即可预览",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "支持预览 WAV/MP3 的波形图与频谱图\nMP3 的处理结果将默认保存为 WAV 格式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AudioSummary(audio: AudioPreviewState) {
    Surface(shape = smoothCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(audio.asset.meta.name, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(
                    formatDuration(audio.asset.meta.durationMs),
                    audio.asset.meta.sampleRate?.let { "${it}Hz" },
                    audio.asset.meta.bitrate?.let { "${it}bps" },
                    audio.asset.meta.artist,
                    audio.asset.meta.mimeType
                ).joinToString(" · ").ifBlank { "无元数据" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            audio.asset.wav?.let { wav ->
                Text(
                    "WAV：${wav.data.sampleRate}Hz · ${wav.data.bitsPerSample}bit · ${wav.data.channels} 声道 · 峰值 ${wav.peakPercent.format1()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AudioTimelineViewport(
    audio: LoadedWavAudio,
    playheadMs: Long,
    viewportStartMs: Float,
    viewportDurationMs: Float,
    onPlayheadChange: (Long) -> Unit,
    onPanViewport: (Float) -> Unit,
    onZoomViewport: (Float, Float) -> Unit
) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHighest
    val waveformColor = MaterialTheme.colorScheme.primary
    val rightWaveformColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val playheadColor = MaterialTheme.colorScheme.error
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val latestDuration by rememberUpdatedState(audio.durationMs)
    val latestViewportStart by rememberUpdatedState(viewportStartMs)
    val latestViewportDuration by rememberUpdatedState(viewportDurationMs)
    val latestOnPlayheadChange by rememberUpdatedState(onPlayheadChange)
    val latestOnPanViewport by rememberUpdatedState(onPanViewport)
    val latestOnZoomViewport by rememberUpdatedState(onZoomViewport)
    val gridStep = remember(viewportDurationMs) { chooseAudioGridStepMs(viewportDurationMs.toLong()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(bg, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width <= 0f) return@detectTapGestures
                    val ratio = (offset.x / size.width.toDouble()).coerceIn(0.0, 1.0)
                    val tappedMs = latestViewportStart.toDouble() + latestViewportDuration.toDouble() * ratio
                    latestOnPlayheadChange(tappedMs.roundToLong().coerceIn(0L, max(1L, latestDuration)))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    if (size.width <= 0f) return@detectDragGestures
                    latestOnPanViewport(-(dragAmount.x / size.width) * latestViewportDuration)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
                    if (size.width <= 0f) return@detectTransformGestures
                    if (abs(zoom - 1f) >= 0.001f) {
                        latestOnZoomViewport(zoom, (centroid.x / size.width).coerceIn(0f, 1f))
                    } else if (abs(pan.x) >= 0.5f) {
                        latestOnPanViewport(-(pan.x / size.width) * latestViewportDuration)
                    }
                }
            }
            .padding(10.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val start = viewportStartMs
            val end = viewportStartMs + viewportDurationMs
            val labelHeight = 18f
            val waveformTop = labelHeight + 8f
            val waveformHeight = size.height * 0.38f
            val specTop = waveformTop + waveformHeight + 14f
            val specHeight = size.height - specTop - 4f

            drawRoundRect(Color.Gray.copy(alpha = 0.22f), cornerRadius = CornerRadius(12f, 12f), style = Stroke(2f))
            drawTimeGrid(start, end, viewportDurationMs, gridStep, gridColor)
            drawWaveformTracks(audio, start, viewportDurationMs, waveformTop, waveformHeight, waveformColor, rightWaveformColor)
            drawSpectrogramBitmap(audio, start, viewportDurationMs, specTop, specHeight)
            drawLine(
                color = textColor.copy(alpha = 0.45f),
                start = Offset(0f, specTop - 7f),
                end = Offset(size.width, specTop - 7f),
                strokeWidth = 1f
            )
            if (playheadMs.toFloat() in start..end) {
                val x = ((playheadMs - start) / viewportDurationMs) * size.width
                drawLine(playheadColor, Offset(x, 0f), Offset(x, size.height), 2.4f)
                drawCircle(playheadColor, radius = 6.dp.toPx(), center = Offset(x, 6.dp.toPx()))
                drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(x, 6.dp.toPx()))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTimeGrid(
    start: Float,
    end: Float,
    duration: Float,
    gridStep: Long,
    gridColor: Color
) {
    var t = (start.toLong() / gridStep) * gridStep
    if (t < start.toLong()) t += gridStep
    while (t <= end.toLong()) {
        val x = ((t - start) / duration) * size.width
        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), if ((t / gridStep) % 5L == 0L) 1.5f else 1f)
        t += gridStep
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveformTracks(
    audio: LoadedWavAudio,
    startMs: Float,
    durationMs: Float,
    top: Float,
    height: Float,
    leftColor: Color,
    rightColor: Color
) {
    val channels = audio.waveform.size
    val trackHeight = height / channels.coerceAtLeast(1)
    audio.waveform.forEachIndexed { channel, samples ->
        if (samples.isEmpty()) return@forEachIndexed
        val centerY = top + channel * trackHeight + trackHeight / 2f
        val ampScale = trackHeight * 0.42f
        val from = ((startMs / audio.durationMs) * samples.size).toInt().coerceIn(0, samples.lastIndex)
        val to = (((startMs + durationMs) / audio.durationMs) * samples.size).toInt().coerceIn(from + 1, samples.size)
        val count = max(1, to - from)
        var previous: Offset? = null
        for (i in from until to) {
            val x = ((i - from).toFloat() / count) * size.width
            val y = centerY - samples[i] * ampScale
            previous?.let { drawLine(if (channel == 0) leftColor else rightColor, it, Offset(x, y), 1.8f) }
            drawLine(if (channel == 0) leftColor else rightColor, Offset(x, centerY), Offset(x, centerY + samples[i] * ampScale), 1.2f)
            previous = Offset(x, y)
        }
        drawLine(Color.Gray.copy(alpha = 0.35f), Offset(0f, centerY), Offset(size.width, centerY), 1f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpectrogramBitmap(
    audio: LoadedWavAudio,
    startMs: Float,
    durationMs: Float,
    top: Float,
    height: Float
) {
    val bitmap = audio.spectrogramBitmap
    if (bitmap == null) {
        drawRoundRect(
            color = Color.Gray.copy(alpha = 0.12f),
            topLeft = Offset(0f, top),
            size = Size(size.width, height),
            cornerRadius = CornerRadius(12f, 12f)
        )
        return
    }

    val image = bitmap.asImageBitmap()
    val sourceWidth = max(1, (bitmap.width * (durationMs / audio.durationMs)).roundToInt())
    val sourceX = (bitmap.width * (startMs / audio.durationMs)).roundToInt().coerceIn(0, max(0, bitmap.width - sourceWidth))
    drawImage(
        image = image,
        srcOffset = IntOffset(sourceX, 0),
        srcSize = IntSize(sourceWidth, bitmap.height),
        dstOffset = IntOffset(0, top.toInt()),
        dstSize = IntSize(size.width.toInt(), height.toInt()),
        filterQuality = FilterQuality.Low
    )
}

private fun chooseAudioGridStepMs(viewportDurationMs: Long): Long = when {
    viewportDurationMs <= 5_000L -> 100L
    viewportDurationMs <= 10_000L -> 200L
    viewportDurationMs <= 20_000L -> 500L
    viewportDurationMs <= 60_000L -> 1_000L
    viewportDurationMs <= 180_000L -> 2_000L
    else -> 5_000L
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private fun Double.format1(): String = String.format(Locale.getDefault(), "%.1f", this)
