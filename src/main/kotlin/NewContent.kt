import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Mica
import io.github.composefluent.component.*
import io.github.composefluent.darkColors
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.AppsList
import io.github.composefluent.icons.regular.CursorClick
import io.github.composefluent.icons.regular.FolderOpen
import io.github.composefluent.icons.regular.Search
import io.github.composefluent.lightColors
import io.github.composefluent.surface.Card

@OptIn(ExperimentalFluentApi::class, ExperimentalFoundationApi::class)
@Composable
fun NewDownloaderContent() {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { MainViewModel(coroutineScope) }

    // --- 状态管理 (来自 ViewModel) ---
    val searchKeyword by viewModel.searchKeyword.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val voiceOnly by viewModel.voiceOnly.collectAsState()

    val characterGroups by viewModel.characterGroups.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()

    val subCategories by viewModel.subCategories.collectAsState()
    val checkedCategories by viewModel.checkedCategories.collectAsState()
    val isScanningTree by viewModel.isScanningTree.collectAsState()

    val savePath by viewModel.savePath.collectAsState()
    val maxConcurrencyStr by viewModel.maxConcurrencyStr.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val progressText by viewModel.progressText.collectAsState()

    val manualSelectionMap by viewModel.manualSelectionMap.collectAsState()
    val categoryTotalCountMap by viewModel.categoryTotalCountMap.collectAsState()

    val showFileDialog by viewModel.showFileDialog.collectAsState()
    val dialogCategoryName by viewModel.dialogCategoryName.collectAsState()
    val dialogFileList by viewModel.dialogFileList.collectAsState()
    val dialogIsLoading by viewModel.dialogIsLoading.collectAsState()
    val dialogInitialSelection by viewModel.dialogInitialSelection.collectAsState()

    val logLines by viewModel.logLines.collectAsState()

    // --- 旧版窗口是否打开 ---
    var isLegacyWindowOpen by remember { mutableStateOf(false) }
    val darkMode = LocalThemeState.current

    // === 旧版窗口 (作为附带功能存在) ===
    if (isLegacyWindowOpen) {
        Window(
            onCloseRequest = { isLegacyWindowOpen = false },
            title = "旧版下载器 (HTML解析)",
            icon = painterResource("icon.png"),
            state = rememberWindowState(width = 800.dp, height = 700.dp)
        ) {
            FluentTheme(
                colors = if (darkMode.value) darkColors() else lightColors(),
            ) {
                Mica(Modifier.fillMaxSize()) {
                    LegacyContent()
                }
            }
        }
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

    val performSearch = { viewModel.performSearch() }
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        DialogWindow(
            onCloseRequest = { showDialog = false },
            state = rememberDialogState(
                position = WindowPosition(Alignment.Center),
                width = 450.dp,
                height = 320.dp
            ),
            title = "关于",
            resizable = false,
            content = {
                AboutContent()
            }
        )
    }

    // === 主界面布局 ===
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        MenuBar {
            MenuBarItem(
                content = { Text("功能") },
                items = {
                    MenuFlyoutItem(
                        onClick = { isLegacyWindowOpen = true },
                        text = { Text("打开旧版网页爬虫界面") }
                    )
                    MenuFlyoutItem(
                        onClick = { darkMode.value = !darkMode.value },
                        text = { Text(if (darkMode.value) "切换亮色主题" else "切换暗色主题") }
                    )
                },
            )
            MenuBarItem(
                content = { Text("关于") },
                items = {
                    MenuFlyoutItem(
                        onClick = { showDialog = true },
                        text = { Text("关于") }
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
                            modifier = Modifier.weight(1f).onKeyEvent { keyEvent ->
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
                    // 语音模式开关
                    ToggleButton(
                        checked = voiceOnly,
                        onCheckedChanged = { viewModel.onVoiceOnlyChange(it) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (voiceOnly) "仅搜索语音" else "搜索全部类型",
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    if (characterGroups.isEmpty() && !isSearching) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("无数据", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
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
                                        modifier = Modifier.size(28.dp) // 头像大小
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

            Spacer(Modifier.width(16.dp))

            // --- 右侧：详情与配置 (Weight 0.7) ---
            Column(Modifier.weight(0.7f)) {

                // 1. 资源选择卡片
                Card(Modifier.weight(1f)) {
                    Column(
                        Modifier.padding(16.dp)
                    ) {
                        if (selectedGroup == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(
                                        painter = rememberVectorPainter(Icons.Regular.CursorClick),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(Color.Gray),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text("请从左侧选择一个角色", color = Color.Gray)
                                }
                            }
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
                                                        .clickable {
                                                            viewModel.setCategoryChecked(cat, !isChecked)
                                                        }
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

                        // 大下载按钮
                        Button(
                            onClick = { viewModel.startDownload() },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            disabled = isDownloading || isScanningTree || checkedCategories.isEmpty()
                        ) {
                            Text(
                                if (isDownloading) "正在下载中..." else "开始下载 (${checkedCategories.size} 个分类)",
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
                    painter = rememberVectorPainter(Icons.Regular.AppsList),
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

/**
 * 文件选择弹窗组件
 */
@OptIn(ExperimentalFluentApi::class)
@Composable
fun FileSelectionDialog(
    title: String,
    files: List<Pair<String, String>>,
    initialSelection: List<String>, // [新增] 初始选中的 URL
    isLoading: Boolean,
    onClose: () -> Unit,
    onConfirm: (List<Pair<String, String>>) -> Unit // [修改] 改为确认回调
) {
    val selectedUrls = remember { mutableStateListOf<String>() }
    // 搜索关键词
    var searchKeyword by remember { mutableStateOf("") }
    val darkMode = LocalThemeState.current.value

    // 初始化选中状态
    LaunchedEffect(files, initialSelection) {
        if (files.isNotEmpty()) {
            selectedUrls.clear()
            // 如果 initialSelection 为空且还没加载过，可能是第一次打开默认全选
            // 但因为我们在外部已经控制了 initialSelection 的逻辑（有记录传记录，无记录传全选），
            // 所以这里直接 addAll 即可
            selectedUrls.addAll(initialSelection)
        }
    }

    val filteredFiles = remember(files, searchKeyword) {
        if (searchKeyword.isBlank()) {
            files
        } else {
            files.filter { (name, _) ->
                name.contains(searchKeyword, ignoreCase = true)
            }
        }
    }

    DialogWindow(
        onCloseRequest = onClose,
        title = "文件列表: $title",
        state = rememberDialogState(width = 700.dp, height = 650.dp)
    ) {
        FluentTheme(colors = if (darkMode) darkColors() else lightColors()) {
            Mica(modifier = Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))

                    // === 工具栏 ===
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            val newUrls = filteredFiles.map { it.second }
                            newUrls.forEach { if (!selectedUrls.contains(it)) selectedUrls.add(it) }
                        }) { Text("全选可见") }

                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { selectedUrls.clear() }) { Text("清空") }

                        Spacer(Modifier.width(16.dp))
                        Text("选择后缀:", fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.width(4.dp))

                        listOf("CN", "JP", "EN").forEach { lang ->
                            // 先计算匹配的目标 URL 列表（只做一次遍历）
                            val targets = files.map { (name, url) -> name.uppercase() to url }
                                .filter { (n, _) -> n.endsWith(lang) || n.contains("$lang.") }
                                .map { it.second }

                            // 勾选状态：当且仅当存在匹配项并且所有匹配项都被选中时才为 true
                            val isChecked = targets.isNotEmpty() && targets.all { it in selectedUrls }

                            CheckBox(
                                label = lang,
                                checked = isChecked,
                            ) { checked ->
                                if (checked) {
                                    // 只添加尚未存在的 URL，避免重复
                                    val toAdd = targets.filterNot { it in selectedUrls }
                                    selectedUrls.addAll(toAdd)
                                } else {
                                    // 一次性移除所有匹配项
                                    selectedUrls.removeAll { it in targets }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // 搜索框
                    TextField(
                        value = searchKeyword,
                        onValueChange = { searchKeyword = it },
                        placeholder = { Text("搜索文件名...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Image(
                                painter = rememberVectorPainter(Icons.Regular.Search),
                                contentDescription = "Search",
                                colorFilter = ColorFilter.tint(FluentTheme.colors.text.text.secondary),
                                modifier = Modifier.size(16.dp)
                            )
                        },
                    )

                    Spacer(Modifier.height(12.dp))

                    // === 列表区域 ===
                    Box(
                        Modifier.weight(1f)
                            .fillMaxWidth()
                            .border(1.dp, FluentTheme.colors.stroke.card.default, RoundedCornerShape(4.dp))
                            .background(FluentTheme.colors.control.secondary)
                    ) {
                        if (isLoading) {
                            Column(
                                Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ProgressRing(size = 48.dp)
                                Spacer(Modifier.height(8.dp))
                                Text("正在加载列表...")
                            }
                        } else if (filteredFiles.isEmpty()) {
                            val emptyText = if (files.isEmpty()) "无文件" else "无搜索结果"
                            Text(emptyText, modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                        } else {
                            // 使用 ScrollbarContainer
                            val listState = rememberLazyListState()
                            val adapter = rememberScrollbarAdapter(listState)

                            ScrollbarContainer(
                                adapter = adapter,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    // 稍微加一点 padding，避免内容贴边
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    items(filteredFiles) { (name, url) ->
                                        val isSelected = selectedUrls.contains(url)
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable {
                                                    if (isSelected) selectedUrls.remove(url) else selectedUrls.add(url)
                                                }
                                                .padding(vertical = 2.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CheckBox(checked = isSelected, onCheckStateChange = {
                                                if (it) selectedUrls.add(url) else selectedUrls.remove(url)
                                            })
                                            Spacer(Modifier.width(8.dp))
                                            Text(name, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // === 底部按钮 ===
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            "已选 ${selectedUrls.size} / ${files.size}",
                            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 16.dp),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

                        Button(onClick = onClose) { Text("取消") }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                val finalSelection = files.filter { selectedUrls.contains(it.second) }
                                onConfirm(finalSelection) // [修改] 调用确认回调
                            },
                            disabled = isLoading // 即使选空也可以确认（代表不下载该分类下的任何文件）
                        ) {
                            Text("确认选择", fontWeight = FontWeight.Bold) // [修改] 文案
                        }
                    }
                }
            }
        }
    }
}
