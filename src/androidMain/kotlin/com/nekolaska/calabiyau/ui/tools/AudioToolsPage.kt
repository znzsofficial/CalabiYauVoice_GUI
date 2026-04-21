package com.nekolaska.calabiyau.ui.tools

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    val size: Long,
    val issueFlags: List<String>,
    val silenceHint: String,
    val volumeHint: String,
    val channelHint: String,
    val analysisSummary: String
) {
    val hasIssues: Boolean get() = issueFlags.isNotEmpty()
}

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
    var trimThresholdPercent by remember { mutableStateOf("1.5") }
    var minimumSilenceMs by remember { mutableStateOf("120") }
    var trimMode by remember { mutableStateOf(WavTrimMode.BOTH) }
    var channelMode by remember { mutableStateOf(WavChannelMode.STEREO_TO_MONO) }
    var volumeMode by remember { mutableStateOf(WavVolumeMode.NORMALIZE) }
    var gainPercent by remember { mutableStateOf("120") }
    var targetPeakPercent by remember { mutableStateOf("90") }

    fun analyzeInputs(inputs: List<PickedInput>, successTitle: String, successMessage: (Int) -> String) {
        if (inputs.isEmpty()) return
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val infos = inputs.mapNotNull { readAudioInfo(context, it) }
                    audioInfos.clear()
                    audioInfos.addAll(infos)
                    ToolOutput(
                        title = successTitle,
                        message = successMessage(infos.size),
                        directory = resolveOutputDirectory(outputPath, "音频工具")
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
            }.onFailure {
                showSnack("音频分析失败：${it.message ?: "未知错误"}")
            }
            onBusyChange(false)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        analyzeInputs(uris.toPickedUriInputs(), "音频信息已读取") { "已分析 $it 个文件" }
    }

    ToolPageColumn {
        ToolCard(
            title = "音频信息",
            subtitle = "查看基础信息",
            icon = Icons.Outlined.Info,
            expandedByDefault = true
        ) {
            AudioActionButton(
                text = "导入音频",
                icon = Icons.Outlined.AudioFile,
                enabled = !isBusy
            ) {
                onPickFilesFromFileManager(
                    AppPrefs.savePath,
                    "选择音频文件",
                    "导入音频文件。",
                    true,
                    { audioPicker.launch("audio/*") }
                ) { paths ->
                    analyzeInputs(paths.toPickedFileInputs(), "音频信息已读取") { "已分析 $it 个文件" }
                }
            }
            if (audioInfos.isNotEmpty()) {
                AudioActionButton(
                    text = "导出 CSV",
                    icon = Icons.Outlined.SaveAlt,
                    enabled = !isBusy
                ) {
                    scope.launch {
                        onBusyChange(true)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val outputDir = resolveOutputDirectory(outputPath, "音频工具")
                                val report = exportAudioInfoCsv(outputDir, audioInfos)
                                ToolOutput(
                                    title = "音频报告已导出",
                                    message = report.name,
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
                }
                AudioInfoList(audioInfos)
            }
        }

        ToolCard(
            title = "异常检测",
            subtitle = "检查异常文件",
            icon = Icons.Outlined.ReportProblem
        ) {
            AnalysisCardBody(
                icon = Icons.Outlined.ReportProblem,
                buttonText = "检测",
                enabled = !isBusy,
                summary = when {
                    audioInfos.isEmpty() -> "导入音频后显示结果。"
                    audioInfos.none { it.hasIssues } -> "未发现明显异常。"
                    else -> "发现 ${audioInfos.count { it.hasIssues }} 个疑似异常音频。"
                }
            ) {
                onPickFilesFromFileManager(
                    AppPrefs.savePath,
                    "选择音频文件",
                    "导入音频文件。",
                    true,
                    { audioPicker.launch("audio/*") }
                ) { paths ->
                    analyzeInputs(paths.toPickedFileInputs(), "异常检测完成") { "已检测 $it 个文件" }
                }
            }
            if (audioInfos.isNotEmpty()) {
                HorizontalDivider()
                audioInfos.filter { it.hasIssues }.ifEmpty { audioInfos.take(3) }.forEach { item ->
                    AudioIssueRow(item)
                }
            }
        }

        ToolCard(
            title = "静音裁剪（WAV）",
            subtitle = "分析并裁剪头尾静音",
            icon = Icons.Outlined.SpaceBar
        ) {
            AnalysisCardBody(
                icon = Icons.Outlined.SpaceBar,
                buttonText = "分析",
                enabled = !isBusy,
                summary = if (audioInfos.isEmpty()) {
                    "导入音频后显示结果。"
                } else {
                    audioInfos.joinToString("\n") { "${it.name}：${it.silenceHint}" }
                }
            ) {
                onPickFilesFromFileManager(
                    AppPrefs.savePath,
                    "选择音频文件",
                    "导入音频文件。",
                    true,
                    { audioPicker.launch("audio/*") }
                ) { paths ->
                    analyzeInputs(paths.toPickedFileInputs(), "静音分析完成") { "已分析 $it 个文件" }
                }
            }
            AudioActionButton(
                text = "裁剪 WAV",
                icon = Icons.Outlined.SpaceBar,
                enabled = !isBusy
            ) {
                onPickFilesFromFileManager(
                    AppPrefs.savePath,
                    "选择 WAV 文件",
                    "选择要裁剪的 WAV 文件。",
                    true,
                    { audioPicker.launch("audio/wav") }
                ) { paths ->
                    val inputs = paths.toPickedFileInputs()
                    if (inputs.isEmpty()) return@onPickFilesFromFileManager
                    scope.launch {
                        onBusyChange(true)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val thresholdRatio = trimThresholdPercent.toDoubleOrNull()?.div(100.0)
                                    ?.coerceIn(0.001, 0.2) ?: 0.015
                                val minSilence = minimumSilenceMs.toIntOrNull()?.coerceIn(10, 5000) ?: 120
                                val outputDir = resolveOutputDirectory(outputPath, "音频工具/WAV静音裁剪")
                                val results = inputs.mapNotNull {
                                    trimWavInputSilence(
                                        context = context,
                                        input = it,
                                        outputDir = outputDir,
                                        thresholdRatio = thresholdRatio,
                                        minSilenceMs = minSilence,
                                        trimMode = trimMode
                                    )
                                }
                                val summary = results.take(3).joinToString("；") {
                                    "${it.sourceName} 去头 ${it.trimmedStartMs}ms / 去尾 ${it.trimmedEndMs}ms"
                                }
                                ToolOutput(
                                    title = "WAV 静音裁剪完成",
                                    message = if (results.isEmpty()) {
                                        "没有可裁剪的 WAV 文件"
                                    } else {
                                        "已生成 ${results.size} 个裁剪结果${if (summary.isNotBlank()) "：$summary" else ""}"
                                    },
                                    files = results.map { it.outputFile },
                                    directory = outputDir
                                )
                            }
                        }.onSuccess {
                            onResult(it)
                            showSnack(it.message)
                        }.onFailure {
                            showSnack("WAV 静音裁剪失败：${it.message ?: "未知错误"}")
                        }
                        onBusyChange(false)
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = trimThresholdPercent,
                    onValueChange = { trimThresholdPercent = it },
                    label = { Text("静音阈值 %") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = smoothCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = minimumSilenceMs,
                    onValueChange = { minimumSilenceMs = it },
                    label = { Text("最短静音 ms") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = smoothCornerShape(16.dp)
                )
            }
            Text(
                "阈值建议 1.0%~3.0%，静音 80~200ms。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WavTrimMode.entries.forEach { mode ->
                    AssistChip(
                        onClick = { trimMode = mode },
                        label = { Text(mode.label) },
                        leadingIcon = if (trimMode == mode) {
                            { Icon(Icons.Outlined.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
            Surface(
                shape = smoothCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            ) {
                Text(
                    text = "模式=${trimMode.label} · 阈值=${trimThresholdPercent.ifBlank { "1.5" }}% · 静音=${minimumSilenceMs.ifBlank { "120" }}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
        }

        ToolCard(
            title = "音量处理（WAV）",
            subtitle = "调节音量或标准化",
            icon = Icons.Outlined.GraphicEq
        ) {
            AnalysisCardBody(
                icon = Icons.Outlined.GraphicEq,
                buttonText = "分析",
                enabled = !isBusy,
                summary = if (audioInfos.isEmpty()) {
                    "导入音频后显示结果。"
                } else {
                    audioInfos.joinToString("\n") { "${it.name}：${it.volumeHint}" }
                }
            ) {
                onPickFilesFromFileManager(
                    AppPrefs.savePath,
                    "选择音频文件",
                    "导入音频文件。",
                    true,
                    { audioPicker.launch("audio/*") }
                ) { paths ->
                    analyzeInputs(paths.toPickedFileInputs(), "音量分析完成") { "已分析 $it 个文件" }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WavVolumeMode.entries.forEach { mode ->
                    AssistChip(
                        onClick = { volumeMode = mode },
                        label = { Text(mode.label) },
                        leadingIcon = if (volumeMode == mode) {
                            { Icon(Icons.Outlined.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = gainPercent,
                    onValueChange = { gainPercent = it },
                    label = { Text("音量 %") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = smoothCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = targetPeakPercent,
                    onValueChange = { targetPeakPercent = it },
                    label = { Text("目标峰值 %") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = smoothCornerShape(16.dp)
                )
            }
            AudioActionButton(
                text = "处理 WAV",
                icon = Icons.Outlined.GraphicEq,
                enabled = !isBusy
            ) {
                onPickFilesFromFileManager(
                    AppPrefs.savePath,
                    "选择 WAV 文件",
                    "选择要处理的 WAV 文件。",
                    true,
                    { audioPicker.launch("audio/wav") }
                ) { paths ->
                    val inputs = paths.toPickedFileInputs()
                    if (inputs.isEmpty()) return@onPickFilesFromFileManager
                    scope.launch {
                        onBusyChange(true)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val gain = gainPercent.toIntOrNull()?.coerceIn(10, 400) ?: 120
                                val targetPeak = targetPeakPercent.toIntOrNull()?.coerceIn(10, 100) ?: 90
                                val outputDir = resolveOutputDirectory(outputPath, "音频工具/WAV音量处理")
                                val results = inputs.mapNotNull {
                                    adjustWavVolume(
                                        context = context,
                                        input = it,
                                        outputDir = outputDir,
                                        mode = volumeMode,
                                        gainPercent = gain,
                                        targetPeakPercent = targetPeak
                                    )
                                }
                                val summary = results.take(3).joinToString("；") {
                                    "${it.sourceName} ${it.sourcePeakPercent.format1()}%→${it.targetPeakPercent.format1()}%"
                                }
                                ToolOutput(
                                    title = "WAV 音量处理完成",
                                    message = if (results.isEmpty()) {
                                        "没有可处理的 WAV 文件"
                                    } else {
                                        "已生成 ${results.size} 个音量处理结果${if (summary.isNotBlank()) "：$summary" else ""}"
                                    },
                                    files = results.map { it.outputFile },
                                    directory = outputDir
                                )
                            }
                        }.onSuccess {
                            onResult(it)
                            showSnack(it.message)
                        }.onFailure {
                            showSnack("WAV 音量处理失败：${it.message ?: "未知错误"}")
                        }
                        onBusyChange(false)
                    }
                }
            }
            Text(
                "音量模式按百分比放大；标准化按目标峰值处理。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = smoothCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
            ) {
                Text(
                    text = when (volumeMode) {
                        WavVolumeMode.GAIN -> "音量 ${gainPercent.ifBlank { "120" }}%"
                        WavVolumeMode.NORMALIZE -> "目标峰值 ${targetPeakPercent.ifBlank { "90" }}%"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
        }

        ToolCard(
            title = "声道转换（WAV）",
            subtitle = "查看并转换声道",
            icon = Icons.Outlined.SurroundSound
        ) {
            AnalysisCardBody(
                icon = Icons.Outlined.SurroundSound,
                buttonText = "分析",
                enabled = !isBusy,
                summary = if (audioInfos.isEmpty()) {
                    "导入音频后显示结果。"
                } else {
                    audioInfos.joinToString("\n") { "${it.name}：${it.channelHint}" }
                }
            ) {
                onPickFilesFromFileManager(
                    AppPrefs.savePath,
                    "选择音频文件",
                    "导入音频文件。",
                    true,
                    { audioPicker.launch("audio/*") }
                ) { paths ->
                    analyzeInputs(paths.toPickedFileInputs(), "声道分析完成") { "已分析 $it 个文件" }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WavChannelMode.entries.forEach { mode ->
                    AssistChip(
                        onClick = { channelMode = mode },
                        label = { Text(mode.label) },
                        leadingIcon = if (channelMode == mode) {
                            { Icon(Icons.Outlined.SurroundSound, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
            AudioActionButton(
                text = "转换 WAV",
                icon = Icons.Outlined.SurroundSound,
                enabled = !isBusy
            ) {
                onPickFilesFromFileManager(
                    AppPrefs.savePath,
                    "选择 WAV 文件",
                    "选择要转换的 WAV 文件。",
                    true,
                    { audioPicker.launch("audio/wav") }
                ) { paths ->
                    val inputs = paths.toPickedFileInputs()
                    if (inputs.isEmpty()) return@onPickFilesFromFileManager
                    scope.launch {
                        onBusyChange(true)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val outputDir = resolveOutputDirectory(outputPath, "音频工具/WAV声道转换")
                                val results = inputs.mapNotNull {
                                    convertWavChannels(
                                        context = context,
                                        input = it,
                                        outputDir = outputDir,
                                        mode = channelMode
                                    )
                                }
                                val summary = results.take(3).joinToString("；") {
                                    "${it.sourceName} ${it.sourceChannels}声道→${it.targetChannels}声道"
                                }
                                ToolOutput(
                                    title = "WAV 声道转换完成",
                                    message = if (results.isEmpty()) {
                                        "没有可转换的 WAV 文件"
                                    } else {
                                        "已生成 ${results.size} 个声道转换结果${if (summary.isNotBlank()) "：$summary" else ""}"
                                    },
                                    files = results.map { it.outputFile },
                                    directory = outputDir
                                )
                            }
                        }.onSuccess {
                            onResult(it)
                            showSnack(it.message)
                        }.onFailure {
                            showSnack("WAV 声道转换失败：${it.message ?: "未知错误"}")
                        }
                        onBusyChange(false)
                    }
                }
            }
            Surface(
                shape = smoothCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            ) {
                Text(
                    text = when (channelMode) {
                        WavChannelMode.MONO_TO_STEREO -> "单声道 → 双声道"
                        WavChannelMode.STEREO_TO_MONO -> "双声道 → 单声道"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.AudioActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = smoothCornerShape(24.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.size(6.dp))
        Text(text)
    }
}

@Composable
private fun ColumnScope.AnalysisCardBody(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    buttonText: String,
    enabled: Boolean,
    summary: String,
    onAnalyze: () -> Unit
) {
    AudioActionButton(text = buttonText, icon = icon, enabled = enabled, onClick = onAnalyze)
    Surface(
        shape = smoothCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
    }
}

@Composable
private fun AudioInfoList(audioInfos: List<AudioInfoItem>) {
    HorizontalDivider()
    audioInfos.forEach { item ->
        Surface(
            shape = smoothCornerShape(16.dp),
            color = if (item.hasIssues) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row {
                    Text(item.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (item.hasIssues) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("异常") },
                            leadingIcon = { Icon(Icons.Outlined.ReportProblem, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
                Text(
                    "${formatDuration(item.durationMs)} · ${formatFileSize(item.size)} · ${item.mimeType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    listOfNotNull(
                        item.artist,
                        item.sampleRate?.let { "${it}Hz" },
                        item.bitrate?.let { "${it}bps" }
                    ).joinToString(" · ").ifBlank { "无更多元数据" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.issueFlags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item.issueFlags.forEach { issue ->
                            Surface(
                                shape = smoothCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    issue,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                Text(
                    item.analysisSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun AudioIssueRow(item: AudioInfoItem) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Text(item.name, fontWeight = FontWeight.SemiBold)
        Text(
            item.issueFlags.joinToString(" · ").ifBlank { "未发现异常" },
            style = MaterialTheme.typography.bodySmall,
            color = if (item.hasIssues) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            item.analysisSummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

        val issues = mutableListOf<String>()
        if (duration <= 0L) issues += "时长异常"
        if (size in 0..1024L) issues += "文件过小"
        if (mime == "audio/*") issues += "类型未知"
        if (sampleRate.isNullOrBlank()) issues += "缺少采样率"
        if (bitrate.isNullOrBlank()) issues += "缺少码率"
        if (input.file?.extension?.lowercase(Locale.ROOT) == "wav" && bitrate.isNullOrBlank()) issues += "头信息可疑"

        val silenceHint = when {
            duration <= 0L -> "无法判断静音情况"
            size <= 2048L -> "疑似空内容或全静音文件"
            bitrate?.toLongOrNull()?.let { it < 32000L } == true -> "疑似长静音"
            else -> "正常"
        }
        val volumeHint = when {
            bitrate?.toLongOrNull()?.let { it < 48000L } == true -> "疑似偏小"
            duration <= 0L -> "无法分析音量"
            else -> "正常"
        }
        val channelHint = when {
            mime.contains("ogg", ignoreCase = true) -> "元数据不足"
            input.file?.extension?.equals("wav", ignoreCase = true) == true || mime.contains("wav", ignoreCase = true) -> {
                inspectWavMeta(context, input)?.let { meta ->
                    "${meta.channels} 声道 / ${meta.bitsPerSample}bit / 峰值 ${meta.peakPercent.format1()}%"
                } ?: "WAV 头信息不可读"
            }
            else -> "暂无声道信息"
        }
        val analysisSummary = buildString {
            append("静音：").append(silenceHint)
            append("；音量：").append(volumeHint)
            append("；声道：").append(channelHint)
        }

        AudioInfoItem(
            name = name,
            durationMs = duration,
            mimeType = mime,
            artist = artist,
            sampleRate = sampleRate,
            bitrate = bitrate,
            size = size,
            issueFlags = issues,
            silenceHint = silenceHint,
            volumeHint = volumeHint,
            channelHint = channelHint,
            analysisSummary = analysisSummary
        )
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
            appendLine("name,duration,mime,artist,sampleRate,bitrate,size,issues,silenceHint,volumeHint,channelHint")
            infos.forEach { info ->
                appendLine(
                    listOf(
                        info.name,
                        formatDuration(info.durationMs),
                        info.mimeType,
                        info.artist.orEmpty(),
                        info.sampleRate.orEmpty(),
                        info.bitrate.orEmpty(),
                        info.size.toString(),
                        info.issueFlags.joinToString(" | "),
                        info.silenceHint,
                        info.volumeHint,
                        info.channelHint
                    ).joinToString(",") { value -> "\"${value.replace("\"", "\"\"")}\"" }
                )
            }
        }
    )
    return target
}

private fun Double.format1(): String = String.format(Locale.getDefault(), "%.1f", this)
