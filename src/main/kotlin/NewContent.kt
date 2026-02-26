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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalFluentApi::class, ExperimentalFoundationApi::class)
@Composable
fun NewDownloaderContent() {
    val coroutineScope = rememberCoroutineScope()

    // --- 状态管理 ---
    var searchKeyword by remember { mutableStateOf("角色") }
    var isSearching by remember { mutableStateOf(false) }
    var voiceOnly by remember { mutableStateOf(true) }

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

    // 记录每个分类下用户【手动选择】的文件列表
    // Key: 分类名 (categoryName), Value: 选中的文件列表 List<Pair<Name, Url>>
    // 如果 Map 中不存在该 key，但 checkedCategories 包含它，则视为【默认全选】
    val manualSelectionMap = remember { mutableStateMapOf<String, List<Pair<String, String>>>() }

    // 缓存分类下的总文件数 (用于 UI 显示 "已选 5/10")
    // Key: 分类名, Value: 总数
    val categoryTotalCountMap = remember { mutableStateMapOf<String, Int>() }

    // 日志
    val logLines = remember { mutableStateListOf("欢迎使用卡拉彼丘 Wiki 语音下载器。") }
    fun addLog(msg: String) {
        logLines.add(msg)
        if (logLines.size > 100) logLines.removeAt(0)
    }

    // --- 弹窗状态 ---
    var showFileDialog by remember { mutableStateOf(false) }
    var dialogCategoryName by remember { mutableStateOf("") }
    val dialogFileList = remember { mutableStateListOf<Pair<String, String>>() }
    var dialogIsLoading by remember { mutableStateOf(false) }
    // 弹窗初始化时已选中的文件 (用于回显)
    val dialogInitialSelection = remember { mutableStateListOf<String>() }

    var scanJob by remember { mutableStateOf<Job?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

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
            onClose = { showFileDialog = false },
            onConfirm = { selectedFiles ->
                // 点击确认不再下载，而是保存选择状态
                showFileDialog = false

                // 更新手动选择记录
                manualSelectionMap[dialogCategoryName] = selectedFiles
                // 更新总数缓存 (虽然 fetch 时已经更新过，这里保险起见)
                categoryTotalCountMap[dialogCategoryName] = dialogFileList.size

                // 如果用户选了文件，自动勾选该分类
                if (selectedFiles.isNotEmpty() && !checkedCategories.contains(dialogCategoryName)) {
                    checkedCategories.add(dialogCategoryName)
                }
                // 如果用户清空了选择，是否取消勾选分类？(可选，这里暂不自动取消)
            }
        )
    }

    // 缓存
    val categoryCache = remember { mutableStateMapOf<String, List<String>>() }
    // 抽取的搜索函数
    val performSearch = {
        if (searchKeyword.isNotBlank()) {
            isSearching = true
            selectedGroup = null
            characterGroups.clear()
            // 取消上一次搜索和扫描任务
            searchJob?.cancel()
            scanJob?.cancel()

            searchJob = coroutineScope.launch {
                try {
                    addLog("正在搜索: $searchKeyword ${if (voiceOnly) "(仅语音)" else "(全部类型)"}...")
                    val res = WikiEngine.searchAndGroupCharacters(searchKeyword, voiceOnly)
                    characterGroups.addAll(res)
                    addLog("搜索完成，找到 ${res.size} 个角色。")
                } catch (_: CancellationException) {
                    // 忽略取消
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
                        onCheckedChanged = { voiceOnly = it },
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
                                                    dialogCategoryName = cat // 保存完整的 key
                                                    dialogFileList.clear()
                                                    dialogInitialSelection.clear()
                                                    showFileDialog = true
                                                    dialogIsLoading = true

                                                    coroutineScope.launch {
                                                        try {
                                                            // 1. 获取文件列表
                                            val files = WikiEngine.fetchFilesInCategory(cat, audioOnly = voiceOnly)
                                                            dialogFileList.addAll(files)
                                                            categoryTotalCountMap[cat] = files.size // 更新总数缓存

                                                            // 2. 准备回显状态
                                                            if (manualSelectionMap.containsKey(cat)) {
                                                                // 如果有手动记录，就用手动记录的
                                                                val selectedUrls =
                                                                    manualSelectionMap[cat]!!.map { it.second }
                                                                dialogInitialSelection.addAll(selectedUrls)
                                                            } else {
                                                                // 如果没有手动记录，默认全选
                                                                dialogInitialSelection.addAll(files.map { it.second })
                                                            }
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
                                                        .clip(RoundedCornerShape(4.dp))
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
                                val targetDir = File(
                                    savePath,
                                    WikiEngine.sanitizeFileName(selectedGroup?.characterName ?: "Unknown")
                                )
                                val concurrency = maxConcurrencyStr.toIntOrNull() ?: 16

                                coroutineScope.launch {
                                    try {
                                        addLog("开始处理下载任务...")

                                        // 遍历所有勾选的分类
                                        // 1. 如果 manualSelectionMap 有记录 -> 直接下载这些文件
                                        // 2. 如果没有记录 -> 说明是默认全选 -> 需要先 fetch 再下载

                                        // 收集所有需要下载的文件 (去重)
                                        val finalDownloadList = mutableListOf<Pair<String, String>>()

                                        for (cat in checkedCategories) {
                                            if (manualSelectionMap.containsKey(cat)) {
                                                // 也就是用户手动选过的
                                                val files = manualSelectionMap[cat] ?: emptyList()
                                                finalDownloadList.addAll(files)
                                                addLog(
                                                    "[${
                                                        cat.replace(
                                                            "Category:",
                                                            ""
                                                        )
                                                    }] 使用手动选择 (${files.size}项)"
                                                )
                                            } else {
                                                // 用户没动过，默认全选，需要现场获取
                                                addLog("正在扫描 [${cat.replace("Category:", "")}] ...")
                                                val files = WikiEngine.fetchFilesInCategory(cat, audioOnly = voiceOnly)
                                                finalDownloadList.addAll(files)
                                            }
                                        }

                                        // 去重
                                        val uniqueList = finalDownloadList.distinctBy { it.second }

                                        if (uniqueList.isEmpty()) {
                                            addLog("没有文件需要下载。")
                                        } else {
                                            addLog("共 ${uniqueList.size} 个文件，开始下载...")
                                            WikiEngine.downloadSpecificFiles(
                                                files = uniqueList,
                                                saveDir = targetDir,
                                                maxConcurrency = concurrency,
                                                onLog = { addLog(it) },
                                                onProgress = { current, total, name ->
                                                    progress = current.toFloat() / total
                                                    progressText = "$current / $total : $name"
                                                }
                                            )
                                            addLog("全部下载完成！")
                                        }

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

    LaunchedEffect(Unit) {
        WikiEngine.preloadCharacterNames()
        delay(300)
        performSearch()
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
