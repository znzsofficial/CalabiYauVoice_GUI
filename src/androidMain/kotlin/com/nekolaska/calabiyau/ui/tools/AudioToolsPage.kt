package com.nekolaska.calabiyau.ui.tools

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.data.AppPrefs
import com.nekolaska.calabiyau.ui.shared.rememberSnackbarLauncher
import com.nekolaska.calabiyau.ui.shared.smoothCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private data class AudioInfoItem(
    val name: String,
    val durationMs: Long,
    val mimeType: String,
    val artist: String?,
    val sampleRate: String?,
    val bitrate: String?,
    val size: Long
)

private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "m4a", "flac", "ogg", "aac")

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
    val audioInfos = remember { mutableStateListOf<AudioInfoItem>() }
    var directoryPath by rememberSaveable { mutableStateOf(AppPrefs.savePath) }
    var prefix by rememberSaveable { mutableStateOf("audio_") }

    fun processAudioInputs(inputs: List<PickedInput>) {
        if (inputs.isEmpty()) return
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val infos = inputs.mapNotNull { readAudioInfo(context, it) }
                    audioInfos.clear()
                    audioInfos.addAll(infos)
                    ToolOutput(
                        title = "音频信息已读取",
                        message = "已分析 ${infos.size} 个音频文件",
                        directory = resolveOutputDirectory(outputPath, "音频工具")
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
            }.onFailure {
                showSnack("读取音频信息失败")
            }
            onBusyChange(false)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        processAudioInputs(uris.toPickedUriInputs())
    }

    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { getPathFromUri(it)?.let { path -> directoryPath = path } }
    }

    ToolPageColumn {
        ToolCard(
            title = "查看音频信息",
            subtitle = "查看音频时长、大小与元数据",
            icon = Icons.Outlined.Info
        ) {
            FilledTonalButton(onClick = {
                onPickFilesFromFileManager(
                    directoryPath,
                    "选择音频文件",
                    "优先在文件管理中选择音频文件，也可改用系统选择器。",
                    true,
                    { audioPicker.launch("audio/*") }
                ) { paths ->
                    processAudioInputs(paths.toPickedFileInputs())
                }
            }, enabled = !isBusy, shape = smoothCornerShape(24.dp)) {
                Icon(Icons.Outlined.AudioFile, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("导入并分析音频")
            }
            if (audioInfos.isNotEmpty()) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            onBusyChange(true)
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    val outputDir = resolveOutputDirectory(outputPath, "音频工具")
                                    val report = exportAudioInfoCsv(outputDir, audioInfos)
                                    ToolOutput(
                                        title = "音频报告已导出",
                                        message = "已导出 ${report.name}",
                                        files = listOf(report),
                                        directory = outputDir
                                    )
                                }
                            }.onSuccess {
                                onResult(it)
                                showSnack(it.message)
                            }.onFailure {
                                showSnack("导出音频报告失败：${it.message ?: "未知错误"}")
                            }
                            onBusyChange(false)
                        }
                    },
                    enabled = !isBusy,
                    shape = smoothCornerShape(24.dp)
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("导出CSV报告")
                }
            }
            if (audioInfos.isNotEmpty()) {
                HorizontalDivider()
                audioInfos.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${formatDuration(item.durationMs)} · ${formatFileSize(item.size)} · ${item.mimeType}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            listOfNotNull(item.artist, item.sampleRate?.let { "${it}Hz" }, item.bitrate?.let { "${it}bps" }).joinToString(" · ").ifBlank { "无更多元数据" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }

        ToolCard(
            title = "批量重命名音频",
            subtitle = "按前缀和序号批量重命名音频",
            icon = Icons.Outlined.Transform
        ) {
            OutlinedTextField(
                value = prefix,
                onValueChange = { prefix = it },
                label = { Text("文件名前缀") },
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(16.dp)
            )
            CurrentPathPanel(directoryPath)
            DirectorySelectionActions(
                onPickInFileManager = {
                    onPickDirectoryFromFileManager(
                        directoryPath,
                        "选择音频目录",
                        "在文件管理中浏览并确认目录，或使用系统选择器。"
                    ) { pickedPath ->
                        directoryPath = pickedPath
                    }
                },
                onPickInSystem = { dirPicker.launch(null) }
            )
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        onBusyChange(true)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val dir = File(directoryPath)
                                val renamed = batchRenameByPrefix(dir, prefix) { it.extension.lowercase() in AUDIO_EXTENSIONS }
                                ToolOutput(
                                    title = "音频批量重命名完成",
                                    message = "已处理 ${renamed.size} 个音频文件",
                                    files = renamed,
                                    directory = dir
                                )
                            }
                        }.onSuccess {
                            onResult(it)
                            showSnack(it.message)
                        }.onFailure {
                            showSnack("音频重命名失败")
                        }
                        onBusyChange(false)
                    }
                },
                enabled = !isBusy,
                shape = smoothCornerShape(24.dp)
            ) {
                Icon(Icons.Outlined.Transform, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("执行音频重命名")
            }
        }
    }
}

private fun readAudioInfo(context: Context, input: PickedInput): AudioInfoItem? {
    val retriever = MediaMetadataRetriever()
    return runCatching {
        input.file?.let { file ->
            retriever.setDataSource(file.absolutePath)
        } ?: input.uri?.let { uri ->
            retriever.setDataSource(context, uri)
        } ?: return null
        val name = input.file?.name ?: input.uri?.let(context::queryDisplayName) ?: "audio"
        val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "audio/*"
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
        } else {
            null
        }
        val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
        val size = input.file?.length() ?: input.uri?.let { uri ->
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.statSize
            } ?: 0L
        } ?: 0L
        AudioInfoItem(name, duration, mime, artist, sampleRate, bitrate, size)
    }.getOrNull().also {
        runCatching { retriever.release() }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private fun exportAudioInfoCsv(outputDir: File, infos: List<AudioInfoItem>): File {
    val target = buildUniqueFile(outputDir, "audio_info_${System.currentTimeMillis()}", "csv")
    target.writeText(
        buildString {
            appendLine("name,duration,mime,artist,sampleRate,bitrate,size")
            infos.forEach { info ->
                appendLine(
                    listOf(
                        info.name,
                        formatDuration(info.durationMs),
                        info.mimeType,
                        info.artist.orEmpty(),
                        info.sampleRate.orEmpty(),
                        info.bitrate.orEmpty(),
                        info.size.toString()
                    ).joinToString(",") { value -> "\"${value.replace("\"", "\"\"")}\"" }
                )
            }
        }
    )
    return target
}

private fun batchRenameByPrefix(directory: File, prefix: String, predicate: (File) -> Boolean): List<File> {
    if (!directory.exists() || !directory.isDirectory) return emptyList()
    val files = directory.listFiles()?.filter(predicate)?.sortedBy { it.name.lowercase() } ?: return emptyList()
    val renamed = mutableListOf<File>()
    files.forEachIndexed { index, file ->
        val ext = file.extension.takeIf { it.isNotBlank() }?.let { ".${it}" } ?: ""
        val target = File(directory, "${sanitizeFileName(prefix)}${(index + 1).toString().padStart(3, '0')}$ext")
        if (target.absolutePath != file.absolutePath && !target.exists() && file.renameTo(target)) {
            renamed += target
        }
    }
    return renamed
}
