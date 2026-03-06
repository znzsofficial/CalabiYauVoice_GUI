package ui.screens

import LocalAppStore
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.mayakapps.compose.windowstyler.WindowBackdrop
import com.mayakapps.compose.windowstyler.WindowStyle
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.component.*
import io.github.composefluent.darkColors
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.*
import io.github.composefluent.lightColors
import io.github.composefluent.surface.Card
import jna.windows.structure.isWindows11OrLater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ui.components.WindowsWindowFrame
import ui.components.rememberWindowsWindowFrameState
import util.*
import util.AppPrefs
import java.awt.datatransfer.DataFlavor
import java.io.File

@OptIn(ExperimentalFluentApi::class, ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun Mp3ConverterWindow(
    onCloseRequest: () -> Unit
) {
    val darkModeState = LocalAppStore.current.darkMode
    var savePath by remember { mutableStateOf(AppPrefs.converterSavePath) }

    // 独立的转换配置状态（不与主页共用）
    var targetSampleRateIndex by remember { mutableStateOf(0) }
    var targetBitDepthIndex by remember { mutableStateOf(DEFAULT_BIT_DEPTH_INDEX) }
    var deleteOriginalMp3 by remember { mutableStateOf(false) }
    var mergeWav by remember { mutableStateOf(false) }
    var mergeWavMaxCountStr by remember { mutableStateOf("0") }
    var deleteWavAfterMerge by remember { mutableStateOf(true) }

    // 拖入的 MP3 文件列表
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

    Window(
        onCloseRequest = onCloseRequest,
        title = "MP3 → WAV 转换工具",
        state = windowState,
        onKeyEvent = { keyEvent ->
            if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                onCloseRequest(); true
            } else false
        }
    ) {
        val darkMode = darkModeState.value
        val windowFrameState = rememberWindowsWindowFrameState(window)
        val skiaLayerExists = remember { window.findSkiaLayer() != null }
        val isWin11 = remember { isWindows11OrLater() }

        if (skiaLayerExists && isWin11) {
            LaunchedEffect(Unit) { window.findSkiaLayer()?.transparency = true }
            WindowStyle(isDarkTheme = darkMode, backdropType = WindowBackdrop.Tabbed)
        }

        FluentTheme(colors = if (darkMode) darkColors() else lightColors(), useAcrylicPopup = true) {
            WindowsWindowFrame(
                title = "MP3 → WAV 转换工具",
                onCloseRequest = onCloseRequest,
                state = windowState,
                frameState = windowFrameState,
                isDarkTheme = darkMode,
                captionBarHeight = 36.dp
            ) { windowInset, _ ->
                Layer(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(windowFrameState.paddingInset)
                        .windowInsetsPadding(windowInset),
                    color = Color.Transparent,
                    contentColor = FluentTheme.colors.text.text.primary,
                    border = null,
                ) {
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
                                        text = "删除原始 MP3"
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
                                                header = { Text("每组文件上限 (0为不限)", fontSize = 11.sp, color = FluentTheme.colors.text.text.secondary) },
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
                                                                .filter { it.isFile && it.extension.lowercase() == "mp3" }
                                                                .toList()
                                                            else listOf(f)
                                                        }
                                                        .filter { it.extension.lowercase() == "mp3" }
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
                                            "将 MP3 文件或文件夹拖放到此处",
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
                                            Text(line, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
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
                            Button(
                                onClick = {
                                    val files = mp3Files.toList()
                                    if (files.isEmpty()) return@Button
                                    // 转换到各自所在目录（若设置了保存路径则用保存路径）
                                    val outDir = File(savePath).also { it.mkdirs() }
                                    isConverting = true
                                    logLines = emptyList()
                                    progressText = ""
                                    coroutineScope.launch(Dispatchers.IO) {
                                        // 把文件复制到临时目录逐一处理（或直接就地处理）
                                        // 统一放进一个临时子目录再调用 batchConvertMp3ToWav
                                        val tempDir = File(outDir, "_mp3conv_tmp_${System.currentTimeMillis()}")
                                        tempDir.mkdirs()
                                        files.forEach { it.copyTo(File(tempDir, it.name), overwrite = true) }

                                        val sampleRate = SAMPLE_RATE_OPTIONS.getOrNull(targetSampleRateIndex)
                                        val bitDepth = BIT_DEPTH_OPTIONS.getOrElse(targetBitDepthIndex) { 16 }
                                        val mergeCount = mergeWavMaxCountStr.toIntOrNull() ?: 0
                                        val doMerge = mergeWav
                                        val doDelete = deleteOriginalMp3
                                        val doDeleteWavAfterMerge = deleteWavAfterMerge

                                        batchConvertMp3ToWav(
                                            dir = tempDir,
                                            deleteOriginal = doDelete,
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

                                        // 将结果移到 outDir
                                        tempDir.walkTopDown().filter { it.isFile }.forEach { f ->
                                            f.copyTo(File(outDir, f.name), overwrite = true)
                                            f.delete()
                                        }
                                        tempDir.delete()

                                        coroutineScope.launch(Dispatchers.Main) {
                                            isConverting = false
                                            progressText = "完成"
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
                    }
                }
            }
        }
    }
}

