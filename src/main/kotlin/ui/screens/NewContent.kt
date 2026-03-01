package ui.screens

import LocalBackdropType
import LocalThemeState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.launch
import com.mayakapps.compose.windowstyler.WindowBackdrop
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Mica
import io.github.composefluent.component.*
import io.github.composefluent.darkColors
import io.github.composefluent.lightColors
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.FolderOpen
import io.github.composefluent.icons.regular.CursorClick
import io.github.composefluent.icons.regular.TextBulletListLtr
import ui.components.AudioPlayerManager
import ui.components.EmptyPlaceholder
import ui.components.FileListItem
import ui.components.ImagePreviewDialog
import ui.components.SubtleBox
import io.github.composefluent.surface.Card
import ui.components.CharacterAvatar
import ui.components.FileSelectionDialog
import ui.components.TerminalOutputView
import util.BIT_DEPTH_OPTIONS
import util.SAMPLE_RATE_OPTIONS
import util.bitDepthLabel
import util.jChoose
import util.sampleRateLabel
import viewmodel.MainViewModel
import viewmodel.SearchMode

@OptIn(ExperimentalFluentApi::class, ExperimentalFoundationApi::class)
@Composable
fun NewDownloaderContent() {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { MainViewModel(coroutineScope) }

    // --- 状态管理 (来自 ViewModel) ---
    val searchKeyword by viewModel.searchKeyword.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()

    val characterGroups by viewModel.characterGroups.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()

    val fileSearchResults by viewModel.fileSearchResults.collectAsState()
    val fileSearchSelectedUrls by viewModel.fileSearchSelectedUrls.collectAsState()

    // 文件搜索列表 - 音频播放状态
    var fileSearchPlayingUrl by remember { mutableStateOf<String?>(null) }
    var fileSearchLoadingUrl by remember { mutableStateOf<String?>(null) }
    // 文件搜索列表 - 图片预览状态
    var fileSearchPreviewUrl by remember { mutableStateOf<String?>(null) }
    var fileSearchPreviewName by remember { mutableStateOf("") }
    // 注册 AudioPlayerManager 播放结束 & 加载状态回调（文件搜索列表用）
    val fileSearchScope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val stoppedListener: (String) -> Unit = { url ->
            fileSearchScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                if (fileSearchPlayingUrl == url) fileSearchPlayingUrl = null
            }
        }
        val loadingListener: (String, Boolean) -> Unit = { url, loading ->
            fileSearchScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                fileSearchLoadingUrl = if (loading) url
                    else if (fileSearchLoadingUrl == url) null
                    else fileSearchLoadingUrl
            }
        }
        AudioPlayerManager.addOnPlaybackStopped(stoppedListener)
        AudioPlayerManager.addOnLoadingChanged(loadingListener)
        onDispose {
            AudioPlayerManager.removeOnPlaybackStopped(stoppedListener)
            AudioPlayerManager.removeOnLoadingChanged(loadingListener)
        }
    }

    // 图片预览弹窗
    if (fileSearchPreviewUrl != null) {
        ImagePreviewDialog(
            url = fileSearchPreviewUrl!!,
            name = fileSearchPreviewName,
            onClose = { fileSearchPreviewUrl = null }
        )
    }

    val subCategories by viewModel.subCategories.collectAsState()
    val checkedCategories by viewModel.checkedCategories.collectAsState()
    val isScanningTree by viewModel.isScanningTree.collectAsState()

    val savePath by viewModel.savePath.collectAsState()
    val maxConcurrencyStr by viewModel.maxConcurrencyStr.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val progressText by viewModel.progressText.collectAsState()
    val convertAfterDownload by viewModel.convertAfterDownload.collectAsState()
    val deleteOriginalMp3 by viewModel.deleteOriginalMp3.collectAsState()
    val targetSampleRateIndex by viewModel.targetSampleRateIndex.collectAsState()
    val targetBitDepthIndex by viewModel.targetBitDepthIndex.collectAsState()
    val mergeWav by viewModel.mergeWav.collectAsState()
    val mergeWavMaxCountStr by viewModel.mergeWavMaxCountStr.collectAsState()

    val manualSelectionMap by viewModel.manualSelectionMap.collectAsState()
    val categoryTotalCountMap by viewModel.categoryTotalCountMap.collectAsState()

    val showFileDialog by viewModel.showFileDialog.collectAsState()
    val dialogCategoryName by viewModel.dialogCategoryName.collectAsState()
    val dialogFileList by viewModel.dialogFileList.collectAsState()
    val dialogIsLoading by viewModel.dialogIsLoading.collectAsState()
    val dialogInitialSelection by viewModel.dialogInitialSelection.collectAsState()

    val logLines by viewModel.logLines.collectAsState()

    val darkMode = LocalThemeState.current
    val backdropType = LocalBackdropType.current

    // backdrop 选项列表：名称 → 值
    val backdropOptions = remember {
        listOf(
            "Tabbed"      to WindowBackdrop.Tabbed,
            "Mica"        to WindowBackdrop.Mica,
            "Acrylic"     to WindowBackdrop.Acrylic(Color.Transparent),
            "Aero"        to WindowBackdrop.Aero,
            "Transparent" to WindowBackdrop.Transparent,
            "Default"     to WindowBackdrop.Default,
        )
    }

    // === 文件选择弹窗 ===
    if (showFileDialog) {
        FileSelectionDialog(
            title = dialogCategoryName,
            files = dialogFileList,
            // 传入初始选中的 URL 列表
            initialSelection = dialogInitialSelection,
            isLoading = dialogIsLoading,
            onClose = { viewModel.closeFileDialog() },
            onConfirm = { selectedFiles -> viewModel.confirmFileDialog(selectedFiles) }
        )
    }

    val performSearch = viewModel::performSearch
    var showDialog by remember { mutableStateOf(false) }
    var showShortcutsDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        AboutWindow(onCloseRequest = { showDialog = false })
    }
    if (showShortcutsDialog) {
        KeyboardShortcutsDialog(onClose = { showShortcutsDialog = false })
    }

    // 搜索框焦点控制
    val searchFocusRequester = remember { FocusRequester() }
    // 角色列表滚动状态（用于键盘导航）
    val characterListState = rememberLazyListState()
    val keyboardScope = rememberCoroutineScope()

    // === 主界面布局 ===
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val ctrl = keyEvent.isCtrlPressed
                val shift = keyEvent.isShiftPressed
                when {
                    // Ctrl+F → 聚焦搜索框
                    ctrl && keyEvent.key == Key.F -> {
                        searchFocusRequester.requestFocus()
                        true
                    }
                    // F5 → 执行搜索
                    keyEvent.key == Key.F5 -> {
                        performSearch()
                        true
                    }
                    // Ctrl+D → 开始下载
                    ctrl && keyEvent.key == Key.D -> {
                        if (!isDownloading && !isScanningTree) viewModel.startDownload()
                        true
                    }
                    // Ctrl+A → 全选
                    ctrl && !shift && keyEvent.key == Key.A -> {
                        when (searchMode) {
                            SearchMode.FILE_SEARCH -> viewModel.selectAllFileSearchResults()
                            else -> viewModel.checkAllCategories()
                        }
                        true
                    }
                    // Ctrl+Shift+A → 取消全选
                    ctrl && shift && keyEvent.key == Key.A -> {
                        when (searchMode) {
                            SearchMode.FILE_SEARCH -> viewModel.clearFileSearchSelection()
                            else -> viewModel.uncheckAllCategories()
                        }
                        true
                    }
                    // Ctrl+T → 切换主题
                    ctrl && keyEvent.key == Key.T -> {
                        darkMode.value = !darkMode.value
                        true
                    }
                    // Ctrl+1/2/3 → 切换搜索模式
                    ctrl && keyEvent.key == Key.One -> {
                        viewModel.onSearchModeChange(SearchMode.VOICE_ONLY); true
                    }
                    ctrl && keyEvent.key == Key.Two -> {
                        viewModel.onSearchModeChange(SearchMode.ALL_CATEGORIES); true
                    }
                    ctrl && keyEvent.key == Key.Three -> {
                        viewModel.onSearchModeChange(SearchMode.FILE_SEARCH); true
                    }
                    // ↑/↓ → 在角色列表中导航
                    keyEvent.key == Key.DirectionUp && searchMode != SearchMode.FILE_SEARCH -> {
                        val groups = characterGroups
                        val current = selectedGroup
                        val idx = groups.indexOf(current)
                        if (idx > 0) {
                            viewModel.onSelectGroup(groups[idx - 1])
                            keyboardScope.launch { characterListState.animateScrollToItem(idx - 1) }
                        }
                        true
                    }
                    keyEvent.key == Key.DirectionDown && searchMode != SearchMode.FILE_SEARCH -> {
                        val groups = characterGroups
                        val current = selectedGroup
                        val idx = groups.indexOf(current)
                        if (idx < groups.size - 1) {
                            viewModel.onSelectGroup(groups[idx + 1])
                            keyboardScope.launch { characterListState.animateScrollToItem(idx + 1) }
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        MenuBar {
            MenuBarItem(
                content = { Text("功能") },
                items = {
                    MenuFlyoutItem(
                        onClick = {
                            val path = savePath.ifBlank { null }
                            if (path != null) {
                                val dir = java.io.File(path)
                                if (dir.exists()) java.awt.Desktop.getDesktop().open(dir)
                                else java.awt.Desktop.getDesktop().open(dir.parentFile ?: dir)
                            }
                        },
                        text = { Text("打开保存路径") }
                    )
                    MenuFlyoutSeparator()
                    MenuFlyoutItem(
                        onClick = { darkMode.value = !darkMode.value },
                        text = { Text(if (darkMode.value) "切换亮色主题" else "切换暗色主题") }
                    )
                    MenuFlyoutSeparator()
                    backdropOptions.forEach { (label, backdrop) ->
                        MenuFlyoutItem(
                            onClick = { backdropType.value = backdrop },
                            text = {
                                val current = backdropType.value
                                val isCurrent = current::class == backdrop::class
                                Text(
                                    text = "窗口效果：$label",
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isCurrent) FluentTheme.colors.text.text.primary else FluentTheme.colors.text.text.secondary
                                )
                            }
                        )
                    }
                },
            )
            MenuBarItem(
                content = { Text("关于") },
                items = {
                    MenuFlyoutItem(
                        onClick = { showDialog = true },
                        text = { Text("关于") }
                    )
                    MenuFlyoutSeparator()
                    MenuFlyoutItem(
                        onClick = { showShortcutsDialog = true },
                        text = { Text("键盘快捷键") }
                    )
                },
            )
        }
        // 上半部分：左右分栏
        Row(Modifier.weight(1f)) {

            // --- 左侧：导航与搜索 (Weight 0.3) ---
            Card(Modifier.weight(0.3f)) {
                Column(
                    Modifier.padding(12.dp)
                ) {
                    Text("搜索列表", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    // 搜索栏
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = searchKeyword,
                            onValueChange = { viewModel.onSearchKeywordChange(it) },
                            modifier = Modifier.weight(1f)
                                .focusRequester(searchFocusRequester)
                                .onKeyEvent { keyEvent ->
                                // 监听回车键按下
                                if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp) {
                                    performSearch()
                                    true
                                } else {
                                    false
                                }
                            },
                            trailing = {
                                TextBoxButton(onClick = {
                                    performSearch()
                                }) {
                                    TooltipBox(
                                        tooltip = { Text("开始搜索") }) {
                                        TextBoxButtonDefaults.SearchIcon()
                                    }
                                }
                            },
                            singleLine = true,
                            placeholder = { Text("搜索...") },
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    // 搜索模式切换
                    SegmentedControl(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            checked = searchMode == SearchMode.VOICE_ONLY,
                            onCheckedChanged = { viewModel.onSearchModeChange(SearchMode.VOICE_ONLY) },
                            position = SegmentedItemPosition.Start,
                            modifier = Modifier.weight(1f),
                            text = { Text("仅语音", fontSize = 12.sp) }
                        )
                        SegmentedButton(
                            checked = searchMode == SearchMode.ALL_CATEGORIES,
                            onCheckedChanged = { viewModel.onSearchModeChange(SearchMode.ALL_CATEGORIES) },
                            position = SegmentedItemPosition.Center,
                            modifier = Modifier.weight(1f),
                            text = { Text("全部分类", fontSize = 12.sp) }
                        )
                        SegmentedButton(
                            checked = searchMode == SearchMode.FILE_SEARCH,
                            onCheckedChanged = { viewModel.onSearchModeChange(SearchMode.FILE_SEARCH) },
                            position = SegmentedItemPosition.End,
                            modifier = Modifier.weight(1f),
                            text = { Text("文件搜索", fontSize = 12.sp) }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (searchMode == SearchMode.FILE_SEARCH) {
                        // 文件搜索结果列表
                        if (fileSearchResults.isEmpty() && !isSearching) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("输入关键词后搜索", color = Color.Gray, fontSize = 12.sp)
                            }
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "共 ${fileSearchResults.size} 个文件",
                                        fontSize = 12.sp, color = Color.Gray,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = { viewModel.selectAllFileSearchResults() },
                                        modifier = Modifier.height(24.dp)
                                    ) { Text("全选", fontSize = 11.sp) }
                                    Spacer(Modifier.width(4.dp))
                                    Button(
                                        onClick = { viewModel.clearFileSearchSelection() },
                                        modifier = Modifier.height(24.dp)
                                    ) { Text("清空", fontSize = 11.sp) }
                                }
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.fillMaxSize()) {
                                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                                    val adapter = androidx.compose.foundation.rememberScrollbarAdapter(listState)
                                    ScrollbarContainer(
                                        adapter = adapter,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            items(fileSearchResults) { (name, url) ->
                                                FileListItem(
                                                    name = name,
                                                    url = url,
                                                    isSelected = url in fileSearchSelectedUrls,
                                                    onToggle = { viewModel.toggleFileSearchSelection(url) },
                                                    playingUrl = fileSearchPlayingUrl,
                                                    loadingUrl = fileSearchLoadingUrl,
                                                    onPlayToggle = { u, isActive ->
                                                        if (isActive) {
                                                            AudioPlayerManager.stop()
                                                            fileSearchPlayingUrl = null
                                                        } else {
                                                            AudioPlayerManager.play(u)
                                                            fileSearchPlayingUrl = u
                                                        }
                                                    },
                                                    onImageClick = { u, n ->
                                                        fileSearchPreviewUrl = u
                                                        fileSearchPreviewName = n
                                                    },
                                                    thumbnailSize = 36.dp,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (characterGroups.isEmpty() && !isSearching) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("无数据", color = Color.Gray, fontSize = 12.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = characterListState,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(characterGroups) { group ->
                                    val isSelected = group == selectedGroup
                                    val bgColor =
                                        if (isSelected) FluentTheme.colors.control.secondary else Color.Transparent

                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(bgColor)
                                            .clickable {
                                                if (isDownloading) return@clickable
                                                viewModel.onSelectGroup(group)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CharacterAvatar(
                                            characterName = group.characterName,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            group.characterName,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            // --- 右侧：详情与配置 (Weight 0.7) ---
            Column(Modifier.weight(0.7f)) {

                // 1. 资源选择卡片
                Card(Modifier.weight(1f)) {
                    Column(
                        Modifier.padding(16.dp)
                    ) {
                        if (searchMode == SearchMode.FILE_SEARCH) {
                            // 文件搜索模式：显示已选文件汇总
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("文件搜索结果", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "已选 ${fileSearchSelectedUrls.size} / ${fileSearchResults.size} 个文件",
                                    color = Color.Gray, fontSize = 12.sp
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            if (fileSearchResults.isEmpty()) {
                                EmptyPlaceholder(
                                    icon = Icons.Regular.CursorClick,
                                    text = "在左侧搜索文件"
                                )
                            } else {
                                LazyColumn(Modifier.fillMaxSize().padding(4.dp)) {
                                    items(fileSearchResults.filter { it.second in fileSearchSelectedUrls }) { (name, _) ->
                                        Text(
                                            name,
                                            fontSize = 13.sp,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else if (selectedGroup == null) {
                            EmptyPlaceholder(
                                icon = Icons.Regular.CursorClick,
                                text = "请从左侧选择一个角色"
                            )
                        } else {
                            val group = selectedGroup!!

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(group.characterName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Spacer(Modifier.width(12.dp))
                                Text("含 ${subCategories.size} 个分类", color = Color.Gray, fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))

                                Button(
                                    onClick = { viewModel.checkAllCategories() },
                                    modifier = Modifier.height(28.dp)
                                ) { Text("全选", fontSize = 12.sp) }

                                Spacer(Modifier.width(8.dp))

                                Button(
                                    onClick = { viewModel.uncheckAllCategories() },
                                    modifier = Modifier.height(28.dp)
                                ) { Text("全不选", fontSize = 12.sp) }
                            }

                            Spacer(Modifier.height(12.dp))

                            // 分类列表容器
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                if (isScanningTree) {
                                    ProgressRing(
                                        modifier = Modifier.align(Alignment.Center),
                                        size = 48.dp
                                    )
                                } else {
                                    LazyColumn(Modifier.fillMaxSize().padding(4.dp)) {
                                        items(subCategories) { cat ->
                                            val name = cat.replace("Category:", "").replace("分类:", "")
                                            val isChecked = checkedCategories.contains(cat)
                                            val isRoot = cat == group.rootCategory

                                            // [新增] 计算该分类的状态显示文本
                                            val statusText = if (manualSelectionMap.containsKey(cat)) {
                                                val count = manualSelectionMap[cat]?.size ?: 0
                                                val total = categoryTotalCountMap[cat]
                                                if (total != null) "已选 $count / $total" else "已选 $count 项"
                                            } else {
                                                // 没有手动选择过，且被勾选 -> 默认全选
                                                if (isChecked) "默认全选" else ""
                                            }

                                            ContextMenuArea(items = {
                                                listOf(ContextMenuItem("选择文件...") {
                                                    viewModel.openFileDialog(cat)
                                                })
                                            }) {
                                                Row(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .combinedClickable(
                                                            onClick = {
                                                                viewModel.setCategoryChecked(cat, !isChecked)
                                                            },
                                                            onDoubleClick = {
                                                                if (!isDownloading) viewModel.openFileDialog(cat)
                                                            }
                                                        )
                                                        .padding(vertical = 6.dp, horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    CheckBox(
                                                        checked = isChecked,
                                                        onCheckStateChange = { viewModel.setCategoryChecked(cat, it) }
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(name + if (isRoot) " (主分类)" else "")

                                                    // [新增] 显示选择状态
                                                    Spacer(Modifier.weight(1f))
                                                    Text(statusText, fontSize = 12.sp, color = Color.Gray)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 2. 配置与操作卡片
                Card(Modifier) {
                    Column(
                        Modifier.padding(16.dp)
                    ) {
                        Text("下载配置", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.Bottom) {
                            TextField(
                                value = savePath,
                                onValueChange = { viewModel.onSavePathChange(it) },
                                header = { Text("保存路径", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(Modifier.width(8.dp))
                            TooltipBox(
                                tooltip = { Text("选择文件夹") }
                            ) {
                                Button(
                                    iconOnly = true,
                                    onClick = { jChoose { viewModel.onSavePathChange(it.absolutePath) } },
                                ) {
                                    Image(
                                        painter = rememberVectorPainter(Icons.Regular.FolderOpen),
                                        contentDescription = "Browse",
                                        colorFilter = ColorFilter.tint(FluentTheme.colors.text.text.primary),
                                    )
                                }
                            }

                            Spacer(Modifier.width(16.dp))

                            TextField(
                                value = maxConcurrencyStr,
                                onValueChange = { viewModel.onMaxConcurrencyChange(it) },
                                header = { Text("并发数", fontSize = 12.sp) },
                                modifier = Modifier.width(80.dp),
                                singleLine = true
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // 音频转换配置
                        val isVoiceOnly = searchMode == SearchMode.VOICE_ONLY
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Switcher(
                                checked = convertAfterDownload,
                                onCheckStateChange = { viewModel.onConvertAfterDownloadChange(it) },
                                text = if (isVoiceOnly) "下载完成后转换为 WAV 格式"
                                       else "下载完成后将 MP3 转换为 WAV"
                            )
                        }

                        if (convertAfterDownload) {
                            Spacer(Modifier.height(12.dp))

                            // 转换详情设置区域
                            SubtleBox(
                                modifier = Modifier.fillMaxWidth(),
                                padding = 12.dp
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // 非语音模式下的说明
                                if (!isVoiceOnly) {
                                    Text(
                                        "仅对 MP3 文件执行转换，其他格式（OGG、WAV 等）将跳过",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }

                                // 1. 格式设置
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ComboBox(
                                        header = "采样率",
                                        placeholder = "原采样率",
                                        selected = targetSampleRateIndex,
                                        items = SAMPLE_RATE_OPTIONS.map { sampleRateLabel(it) },
                                        onSelectionChange = { i, _ -> viewModel.onTargetSampleRateIndexChange(i) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ComboBox(
                                        header = "位深",
                                        placeholder = "16 bit",
                                        selected = targetBitDepthIndex,
                                        items = BIT_DEPTH_OPTIONS.map { bitDepthLabel(it) },
                                        onSelectionChange = { i, _ -> viewModel.onTargetBitDepthIndexChange(i) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // 2. 合并设置
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CheckBox(
                                        label = "合并导出的 WAV 文件",
                                        checked = mergeWav,
                                        onCheckStateChange = { viewModel.onMergeWavChange(it) }
                                    )

                                    if (mergeWav) {
                                        TextField(
                                            value = mergeWavMaxCountStr,
                                            onValueChange = { viewModel.onMergeWavMaxCountStrChange(it) },
                                            placeholder = { Text("0") },
                                            header = { Text("每组文件上限 (0=全部)", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }

                                // 3. 删除原始文件
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CheckBox(
                                        label = if (isVoiceOnly) "转换完成后删除原始 MP3"
                                                else "转换完成后删除原始 MP3（其他格式不受影响）",
                                        checked = deleteOriginalMp3,
                                        onCheckStateChange = { viewModel.onDeleteOriginalMp3Change(it) }
                                    )
                                }
                                } // Column
                            } // SubtleBox
                        }

                        Spacer(Modifier.height(16.dp))

                        // 大下载按钮
                        val isFileSearch = searchMode == SearchMode.FILE_SEARCH
                        val canDownload = if (isFileSearch) fileSearchSelectedUrls.isNotEmpty()
                                         else checkedCategories.isNotEmpty()
                        Button(
                            onClick = { viewModel.startDownload() },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            disabled = isDownloading || isScanningTree || !canDownload
                        ) {
                            Text(
                                when {
                                    isDownloading -> "正在下载中..."
                                    isFileSearch -> "开始下载 (${fileSearchSelectedUrls.size} 个文件)"
                                    else -> "开始下载 (${checkedCategories.size} 个分类)"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // === 底部：日志面板 ===
        Column(
            Modifier
                .height(150.dp) // 给多一点高度
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E1E))
        ) {
            // 标题栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = rememberVectorPainter(Icons.Regular.TextBulletListLtr),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.Gray),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("运行日志", color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                if (progressText.isNotEmpty()) {
                    Text(progressText, color = Color(0xFF61AFEF), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }

            // 进度条整合
            if (isDownloading || isScanningTree) {
                ProgressBar(progress = progress, modifier = Modifier.fillMaxWidth().height(2.dp))
            } else {
                Spacer(Modifier.height(1.dp).fillMaxWidth().background(Color(0xFF333333)))
            }

            // 日志内容
            TerminalOutputView(logLines, Modifier.fillMaxSize().background(Color.Transparent))
        }
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun KeyboardShortcutsDialog(onClose: () -> Unit) {
    val darkMode = LocalThemeState.current.value
    DialogWindow(
        onCloseRequest = onClose,
        title = "键盘快捷键",
        state = rememberDialogState(width = 480.dp, height = 440.dp),
        onKeyEvent = { keyEvent ->
            if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                onClose(); true
            } else false
        }
    ) {
        FluentTheme(
            colors = if (darkMode) darkColors() else lightColors(),
            useAcrylicPopup = true
        ) {
            Mica(modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text("键盘快捷键", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(16.dp))

                    val shortcuts = listOf(
                        "Ctrl + F" to "聚焦搜索框",
                        "Enter" to "执行搜索（搜索框聚焦时）",
                        "F5" to "重新搜索",
                        "Ctrl + D" to "开始下载",
                        "Ctrl + A" to "全选分类 / 全选文件",
                        "Ctrl + Shift + A" to "取消全选",
                        "Ctrl + T" to "切换深色 / 浅色主题",
                        "Ctrl + 1" to "切换至「仅语音」模式",
                        "Ctrl + 2" to "切换至「全部分类」模式",
                        "Ctrl + 3" to "切换至「文件搜索」模式",
                        "↑ / ↓" to "在角色列表中上下导航",
                        "" to "",
                        "文件列表弹窗：" to "",
                        "Ctrl + A" to "全选可见文件",
                        "Ctrl + Shift + A" to "清空选择",
                        "Enter" to "确认选择",
                        "Esc" to "关闭弹窗",
                    )

                    val listState = rememberLazyListState()
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        items(shortcuts) { (key, desc) ->
                            if (key.isEmpty()) {
                                Spacer(Modifier.height(8.dp))
                            } else if (desc.isEmpty()) {
                                // Section header
                                Text(
                                    key,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = FluentTheme.colors.text.text.secondary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                            } else {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .background(
                                                FluentTheme.colors.control.secondary,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .border(
                                                1.dp,
                                                FluentTheme.colors.stroke.card.default,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                            .widthIn(min = 160.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            key,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(desc, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = onClose) { Text("关闭") }
                    }
                }
            }
        }
    }
}

