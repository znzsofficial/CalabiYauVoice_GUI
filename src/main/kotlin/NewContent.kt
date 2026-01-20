import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Mica
import io.github.composefluent.component.*
import io.github.composefluent.darkColors
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.*
import io.github.composefluent.lightColors
import io.github.composefluent.surface.Card
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalFluentApi::class, ExperimentalFoundationApi::class)
@Composable
fun NewDownloaderContent() {
    val coroutineScope = rememberCoroutineScope()

    // --- 状态管理 ---
    var searchKeyword by remember { mutableStateOf("语音") }
    var isSearching by remember { mutableStateOf(false) }

    // 角色列表
    val characterGroups = remember { mutableStateListOf<WikiEngine.CharacterGroup>() }
    var selectedGroup by remember { mutableStateOf<WikiEngine.CharacterGroup?>(null) }

    // 右侧：选中的角色加载出来的所有子分类
    val subCategories = remember { mutableStateListOf<String>() }
    val checkedCategories = remember { mutableStateListOf<String>() }
    var isScanningTree by remember { mutableStateOf(false) }

    // 下载配置
    var savePath by remember { mutableStateOf("$home\\角色语音") }
    var maxConcurrencyStr by remember { mutableStateOf("16") }
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var progressText by remember { mutableStateOf("") }

    // 日志
    val logLines = remember { mutableStateListOf("欢迎使用新版 Wiki 语音下载器。") }
    fun addLog(msg: String) {
        logLines.add(msg)
        if (logLines.size > 100) logLines.removeAt(0)
    }

    // --- 弹窗状态 ---
    var showFileDialog by remember { mutableStateOf(false) }
    var dialogCategoryName by remember { mutableStateOf("") }
    val dialogFileList = remember { mutableStateListOf<Pair<String, String>>() }
    var dialogIsLoading by remember { mutableStateOf(false) }
    var scanJob by remember { mutableStateOf<Job?>(null) } // 用于管理分类扫描任务

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
                useAcrylicPopup = true,
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
            isLoading = dialogIsLoading,
            onClose = { showFileDialog = false },
            onDownload = { selectedFiles ->
                showFileDialog = false
                isDownloading = true
                val charName = selectedGroup?.characterName ?: "Unknown"
                val targetDir = File(savePath, WikiEngine.sanitizeFileName(charName))
                val concurrency = maxConcurrencyStr.toIntOrNull() ?: 16

                coroutineScope.launch {
                    try {
                        addLog("开始下载选中的 ${selectedFiles.size} 个文件...")
                        WikiEngine.downloadSpecificFiles(
                            files = selectedFiles,
                            saveDir = targetDir,
                            maxConcurrency = concurrency,
                            onLog = { addLog(it) },
                            onProgress = { current, total, name ->
                                progress = current.toFloat() / total
                                progressText = "$current / $total : $name"
                            }
                        )
                        addLog("特定文件下载完成！")
                    } catch (e: Exception) {
                        addLog("下载出错: ${e.message}")
                    } finally {
                        isDownloading = false
                        progress = 0f
                        progressText = ""
                    }
                }
            }
        )
    }

    // 缓存
    val categoryCache = remember { mutableStateMapOf<String, List<String>>() }
    // 抽取的搜索函数
    val performSearch = {
        if (searchKeyword.isNotBlank() && !isSearching) {
            isSearching = true
            selectedGroup = null
            characterGroups.clear()
            // 搜索前取消之前的扫描任务，防止回调冲突
            scanJob?.cancel()

            coroutineScope.launch {
                try {
                    addLog("正在搜索: $searchKeyword ...")
                    val res = WikiEngine.searchAndGroupCharacters(searchKeyword)
                    characterGroups.addAll(res)
                    addLog("搜索完成，找到 ${res.size} 个角色。")
                } catch (e: Exception) {
                    addLog("搜索失败: ${e.message}")
                } finally {
                    isSearching = false
                }
            }
        }
    }
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
    Column(Modifier.fillMaxSize().padding(16.dp)) {
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
                    Text("角色列表", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    // 搜索栏
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = searchKeyword,
                            onValueChange = { searchKeyword = it },
                            modifier = Modifier.weight(1f).onKeyEvent { keyEvent ->
                                // 监听回车键按下
                                if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp) {
                                    performSearch()
                                    true
                                } else {
                                    false
                                }
                            },
                            singleLine = true,
                            placeholder = { Text("搜索...") },
                            leadingIcon = {
                                Image(
                                    painter = rememberVectorPainter(Icons.Regular.Search),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(FluentTheme.colors.text.text.secondary),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        TooltipBox(
                            tooltip = { Text("开始搜索") }
                        ) {
                            Button(
                                onClick = {
                                    performSearch()
                                },
                                disabled = isSearching || isDownloading,
                                iconOnly = true,
                            ) {
                                Image(
                                    painter = rememberVectorPainter(Icons.Regular.ArrowForward),
                                    contentDescription = "Go",
                                    colorFilter = ColorFilter.tint(FluentTheme.colors.text.text.primary),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 角色列表
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
                                            // 1. 取消上一次的扫描任务
                                            scanJob?.cancel()
                                            selectedGroup = group
                                            subCategories.clear()
                                            checkedCategories.clear()
                                            // 2. 检查缓存
                                            val cached = categoryCache[group.rootCategory]
                                            if (cached != null) {
                                                subCategories.addAll(cached)
                                                checkedCategories.addAll(cached)
                                                // 别忘了更新左侧列表的显示数量逻辑(如果有的话)
                                            } else {
                                                isScanningTree = true
                                                // 3. 启动新任务
                                                scanJob = coroutineScope.launch {
                                                    try {
                                                        addLog("正在获取 [${group.characterName}] 的所有分类...")
                                                        val tree = WikiEngine.scanCategoryTree(group.rootCategory)

                                                        // 存入缓存
                                                        categoryCache[group.rootCategory] = tree

                                                        subCategories.addAll(tree)
                                                        checkedCategories.addAll(tree)

                                                        // 更新列表显示状态
                                                        val index = characterGroups.indexOf(group)
                                                        if (index != -1) {
                                                            characterGroups[index] = group.copy(subCategories = tree)
                                                            selectedGroup = characterGroups[index]
                                                        }
                                                        addLog("获取完成，共 ${tree.size} 个分类。")
                                                    } catch (_: CancellationException) {
                                                        // 忽略取消异常
                                                    } catch (e: Exception) {
                                                        addLog("获取分类失败: ${e.message}")
                                                    } finally {
                                                        isScanningTree = false
                                                    }
                                                }
                                            }
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
                                    onClick = {
                                        checkedCategories.clear()
                                        checkedCategories.addAll(subCategories)
                                    },
                                    modifier = Modifier.height(28.dp)
                                ) { Text("全选", fontSize = 12.sp) }

                                Spacer(Modifier.width(8.dp))

                                Button(
                                    onClick = { checkedCategories.clear() },
                                    modifier = Modifier.height(28.dp)
                                ) { Text("全不选", fontSize = 12.sp) }
                            }

                            Spacer(Modifier.height(12.dp))

                            // 分类列表容器
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .border(1.dp, FluentTheme.colors.stroke.card.default, RoundedCornerShape(4.dp))
                                    .background(FluentTheme.colors.background.layer.default)
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

                                            ContextMenuArea(items = {
                                                listOf(ContextMenuItem("查看文件列表...") {
                                                    dialogCategoryName = name
                                                    dialogFileList.clear()
                                                    showFileDialog = true
                                                    dialogIsLoading = true

                                                    coroutineScope.launch {
                                                        try {
                                                            val files = WikiEngine.fetchFilesInCategory(cat)
                                                            dialogFileList.addAll(files)
                                                        } catch (e: Exception) {
                                                            addLog("加载失败: ${e.message}")
                                                        } finally {
                                                            dialogIsLoading = false
                                                        }
                                                    }
                                                })
                                            }) {
                                                Row(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            if (isChecked) checkedCategories.remove(cat) else checkedCategories.add(
                                                                cat
                                                            )
                                                        }
                                                        .padding(vertical = 6.dp, horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    CheckBox(
                                                        checked = isChecked,
                                                        onCheckStateChange = {
                                                            if (it) checkedCategories.add(cat) else checkedCategories.remove(
                                                                cat
                                                            )
                                                        }
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(name + if (isRoot) " (主分类)" else "")
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
                                onValueChange = { savePath = it },
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
                                    onClick = { jChoose { savePath = it.absolutePath } },
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
                                onValueChange = { if (it.all { c -> c.isDigit() }) maxConcurrencyStr = it },
                                header = { Text("并发数", fontSize = 12.sp) },
                                modifier = Modifier.width(80.dp),
                                singleLine = true
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // 大下载按钮
                        Button(
                            onClick = {
                                if (checkedCategories.isEmpty()) return@Button
                                isDownloading = true
                                val targetDir =
                                    File(
                                        savePath,
                                        WikiEngine.sanitizeFileName(selectedGroup?.characterName ?: "Unknown")
                                    )
                                val concurrency = maxConcurrencyStr.toIntOrNull() ?: 16

                                coroutineScope.launch {
                                    try {
                                        addLog("开始批量下载...")
                                        WikiEngine.downloadFiles(
                                            categories = checkedCategories.toList(),
                                            saveDir = targetDir,
                                            maxConcurrency = concurrency,
                                            onLog = { addLog(it) },
                                            onProgress = { current, total, name ->
                                                progress = current.toFloat() / total
                                                progressText = "$current / $total : $name"
                                            }
                                        )
                                        addLog("批量下载完成！")
                                    } catch (e: Exception) {
                                        addLog("中断: ${e.message}")
                                    } finally {
                                        isDownloading = false
                                        progress = 0f
                                        progressText = ""
                                    }
                                }
                            },
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
    isLoading: Boolean,
    onClose: () -> Unit,
    onDownload: (List<Pair<String, String>>) -> Unit
) {
    val selectedUrls = remember { mutableStateListOf<String>() }

    LaunchedEffect(files) {
        if (files.isNotEmpty() && selectedUrls.isEmpty()) {
            selectedUrls.addAll(files.map { it.second })
        }
    }

    DialogWindow(
        onCloseRequest = onClose,
        title = "文件列表: $title",
        state = rememberDialogState(width = 700.dp, height = 600.dp)
    ) {
        FluentTheme(colors = if (isSystemInDarkTheme()) darkColors() else lightColors()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(FluentTheme.colors.background.layer.default)
                    .padding(16.dp)
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))

                // 工具栏
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        selectedUrls.clear()
                        selectedUrls.addAll(files.map { it.second })
                    }) { Text("全选") }

                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { selectedUrls.clear() }) { Text("清空") }

                    Spacer(Modifier.width(16.dp))
                    Text("快速筛选:", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.width(4.dp))

                    listOf("CN", "JP", "EN").forEach { lang ->
                        Button(onClick = {
                            selectedUrls.clear()
                            val targets = files.filter { (name, _) ->
                                val n = name.uppercase()
                                n.endsWith(lang) || n.contains("$lang.")
                            }.map { it.second }
                            selectedUrls.addAll(targets)
                        }) { Text(lang) }
                        Spacer(Modifier.width(4.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))

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
                    } else if (files.isEmpty()) {
                        Text("无文件", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                    } else {
                        LazyColumn(Modifier.fillMaxSize().padding(4.dp)) {
                            items(files) { (name, url) ->
                                val isSelected = selectedUrls.contains(url)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
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

                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onClose) { Text("取消") }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val finalSelection = files.filter { selectedUrls.contains(it.second) }
                            onDownload(finalSelection)
                        },
                        disabled = selectedUrls.isEmpty() || isLoading
                    ) {
                        Text("下载选中 (${selectedUrls.size})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


@Composable
fun AboutContent() {
    val darkMode = LocalThemeState.current
    FluentTheme(colors = if (darkMode.value) darkColors() else lightColors()) {
        Mica(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 标题和版本
                Text(
                    text = "卡拉彼丘 Wiki 语音下载器",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = FluentTheme.colors.text.text.primary
                    )
                )
                Text(
                    text = "Version 1.1.0",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = FluentTheme.colors.text.text.secondary
                    )
                )

                Spacer(Modifier.height(8.dp))

                // 2. 软件介绍文案
                Text(
                    text = "一款基于 Kotlin Compose Desktop 开发的现代化工具，采用 Fluent Design 设计风格。旨在为卡拉彼丘玩家提供便捷、流畅的 Wiki 语音资源提取与下载体验。",
                    style = TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        color = FluentTheme.colors.text.text.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.weight(1f)) // 把链接推到底部

                // 3. 链接区域
                Text(
                    text = "相关链接",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FluentTheme.colors.text.text.secondary
                    )
                )

                // 链接横向排列
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HyperlinkButton(navigateUri = "https://github.com/znzsofficial/CalabiyauWikiVoice") {
                        Text("核心脚本源码")
                    }
                    Text("|", color = FluentTheme.colors.text.text.disabled)
                    HyperlinkButton(navigateUri = "https://github.com/znzsofficial/CalabiYauVoice_GUI") {
                        Text("GUI 开源仓库")
                    }
                }

                // 4. 版权/落款
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "© 2025 Developed by NekoLaska",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = FluentTheme.colors.text.text.disabled
                    )
                )
            }
        }
    }
}
