package com.nekolaska.calabiyau.feature.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    ToolPageColumn {
        ToolCard(
            title = "B站缓存音画合成",
            subtitle = "将 Bilibili 缓存音画无转码合成为 MP4",
            icon = Icons.Outlined.VideoFile,
            expandedByDefault = true
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
                    text = "合成为 MP4",
                    icon = Icons.Outlined.VideoFile,
                    enabled = !isBusy && biliVideoInput != null && biliAudioInput != null,
                    onClick = { runBilibiliMux() }
                )
            }
        }

        ToolCard(
            title = "音视频拆分",
            subtitle = "从视频中无转码分离音视频轨道",
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
                        "选择 MP4/M4S/WebM 等可由系统识别的音视频容器，可在文件管理中选文件，也可改用系统选择器。",
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
                    text = "拆分音视频",
                    icon = Icons.Outlined.VideoFile,
                    enabled = !isBusy && splitInput != null,
                    onClick = { runMediaSplit() }
                )
            }
        }
    }
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
