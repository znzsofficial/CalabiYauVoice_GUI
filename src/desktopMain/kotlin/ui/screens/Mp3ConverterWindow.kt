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
import androidx.compose.foundation.verticalScroll
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

private enum class ConverterHelpTopic(val title: String, val lines: List<String>) {
    Format(
        "采样率与位深怎么选",
        listOf(
            "原采样率/原位深：尽量保留源文件参数，适合只想转成 WAV。",
            "16 bit：兼容性最好，适合游戏语音、剪辑和常规播放。",
            "24 bit：保留更多动态范围，适合后续编辑。",
            "32 bit int：整数 PCM，适合需要高位深但不想使用浮点的流程。",
            "32 bit float：适合专业音频工作流，文件会更大。"
        )
    ),
    DeleteOriginal(
        "删除原始源文件",
        listOf(
            "开启后，仅在 WAV 转换成功时删除原来的 MP3/FLAC。",
            "如果还要核对转换结果，建议先保持关闭。"
        )
    ),
    Merge(
        "合并导出 WAV",
        listOf(
            "会先把每个源文件转为 WAV，再按文件列表顺序合并。",
            "合并时需要统一格式，所以不能使用“原位深”。",
            "每组文件上限为 0 时，表示全部合并为一个文件。"
        )
    )
}

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
    var enableDitherOnDownsample by remember { mutableStateOf(false) }
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
    var isFlyoutVisible by remember { mutableStateOf(false) }
    var helpTopic by remember { mutableStateOf<ConverterHelpTopic?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val windowState = rememberWindowState(
        width = 1080.dp,
        height = 840.dp,
        position = WindowPosition(Alignment.Center)
    )

    val selectedBitDepthOption = bitDepthOptionAt(targetBitDepthIndex)

    StyledWindow(
        title = "音频转换工具",
        onCloseRequest = onCloseRequest,
        state = windowState,
        onKeyEvent = { keyEvent ->
            if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                onCloseRequest(); true
            } else false
        }
    ) { _ ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // 左列：拖放文件操作区与日志
            // ==========================================
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                    Card(Modifier.fillMaxWidth().height(120.dp)) {
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
                
                // ── 左侧底部状态文本
                if (progressText.isNotBlank()) {
                    Text(progressText, fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
                }
            }

            // ==========================================
            // 右列：设定、帮助与执行操作
            // ==========================================
            val scrollState = rememberScrollState()
            Column(
                Modifier.width(340.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 用于包含配置和详情支持滑动的区域
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── 保存路径 ──────────────────────────────────────
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("保存路径", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            TextField(
                                value = savePath,
                                onValueChange = {
                                    savePath = it
                                    AppPrefs.converterSavePath = it
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TooltipBox(tooltip = { Text("选择文件夹") }) {
                                    Button(
                                        onClick = {
                                            jChoose {
                                                savePath = it.absolutePath
                                                AppPrefs.converterSavePath = it.absolutePath
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Regular.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("浏览")
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
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
                    }

                    // ── 转换配置 ──────────────────────────────────────
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("转换配置", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(Modifier.weight(1f))
                                TooltipBox(tooltip = { Text("查看推荐设置") }) {
                                    Button(
                                        iconOnly = true,
                                        onClick = { 
                                            helpTopic = if (helpTopic == ConverterHelpTopic.Format) null else ConverterHelpTopic.Format
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) { Icon(Icons.Regular.Info, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                }
                            }

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
                                    items = BIT_DEPTH_OPTION_LABELS,
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
                                CheckBox(
                                    checked = deleteOriginalMp3,
                                    label = "删除源文件",
                                    onCheckStateChange = { deleteOriginalMp3 = it }
                                )
                                TooltipBox(tooltip = { Text("转换成功后才删除源文件") }) {
                                    Button(
                                        iconOnly = true,
                                        onClick = { helpTopic = if (helpTopic == ConverterHelpTopic.DeleteOriginal) null else ConverterHelpTopic.DeleteOriginal },
                                        modifier = Modifier.size(28.dp)
                                    ) { Icon(Icons.Regular.Info, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                }
                            }
                            
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                CheckBox(
                                    checked = mergeWav,
                                    label = "合并导出 WAV",
                                    onCheckStateChange = { mergeWav = it }
                                )
                                TooltipBox(tooltip = { Text("把多个 WAV 拼成一个或多个文件") }) {
                                    Button(
                                        iconOnly = true,
                                        onClick = { helpTopic = if (helpTopic == ConverterHelpTopic.Merge) null else ConverterHelpTopic.Merge },
                                        modifier = Modifier.size(28.dp)
                                    ) { Icon(Icons.Regular.Info, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                }
                            }

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                BIT_DEPTH_OPTIONS.forEachIndexed { index, option ->
                                    ToggleButton(
                                        checked = index == targetBitDepthIndex,
                                        onCheckedChanged = { if (it) targetBitDepthIndex = index },
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text(
                                            option.label,
                                            fontSize = 12.sp,
                                            fontWeight = if (index == targetBitDepthIndex) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            CheckBox(
                                checked = enableDitherOnDownsample,
                                label = "高位深转低位深时启用抖动",
                                onCheckStateChange = { enableDitherOnDownsample = it }
                            )

                            Text(
                                text = buildConverterSummary(
                                    sampleRate = SAMPLE_RATE_OPTIONS.getOrNull(targetSampleRateIndex),
                                    bitDepth = selectedBitDepthOption,
                                    enableDitherOnDownsample = enableDitherOnDownsample,
                                    mergeWav = mergeWav,
                                    deleteOriginal = deleteOriginalMp3
                                ),
                                fontSize = 12.sp,
                                color = FluentTheme.colors.text.text.secondary
                            )

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
                                    TextField(
                                        value = mergeWavMaxCountStr,
                                        onValueChange = { if (it.all { c -> c.isDigit() }) mergeWavMaxCountStr = it },
                                        header = {
                                            Text(
                                                "每单文件组合上限 (0为不限)",
                                                fontSize = 11.sp,
                                                color = FluentTheme.colors.text.text.secondary
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    Box(Modifier.padding(bottom = 6.dp)) {
                                        CheckBox(
                                            checked = deleteWavAfterMerge,
                                            label = "合并后删除分段缓存",
                                            onCheckStateChange = { deleteWavAfterMerge = it }
                                        )
                                    }
                                    if (selectedBitDepthOption.target == BitDepthTarget.ORIGINAL) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Regular.Info, contentDescription = null, modifier = Modifier.size(14.dp), tint = FluentTheme.colors.system.attention)
                                            Text(
                                                "不能用原位深，必需 16 / 24 / 32 float",
                                                fontSize = 12.sp,
                                                color = FluentTheme.colors.system.attention
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 帮助贴士展示区域 ──────────────────────────────────────
                    // 在右列的下方展示，避免挤压主要拖动区域区域
                    // 可使用 AnimatedVisibility 使其过渡自然，但因不在左侧，直接 if 也可以
                    if (helpTopic != null) {
                        Card(
                            Modifier.fillMaxWidth(),
                        ) {
                            val topic = helpTopic!!
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Regular.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = FluentTheme.colors.fillAccent.default)
                                    Text(topic.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Spacer(Modifier.weight(1f))
                                    Button(iconOnly = true, onClick = { helpTopic = null }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Regular.Dismiss, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }
                                topic.lines.forEach { line ->
                                    Text(line, fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
                                }
                            }
                        }
                    }
                }

                // ── 底部操作栏 ────────────────────────────────────
                // 位于右侧面板最下方，始终保持在视野中
                FlyoutContainer(
                    flyout = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.widthIn(max = 280.dp).padding(16.dp)
                        ) {
                            Text(
                                text = "合并分段模式下必须明确指定位深，请在上方的\"位深\"下拉菜单中选择一个指定的位深度。",
                                style = FluentTheme.typography.bodyStrong
                            )
                            Button(
                                onClick = { isFlyoutVisible = false },
                                content = { Text("知道了") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    content = {
                        Button(
                            onClick = {
                                val files = mp3Files.toList()
                                if (files.isEmpty()) return@Button
                                // 合并分片时必须设置具体位深
                                if (mergeWav && selectedBitDepthOption.target == BitDepthTarget.ORIGINAL) {
                                    isFlyoutVisible = true
                                    return@Button
                                }
                                isFlyoutVisible = false
                                // 转换到各自所在目录（若设置了保存路径则用保存路径）
                                val outDir = File(savePath).also { it.mkdirs() }
                                isConverting = true
                                logLines = emptyList()
                                progressText = ""
                                coroutineScope.launch(Dispatchers.IO) {
                                    val sampleRate = SAMPLE_RATE_OPTIONS.getOrNull(targetSampleRateIndex)
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
                                            targetBitDepth = selectedBitDepthOption.target,
                                            enableDitherOnDownsample = enableDitherOnDownsample,
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
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            if (isConverting) {
                                ProgressRing(size = 16.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("转换中...")
                            } else {
                                Icon(Icons.Regular.ArrowSync, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("开始转换 (${mp3Files.size} 个)", fontWeight = FontWeight.Bold)
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

private fun buildConverterSummary(
    sampleRate: Float?,
    bitDepth: BitDepthOption,
    enableDitherOnDownsample: Boolean,
    mergeWav: Boolean,
    deleteOriginal: Boolean
): String {
    val format = "输出：${sampleRateLabel(sampleRate)} / ${bitDepth.label}"
    val dither = if (enableDitherOnDownsample) "降位深时抖动" else "不额外抖动"
    val merge = if (mergeWav) "会合并 WAV" else "逐个导出 WAV"
    val delete = if (deleteOriginal) "转换成功后删除源文件" else "保留源文件"
    return "$format · $dither · $merge · $delete"
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
