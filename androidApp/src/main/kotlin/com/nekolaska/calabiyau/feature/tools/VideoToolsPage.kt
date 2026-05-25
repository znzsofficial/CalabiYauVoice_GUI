package com.nekolaska.calabiyau.feature.tools

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun VideoToolsPage(
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
    var biliVideoInput by remember { mutableStateOf<PickedInput?>(null) }
    var biliAudioInput by remember { mutableStateOf<PickedInput?>(null) }
    var biliVideoName by remember { mutableStateOf<String?>(null) }
    var biliAudioName by remember { mutableStateOf<String?>(null) }
    var splitInput by remember { mutableStateOf<PickedInput?>(null) }
    var splitName by remember { mutableStateOf<String?>(null) }
    var frameInput by remember { mutableStateOf<PickedInput?>(null) }
    var frameName by remember { mutableStateOf<String?>(null) }
    var frameDurationMs by remember { mutableLongStateOf(0L) }
    var framePositionMs by remember { mutableLongStateOf(0L) }
    var framePreview by remember { mutableStateOf<Bitmap?>(null) }
    var framePreviewJob by remember { mutableStateOf<Job?>(null) }

    val biliVideoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            biliVideoInput = PickedInput(uri = it)
            biliVideoName = context.queryDisplayName(it) ?: "video.m4s"
        }
    }
    val biliAudioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            biliAudioInput = PickedInput(uri = it)
            biliAudioName = context.queryDisplayName(it) ?: "audio.m4s"
        }
    }
    val splitPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            splitInput = PickedInput(uri = it)
            splitName = context.queryDisplayName(it) ?: "video.mp4"
        }
    }
    val framePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            frameInput = PickedInput(uri = it)
            frameName = context.queryDisplayName(it) ?: "video.mp4"
            frameDurationMs = readVideoDurationMs(context, PickedInput(uri = it))
            framePositionMs = 0L
        }
    }

    fun updateFramePreview(input: PickedInput, positionMs: Long, debounceMs: Long = 0L) {
        framePreviewJob?.cancel()
        framePreviewJob = scope.launch {
            if (debounceMs > 0L) delay(debounceMs.milliseconds)
            val bitmap = withContext(Dispatchers.IO) { extractVideoFrameBitmap(context, input, positionMs) }
            framePreview?.takeIf { it !== bitmap && !it.isRecycled }?.recycle()
            framePreview = bitmap
        }
    }

    LaunchedEffect(frameInput) {
        frameInput?.let { updateFramePreview(it, framePositionMs) }
    }

    DisposableEffect(Unit) {
        onDispose {
            framePreviewJob?.cancel()
            framePreview?.takeIf { !it.isRecycled }?.recycle()
        }
    }

    fun runBilibiliMux() {
        val video = biliVideoInput ?: run {
            showSnack("请先选择 video.m4s")
            return
        }
        val audioTrack = biliAudioInput ?: run {
            showSnack("请先选择 audio.m4s")
            return
        }
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "视频工具/B站缓存音画合成")
                    val result = muxBilibiliM4sCache(context, video, audioTrack, outputDir)
                    scanMediaLibrary(context, listOf(result.outputFile))
                    ToolOutput(
                        title = "B站缓存合成完成",
                        message = "已生成 ${result.outputFile.name}",
                        files = listOf(result.outputFile),
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
            }.onFailure {
                showSnack("合成失败：${it.message ?: "未知错误"}")
            }
            onBusyChange(false)
        }
    }

    fun runMediaSplit() {
        val input = splitInput ?: run {
            showSnack("请先选择要拆分的视频")
            return
        }
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "视频工具/音视频拆分")
                    val result = splitMediaTracks(context, input, outputDir)
                    val files = listOfNotNull(result.videoFile, result.audioFile)
                    scanMediaLibrary(context, files)
                    ToolOutput(
                        title = "音视频拆分完成",
                        message = "已生成 ${files.size} 个文件：视频轨 ${result.videoSamples} 帧，音频轨 ${result.audioSamples} 帧",
                        files = files,
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
            }.onFailure {
                showSnack("拆分失败：${it.message ?: "未知错误"}")
            }
            onBusyChange(false)
        }
    }

    fun runFrameExport() {
        val input = frameInput ?: run {
            showSnack("请先选择视频")
            return
        }
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "视频工具/帧画面提取")
                    val sourceName = frameName?.substringBeforeLast('.') ?: input.file?.nameWithoutExtension ?: "video"
                    val target = buildUniqueFile(outputDir, "${sanitizeFileName(sourceName)}_${framePositionMs}ms", "png")
                    val bitmap = extractVideoFrameBitmap(context, input, framePositionMs)
                        ?: throw IllegalStateException("无法读取该时间点画面")
                    FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    scanMediaLibrary(context, listOf(target))
                    ToolOutput(
                        title = "帧画面提取完成",
                        message = "已导出 ${target.name}",
                        files = listOf(target),
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
            }.onFailure {
                showSnack("帧画面导出失败：${it.message ?: "未知错误"}")
            }
            onBusyChange(false)
        }
    }

    ToolPageColumn {
        ToolCard(
            title = "提取视频帧",
            subtitle = "从视频中截取关键画面，用于封面、缩略图或素材留存",
            icon = Icons.Outlined.Image,
            expandedByDefault = true
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VideoPickedFilePanel(label = "源文件", name = frameName ?: "未选择视频文件")
                AudioActionButton(
                    text = frameName?.let { "重新选择视频" } ?: "选择视频文件",
                    icon = Icons.Outlined.VideoFile,
                    enabled = !isBusy
                ) {
                    onPickFilesFromFileManager(
                        AppPrefs.savePath,
                        "选择要提取帧的视频",
                        "选择 MP4/WebM/M4S 等常见视频文件，可在文件管理中选文件，也可改用系统选择器。",
                        false,
                        { framePicker.launch("video/*") }
                    ) { paths ->
                        paths.firstOrNull()?.let {
                            val input = PickedInput(file = File(it))
                            frameInput = input
                            frameName = File(it).name
                            frameDurationMs = readVideoDurationMs(context, input)
                            framePositionMs = 0L
                        }
                    }
                }
                frameInput?.let { selected ->
                    Text(
                        text = "当前位置：${formatVideoFrameTime(framePositionMs)} / ${formatVideoFrameTime(frameDurationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = framePositionMs.toFloat(),
                        onValueChange = {
                            framePositionMs = it.toLong().coerceIn(0L, frameDurationMs.coerceAtLeast(0L))
                            updateFramePreview(selected, framePositionMs, debounceMs = 120L)
                        },
                        valueRange = 0f..frameDurationMs.coerceAtLeast(1L).toFloat(),
                        enabled = !isBusy && frameDurationMs > 0L
                    )
                    Surface(shape = smoothCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                            val preview = framePreview
                            if (preview != null && !preview.isRecycled) {
                                androidx.compose.foundation.Image(
                                    bitmap = preview.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    "正在读取预览帧...",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    FilledTonalButton(
                        onClick = { runFrameExport() },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = smoothCornerShape(24.dp)
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("导出当前画面")
                    }
                }
            }
        }

        ToolCard(
            title = "B站缓存音画合成",
            subtitle = "将 Bilibili 缓存中的视频轨和音频轨合成为可播放文件",
            icon = Icons.Outlined.VideoFile,
            expandedByDefault = false
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VideoPickedFilePanel(label = "视频轨", name = biliVideoName ?: "未选择 video.m4s")
                AudioActionButton(
                    text = biliVideoName?.let { "重新选择 video.m4s" } ?: "选择 video.m4s",
                    icon = Icons.Outlined.VideoFile,
                    enabled = !isBusy
                ) {
                    onPickFilesFromFileManager(
                        AppPrefs.savePath,
                        "选择 video.m4s",
                        "选择 Bilibili 缓存目录中的 video.m4s，可在文件管理中选文件，也可改用系统选择器。",
                        false,
                        { biliVideoPicker.launch("*/*") }
                    ) { paths ->
                        paths.firstOrNull()?.let {
                            biliVideoInput = PickedInput(file = File(it))
                            biliVideoName = File(it).name
                        }
                    }
                }

                VideoPickedFilePanel(label = "音频轨", name = biliAudioName ?: "未选择 audio.m4s")
                AudioActionButton(
                    text = biliAudioName?.let { "重新选择 audio.m4s" } ?: "选择 audio.m4s",
                    icon = Icons.Outlined.AudioFile,
                    enabled = !isBusy
                ) {
                    onPickFilesFromFileManager(
                        AppPrefs.savePath,
                        "选择 audio.m4s",
                        "选择 Bilibili 缓存目录中的 audio.m4s，可在文件管理中选文件，也可改用系统选择器。",
                        false,
                        { biliAudioPicker.launch("*/*") }
                    ) { paths ->
                        paths.firstOrNull()?.let {
                            biliAudioInput = PickedInput(file = File(it))
                            biliAudioName = File(it).name
                        }
                    }
                }

                Spacer(Modifier.size(4.dp))
                AudioActionButton(
                    text = "开始合成",
                    icon = Icons.Outlined.VideoFile,
                    enabled = !isBusy && biliVideoInput != null && biliAudioInput != null,
                    onClick = { runBilibiliMux() }
                )
            }
        }

        ToolCard(
            title = "音视频拆分",
            subtitle = "从视频文件中提取独立的视频轨和音频轨",
            icon = Icons.Outlined.VideoFile
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VideoPickedFilePanel(label = "源文件", name = splitName ?: "未选择视频文件")
                AudioActionButton(
                    text = splitName?.let { "重新选择视频" } ?: "选择视频文件",
                    icon = Icons.Outlined.VideoFile,
                    enabled = !isBusy
                ) {
                    onPickFilesFromFileManager(
                        AppPrefs.savePath,
                        "选择要拆分的视频",
                        "选择 MP4/M4S/WebM 等常见音视频文件，可在文件管理中选文件，也可改用系统选择器。",
                        false,
                        { splitPicker.launch("*/*") }
                    ) { paths ->
                        paths.firstOrNull()?.let {
                            splitInput = PickedInput(file = File(it))
                            splitName = File(it).name
                        }
                    }
                }
                AudioActionButton(
                    text = "开始拆分",
                    icon = Icons.Outlined.VideoFile,
                    enabled = !isBusy && splitInput != null,
                    onClick = { runMediaSplit() }
                )
            }
        }
    }
}

private fun readVideoDurationMs(context: Context, input: PickedInput): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, input)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Throwable) {
        0L
    } finally {
        retriever.release()
    }
}

private fun extractVideoFrameBitmap(context: Context, input: PickedInput, positionMs: Long): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, input)
        retriever.getFrameAtTime(positionMs.coerceAtLeast(0L) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
    } catch (_: Throwable) {
        null
    } finally {
        retriever.release()
    }
}

private fun MediaMetadataRetriever.setDataSource(context: Context, input: PickedInput) {
    when {
        input.file != null -> setDataSource(input.file.absolutePath)
        input.uri != null -> setDataSource(context, input.uri)
        else -> throw IllegalArgumentException("缺少输入文件")
    }
}

private fun formatVideoFrameTime(durationMs: Long): String {
    val safe = durationMs.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val millis = safe % 1000L
    return String.format(Locale.getDefault(), "%02d:%02d.%03d", minutes, seconds, millis)
}

@Composable
private fun VideoPickedFilePanel(label: String, name: String) {
    Surface(
        shape = smoothCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
