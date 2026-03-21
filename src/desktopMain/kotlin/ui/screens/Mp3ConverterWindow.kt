package ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.*
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.*
import io.github.composefluent.surface.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ui.components.ComboBox
import ui.components.StyledWindow
import util.*
import util.AppPrefs
import java.awt.datatransfer.DataFlavor
import java.io.File

internal data class StagedAudioFile(
    val originalFile: File,
    val stagedDirectory: File,
    val stagedSourceFile: File,
    val stagedWavFile: File
)

@OptIn(
    ExperimentalFluentApi::class, ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun Mp3ConverterWindow(
    onCloseRequest: () -> Unit
) {
    var savePath by remember { mutableStateOf(AppPrefs.converterSavePath) }

    // 独立的转换配置状态（不与主页共用）
    var targetSampleRateIndex by remember { mutableStateOf(0) }
    var targetBitDepthIndex by remember { mutableStateOf(DEFAULT_BIT_DEPTH_INDEX) }
    var deleteOriginalMp3 by remember { mutableStateOf(false) }
    var mergeWav by remember { mutableStateOf(false) }
    var mergeWavMaxCountStr by remember { mutableStateOf("0") }
    var deleteWavAfterMerge by remember { mutableStateOf(true) }

    // 拖入的音频文件列表
    var mp3Files by remember { mutableStateOf<List<File>>(emptyList()) }
    var isDraggingOver by remember { mutableStateOf(false) }

    // 转换状态
    var isConverting by remember { mutableStateOf(false) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var progressText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    val windowState = rememberWindowState(
        width = 640.dp,
        height = 680.dp,
        position = WindowPosition(Alignment.Center)
    )

    StyledWindow(
        title = "MP3/FLAC → WAV 转换工具",
        onCloseRequest = onCloseRequest,
        state = windowState,
        onKeyEvent = { keyEvent ->
            if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                onCloseRequest(); true
            } else false
        }
    ) { _ ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── 保存路径 ──────────────────────────────────────
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = savePath,
                        onValueChange = {
                            savePath = it
                            AppPrefs.converterSavePath = it
                        },
                        header = { Text("保存路径", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    TooltipBox(tooltip = { Text("选择文件夹") }) {
                        Button(
                            iconOnly = true,
                            onClick = {
                                jChoose {
                                    savePath = it.absolutePath
                                    AppPrefs.converterSavePath = it.absolutePath
                                }
                            }
                        ) {
                            Icon(Icons.Regular.FolderOpen, contentDescription = null)
                        }
                    }
                    TooltipBox(tooltip = { Text("打开保存路径") }) {
                        Button(
                            iconOnly = true,
                            onClick = {
                                val dir = File(savePath)
                                val target = if (dir.exists()) dir else dir.parentFile ?: dir
                                runCatching { java.awt.Desktop.getDesktop().open(target) }
                            }
                        ) {
                            Icon(Icons.Regular.Open, contentDescription = null)
                        }
                    }
                }
            }

            // ── 转换配置 ──────────────────────────────────────
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("转换配置", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                    // 第一行：采样率 + 位深
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ComboBox(
                            header = "采样率",
                            placeholder = "原采样率",
                            selected = targetSampleRateIndex,
                            items = SAMPLE_RATE_OPTIONS.map { sampleRateLabel(it) },
                            onSelectionChange = { i, _ -> targetSampleRateIndex = i },
                            modifier = Modifier.weight(1f)
                        )
                        ComboBox(
                            header = "位深",
                            placeholder = "16 bit",
                            selected = targetBitDepthIndex,
                            items = BIT_DEPTH_OPTIONS.map { bitDepthLabel(it) },
                            onSelectionChange = { i, _ -> targetBitDepthIndex = i },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 第二行：基础开关
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Switcher(
                            checked = deleteOriginalMp3,
                            onCheckStateChange = { deleteOriginalMp3 = it },
                            textBefore = true,
                            text = "删除原始源文件"
                        )
                        Switcher(
                            checked = mergeWav,
                            onCheckStateChange = { mergeWav = it },
                            textBefore = true,
                            text = "合并导出 WAV"
                        )
                    }

                    // 第三行：合并详细配置（折叠面板样式）
                    if (mergeWav) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(FluentTheme.colors.control.secondary)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TextField(
                                    value = mergeWavMaxCountStr,
                                    onValueChange = { if (it.all { c -> c.isDigit() }) mergeWavMaxCountStr = it },
                                    header = {
                                        Text(
                                            "每组文件上限 (0为不限)",
                                            fontSize = 11.sp,
                                            color = FluentTheme.colors.text.text.secondary
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Box(Modifier.padding(bottom = 6.dp)) {
                                    Switcher(
                                        checked = deleteWavAfterMerge,
                                        onCheckStateChange = { deleteWavAfterMerge = it },
                                        textBefore = true,
                                        text = "合并后删除分片"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 文件拖放区 + 列表 ─────────────────────────────
            val dropBorderColor = if (isDraggingOver)
                FluentTheme.colors.fillAccent.default
            else
                FluentTheme.colors.stroke.card.default

            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(2.dp, dropBorderColor, RoundedCornerShape(6.dp))
                        // AWT 拖放监听
                        .dragAndDropTarget(
                            shouldStartDragAndDrop = { true },
                            target = remember {
                                object : DragAndDropTarget {
                                    override fun onStarted(event: DragAndDropEvent) {
                                        isDraggingOver = true
                                    }

                                    override fun onEnded(event: DragAndDropEvent) {
                                        isDraggingOver = false
                                    }

                                    override fun onDrop(event: DragAndDropEvent): Boolean {
                                        isDraggingOver = false
                                        val transferable = event.nativeEvent
                                            .let { it as? java.awt.dnd.DropTargetDropEvent }
                                            ?: return false
                                        transferable.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY)
                                        val dropped = runCatching {
                                            @Suppress("UNCHECKED_CAST")
                                            transferable.transferable
                                                .getTransferData(DataFlavor.javaFileListFlavor)
                                                    as List<File>
                                        }.getOrNull() ?: return false
                                        val newMp3s = dropped
                                            .flatMap { f ->
                                                if (f.isDirectory) f.walkTopDown()
                                                    .filter(::isSupportedAudioSource)
                                                    .toList()
                                                else listOf(f)
                                            }
                                            .filter(::isSupportedAudioSource)
                                        mp3Files = (mp3Files + newMp3s).distinctBy { it.absolutePath }
                                        return true
                                    }
                                }
                            }
                        )
                ) {
                    if (mp3Files.isEmpty()) {
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Regular.ArrowDownload,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = FluentTheme.colors.text.text.secondary
                            )
                            Text(
                                "将 MP3/FLAC 文件或文件夹拖放到此处",
                                color = FluentTheme.colors.text.text.secondary,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Column(Modifier.fillMaxSize().padding(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${mp3Files.size} 个文件",
                                    fontSize = 12.sp,
                                    color = FluentTheme.colors.text.text.secondary
                                )
                                Spacer(Modifier.weight(1f))
                                Button(
                                    onClick = { mp3Files = emptyList(); logLines = emptyList() },
                                    modifier = Modifier.height(26.dp)
                                ) { Text("清空", fontSize = 11.sp) }
                            }
                            val listState = rememberLazyListState()
                            ScrollbarContainer(
                                adapter = rememberScrollbarAdapter(listState),
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    items(mp3Files) { file ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(FluentTheme.colors.control.secondary)
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Regular.MusicNote2,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = FluentTheme.colors.fillAccent.default
                                            )
                                            Text(file.name, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                            Text(
                                                "%.1f KB".format(file.length() / 1024.0),
                                                fontSize = 11.sp,
                                                color = FluentTheme.colors.text.text.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 日志 ─────────────────────────────────────────
            if (logLines.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().height(100.dp)) {
                    val logListState = rememberLazyListState()
                    LaunchedEffect(logLines.size) {
                        if (logLines.isNotEmpty()) logListState.animateScrollToItem(logLines.size - 1)
                    }
                    ScrollbarContainer(
                        adapter = rememberScrollbarAdapter(logListState),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = logListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            items(logLines) { line ->
                                Text(
                                    line,
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // ── 底部操作栏 ────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (progressText.isNotBlank()) {
                    Text(progressText, fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
                }
                Spacer(Modifier.weight(1f))
                FlyoutContainer(
                    flyout = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Text(
                                text = "合并分片模式下必须明确指定位深，请先在\"位深\"下拉菜单中选择 16 bit、24 bit 或浮点 32 bit。",
                                style = FluentTheme.typography.bodyStrong
                            )
                            Button(
                                onClick = { isFlyoutVisible = false },
                                content = { Text("知道了") }
                            )
                        }
                    },
                    content = {
                        Button(
                            onClick = {
                                val files = mp3Files.toList()
                                if (files.isEmpty()) return@Button
                                // 合并分片时必须设置具体位深
                                if (mergeWav && BIT_DEPTH_OPTIONS.getOrNull(targetBitDepthIndex) == null) {
                                    isFlyoutVisible = true
                                    return@Button
                                }
                                // 转换到各自所在目录（若设置了保存路径则用保存路径）
                                val outDir = File(savePath).also { it.mkdirs() }
                                isConverting = true
                                logLines = emptyList()
                                progressText = ""
                                coroutineScope.launch(Dispatchers.IO) {
                                    val sampleRate = SAMPLE_RATE_OPTIONS.getOrNull(targetSampleRateIndex)
                                    val bitDepth = BIT_DEPTH_OPTIONS.getOrNull(targetBitDepthIndex)
                                    val mergeCount = mergeWavMaxCountStr.toIntOrNull() ?: 0
                                    val doMerge = mergeWav
                                    val doDeleteOriginalMp3 = deleteOriginalMp3
                                    val doDeleteWavAfterMerge = deleteWavAfterMerge
                                    val tempDir = File(outDir, "_mp3conv_tmp_${System.currentTimeMillis()}")

                                    try {
                                        tempDir.mkdirs()
                                        val stagedFiles = stageDraggedAudioFiles(files, tempDir)

                                        batchConvertAudioToWav(
                                            dir = tempDir,
                                            deleteOriginal = false,
                                            targetSampleRate = sampleRate,
                                            targetBitDepth = bitDepth,
                                            onLog = { msg ->
                                                coroutineScope.launch(Dispatchers.Main) {
                                                    logLines = logLines + msg
                                                }
                                            },
                                            onProgress = { done, total, name ->
                                                coroutineScope.launch(Dispatchers.Main) {
                                                    progressText = "$done / $total  $name"
                                                }
                                            }
                                        )

                                        val convertedOriginalFiles = stagedFiles
                                            .filter { it.stagedWavFile.isFile }
                                            .map { it.originalFile }

                                        if (doMerge) {
                                            mergeWavFiles(
                                                dir = tempDir,
                                                maxPerFile = mergeCount,
                                                deleteOriginal = doDeleteWavAfterMerge,
                                                onLog = { msg ->
                                                    coroutineScope.launch(Dispatchers.Main) {
                                                        logLines = logLines + msg
                                                    }
                                                }
                                            )
                                        }

                                        exportConvertedFiles(tempDir, outDir)

                                        if (doDeleteOriginalMp3) {
                                            convertedOriginalFiles.forEach { original ->
                                                if (!original.delete()) {
                                                    coroutineScope.launch(Dispatchers.Main) {
                                                        logLines = logLines + "[删除失败] ${original.absolutePath}"
                                                    }
                                                }
                                            }
                                        }

                                        coroutineScope.launch(Dispatchers.Main) {
                                            isConverting = false
                                            progressText = "完成"
                                        }
                                    } catch (e: Exception) {
                                        coroutineScope.launch(Dispatchers.Main) {
                                            isConverting = false
                                            progressText = "失败"
                                            logLines = logLines + "[转换中断] ${e.message ?: e::class.simpleName.orEmpty()}"
                                        }
                                    } finally {
                                        tempDir.deleteRecursively()
                                    }
                                }
                            },
                            disabled = isConverting || mp3Files.isEmpty(),
                            modifier = Modifier.height(36.dp)
                        ) {
                            if (isConverting) {
                                ProgressRing(size = 16.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("转换中...")
                            } else {
                                Icon(Icons.Regular.ArrowSync, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("开始转换 (${mp3Files.size} 个文件)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )
            }
        }
    }
}


internal fun stageDraggedAudioFiles(files: List<File>, tempDir: File): List<StagedAudioFile> =
    files.mapIndexed { index, source ->
        val folderName = "%03d_%s".format(index + 1, safePathSegment(source.nameWithoutExtension))
        val stagedDirectory = File(tempDir, folderName).also { it.mkdirs() }
        val stagedSourceFile = File(stagedDirectory, source.name)
        source.copyTo(stagedSourceFile, overwrite = true)
        StagedAudioFile(
            originalFile = source,
            stagedDirectory = stagedDirectory,
            stagedSourceFile = stagedSourceFile,
            stagedWavFile = File(stagedDirectory, source.nameWithoutExtension + ".wav")
        )
    }

internal fun exportConvertedFiles(tempDir: File, outDir: File): List<File> {
    outDir.mkdirs()
    val exportedFiles = mutableListOf<File>()

    tempDir.walkTopDown()
        .filter { it.isFile && shouldExportConvertedFile(it) }
        .forEach { source ->
            val target = uniqueOutputFile(outDir, source.name)
            source.copyTo(target, overwrite = false)
            exportedFiles += target
        }

    return exportedFiles
}

private fun shouldExportConvertedFile(file: File): Boolean {
    val lowerName = file.name.lowercase()
    return SUPPORTED_AUDIO_SOURCE_EXTENSIONS.none { lowerName.endsWith(".$it") } && !lowerName.endsWith(".tmp")
}

private fun uniqueOutputFile(outDir: File, fileName: String): File {
    val dotIndex = fileName.lastIndexOf('.')
    val baseName = if (dotIndex > 0) fileName.take(dotIndex) else fileName
    val extension = if (dotIndex > 0) fileName.substring(dotIndex) else ""

    var candidate = File(outDir, fileName)
    var index = 2
    while (candidate.exists()) {
        candidate = File(outDir, "$baseName ($index)$extension")
        index++
    }
    return candidate
}

private fun safePathSegment(name: String): String =
    name.ifBlank { "file" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
