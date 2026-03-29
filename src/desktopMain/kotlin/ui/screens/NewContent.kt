package ui.screens

import LocalAppStore
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.mayakapps.compose.windowstyler.WindowBackdrop
import portrait.*
import data.WikiEngine
import data.WikiUserApi
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.*
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.*
import io.github.composefluent.surface.Card
import kotlinx.coroutines.launch
import ui.components.*
import ui.components.ComboBox
import util.*
import viewmodel.MainViewModel
import data.SearchMode
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalFluentApi::class, ExperimentalFoundationApi::class)
@Composable
fun NewDownloaderContent() {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { MainViewModel(coroutineScope) }

    // --- 全局用户状态 ---
    val currentUser by WikiUserApi.currentUser.collectAsState()
    val isUserLoggedIn = currentUser?.isLoggedIn == true

    // --- 状态管理 (来自 ViewModel) ---
    val searchKeyword by viewModel.searchKeyword.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()

    val characterGroups by viewModel.characterGroups.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()

    val fileSearchResults by viewModel.fileSearchResults.collectAsState()
    val fileSearchSelectedUrls by viewModel.fileSearchSelectedUrls.collectAsState()

    val portraitCharacters by viewModel.portraitCharacters.collectAsState()
    val selectedPortraitCharacter by viewModel.selectedPortraitCharacter.collectAsState()
    val portraitCostumes: List<PortraitCostume> by viewModel.portraitCostumes.collectAsState()
    val selectedPortraitCostumeKey by viewModel.selectedPortraitCostumeKey.collectAsState()
    val isPortraitLoading by viewModel.isPortraitLoading.collectAsState()

    // 文件搜索列表 - 音频播放状态
    var fileSearchPlayingUrl by remember { mutableStateOf<String?>(null) }
    var fileSearchLoadingUrl by remember { mutableStateOf<String?>(null) }
    // 文件搜索列表 - 图片预览状态
    var fileSearchPreviewUrl by remember { mutableStateOf<String?>(null) }
    var fileSearchPreviewName by remember { mutableStateOf("") }
    // 注册 AudioPlayerManager 播放结束 & 加载状态回调（文件搜索列表用）
    DisposableEffect(Unit) {
        val stoppedListener: (String) -> Unit = { url ->
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                if (fileSearchPlayingUrl == url) fileSearchPlayingUrl = null
            }
        }
        val loadingListener: (String, Boolean) -> Unit = { url, loading ->
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
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

    val appStore = LocalAppStore.current
    val darkMode = appStore.darkMode
    val backdropType = appStore.backdropType
    val isWin11 = appStore.isWin11
    val canUseNonWin11Backdrop = appStore.canUseNonWin11Backdrop

    // backdrop 选项列表：名称 → 值（null 表示恢复默认渐变背景）
    val backdropOptions: List<Pair<String, WindowBackdrop?>> = remember(isWin11, canUseNonWin11Backdrop) {
        when {
            isWin11 -> listOf(
                "Tabbed" to WindowBackdrop.Tabbed,
                "Mica" to WindowBackdrop.Mica,
                "Acrylic" to WindowBackdrop.Acrylic(Color.Transparent),
                "Aero" to WindowBackdrop.Aero,
                "Transparent" to WindowBackdrop.Transparent,
                "默认" to null,
            )

            canUseNonWin11Backdrop -> listOf(
                "Acrylic" to WindowBackdrop.Acrylic(Color.Transparent),
                "Aero" to WindowBackdrop.Aero,
                "Transparent" to WindowBackdrop.Transparent,
                "默认" to null,
            )

            else -> emptyList()
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

    val performSearch = viewModel::performSearch
    var showDialog by remember { mutableStateOf(false) }
    var showShortcutsDialog by remember { mutableStateOf(false) }
    var showUserInfoDialog by remember { mutableStateOf(false) }
    var showConverterWindow by remember { mutableStateOf(false) }
    var showLogWindow by remember { mutableStateOf(false) }
    var showWikiBrowser by remember { mutableStateOf(false) }
    var isRefreshingUser by remember { mutableStateOf(false) }
    var userQuickActionMessage by remember { mutableStateOf<String?>(null) }

    fun savePortraitAsset(asset: PortraitAsset, baseDir: File = File(savePath.ifBlank { AppPrefs.savePath }, "立绘列表")) {
        coroutineScope.launch {
            try {
                viewModel.addLog("正在保存立绘图片: ${asset.title}")
                WikiEngine.downloadSpecificFiles(
                    files = listOf(asset.title to asset.url),
                    saveDir = baseDir,
                    maxConcurrency = 1,
                    onLog = { viewModel.addLog(it) },
                    onProgress = { _, _, name ->
                        viewModel.addLog("图片已保存: $name -> ${baseDir.absolutePath}")
                    }
                )
            } catch (e: Exception) {
                viewModel.addLog("保存立绘失败: ${e.message}")
            }
        }
    }

    fun savePortraitAssetAs(asset: PortraitAsset) {
        jChoose { directory -> savePortraitAsset(asset, directory) }
    }

    fun refreshCurrentUser() {
        coroutineScope.launch {
            isRefreshingUser = true
            try {
                when (val result = WikiUserApi.fetchCurrentUserInfoResult()) {
                    is WikiUserApi.ApiResult.Success -> {
                        WikiUserApi.updateCurrentUser(result.value)
                        userQuickActionMessage = when {
                            result.value == null -> "未读取到账号信息"
                            result.value.isLoggedIn -> "已刷新账号信息：${result.value.name}"
                            else -> "当前 Cookie 已失效或未登录"
                        }
                    }
                    is WikiUserApi.ApiResult.Error -> {
                        userQuickActionMessage = "刷新失败：${result.message}"
                    }
                }
            } finally {
                isRefreshingUser = false
            }
        }
    }

    LaunchedEffect(currentUser?.id, currentUser?.name) {
        if (currentUser?.isLoggedIn != true) {
            userQuickActionMessage = null
        }
    }

    val searchFocusRequester = remember { FocusRequester() }
    val keyboardScope = rememberCoroutineScope()
    var showCategoryHint by remember { mutableStateOf(!AppPrefs.categoryHintDismissed) }

    if (showDialog) {
        AboutWindow(onCloseRequest = { showDialog = false })
    }
    if (showShortcutsDialog) {
        KeyboardShortcutsDialog(onClose = { showShortcutsDialog = false })
    }
    if (showUserInfoDialog) {
        UserInfoWindow(onCloseRequest = { showUserInfoDialog = false })
    }
    if (showConverterWindow) {
        Mp3ConverterWindow(onCloseRequest = { showConverterWindow = false })
    }
    if (showWikiBrowser) {
        WikiBrowserWindow(onCloseRequest = { showWikiBrowser = false })
    }
    if (showLogWindow) {
        LogWindow(
            logLines = logLines,
            isDownloading = isDownloading,
            isScanningTree = isScanningTree,
            progress = progress,
            progressText = progressText,
            onCloseRequest = { showLogWindow = false }
        )
    }

    // --- 用户信息面板 ---
    // 角色列表状态
    val characterListState = rememberLazyListState()

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
                            SearchMode.FILE_SEARCH -> {
                                viewModel.selectAllFileSearchResults()
                                true
                            }
                            SearchMode.PORTRAIT -> false
                            else -> {
                                viewModel.checkAllCategories()
                                true
                            }
                        }
                    }
                    // Ctrl+Shift+A → 取消全选
                    ctrl && shift && keyEvent.key == Key.A -> {
                        when (searchMode) {
                            SearchMode.FILE_SEARCH -> {
                                viewModel.clearFileSearchSelection()
                                true
                            }
                            SearchMode.PORTRAIT -> false
                            else -> {
                                viewModel.uncheckAllCategories()
                                true
                            }
                        }
                    }
                    // Ctrl+T → 切换主题
                    ctrl && keyEvent.key == Key.T -> {
                        darkMode.value = !darkMode.value
                        true
                    }
                    // Ctrl+1/2/3/4 → 切换搜索模式
                    ctrl && keyEvent.key == Key.One -> {
                        viewModel.onSearchModeChange(SearchMode.VOICE_ONLY); true
                    }

                    ctrl && keyEvent.key == Key.Two -> {
                        viewModel.onSearchModeChange(SearchMode.PORTRAIT); true
                    }

                    ctrl && keyEvent.key == Key.Three -> {
                        viewModel.onSearchModeChange(SearchMode.ALL_CATEGORIES); true
                    }

                    ctrl && keyEvent.key == Key.Four -> {
                        viewModel.onSearchModeChange(SearchMode.FILE_SEARCH); true
                    }
                    // ↑/↓ → 在左侧列表中导航
                    keyEvent.key == Key.DirectionUp && searchMode != SearchMode.FILE_SEARCH -> {
                        if (searchMode == SearchMode.PORTRAIT) {
                            val current = selectedPortraitCharacter
                            val idx = portraitCharacters.indexOf(current)
                            if (idx > 0) {
                                viewModel.onSelectPortraitCharacter(portraitCharacters[idx - 1])
                                keyboardScope.launch { characterListState.animateScrollToItem(idx - 1) }
                            }
                        } else {
                            val groups = characterGroups
                            val current = selectedGroup
                            val idx = groups.indexOf(current)
                            if (idx > 0) {
                                viewModel.onSelectGroup(groups[idx - 1])
                                keyboardScope.launch { characterListState.animateScrollToItem(idx - 1) }
                            }
                        }
                        true
                    }

                    keyEvent.key == Key.DirectionDown && searchMode != SearchMode.FILE_SEARCH -> {
                        if (searchMode == SearchMode.PORTRAIT) {
                            val current = selectedPortraitCharacter
                            val idx = portraitCharacters.indexOf(current)
                            if (idx < portraitCharacters.size - 1 && idx >= 0) {
                                viewModel.onSelectPortraitCharacter(portraitCharacters[idx + 1])
                                keyboardScope.launch { characterListState.animateScrollToItem(idx + 1) }
                            } else if (idx == -1 && portraitCharacters.isNotEmpty()) {
                                viewModel.onSelectPortraitCharacter(portraitCharacters.first())
                                keyboardScope.launch { characterListState.animateScrollToItem(0) }
                            }
                        } else {
                            val groups = characterGroups
                            val current = selectedGroup
                            val idx = groups.indexOf(current)
                            if (idx < groups.size - 1) {
                                viewModel.onSelectGroup(groups[idx + 1])
                                keyboardScope.launch { characterListState.animateScrollToItem(idx + 1) }
                            }
                        }
                        true
                    }

                    else -> false
                }
            }
    ) {
        MenuBar {
            MenuBarItem(
                content = { Text("视图") },
                items = {
                    MenuFlyoutItem(
                        onClick = { darkMode.value = !darkMode.value },
                        icon = {
                            Icon(
                                if (darkMode.value) Icons.Regular.WeatherSunny else Icons.Regular.WeatherMoon,
                                contentDescription = null
                            )
                        },
                        text = { Text(if (darkMode.value) "切换亮色主题" else "切换暗色主题") },
                        trailing = { Text("Ctrl+T", fontSize = 11.sp, color = FluentTheme.colors.text.text.secondary) }
                    )
                    if (backdropOptions.isNotEmpty()) {
                        MenuFlyoutSeparator()
                        MenuFlyoutItem(
                            icon = { Icon(Icons.Regular.Window, contentDescription = null) },
                            text = { Text("窗口效果") },
                            items = {
                                backdropOptions.forEach { (label, backdrop) ->
                                    val current = backdropType.value
                                    val isCurrent = if (backdrop == null) current == null
                                    else current != null && current::class == backdrop::class
                                    MenuFlyoutItem(
                                        text = { Text(label) },
                                        selected = isCurrent,
                                        onSelectedChanged = { backdropType.value = backdrop },
                                        selectionType = ListItemSelectionType.Radio,
                                        colors = ListItemDefaults.defaultListItemColors()
                                    )
                                }
                            }
                        )
                    }
                },
            )
            MenuBarItem(
                content = { Text("工具") },
                items = {
                    MenuFlyoutItem(
                        onClick = { showWikiBrowser = true },
                        icon = { Icon(Icons.Regular.Globe, contentDescription = null) },
                        text = { Text("打开 Wiki") }
                    )
                    MenuFlyoutItem(
                        onClick = { showConverterWindow = true },
                        icon = { Icon(Icons.Regular.MusicNote2, contentDescription = null) },
                        text = { Text("音频转换工具") }
                    )
                    MenuFlyoutItem(
                        onClick = { showLogWindow = true },
                        icon = { Icon(Icons.Regular.TextBulletListLtr, contentDescription = null) },
                        text = { Text("运行日志") }
                    )
                    MenuFlyoutSeparator()
                    MenuFlyoutItem(
                        onClick = {
                            val path = savePath.ifBlank { null }
                            if (path != null) {
                                val dir = File(path)
                                if (dir.exists()) java.awt.Desktop.getDesktop().open(dir)
                                else java.awt.Desktop.getDesktop().open(dir.parentFile ?: dir)
                            }
                        },
                        icon = { Icon(Icons.Regular.FolderOpen, contentDescription = null) },
                        text = { Text("打开保存路径") }
                    )
                    MenuFlyoutSeparator()
                    MenuFlyoutItem(
                        onClick = { if (!isDownloading && !isScanningTree) viewModel.startDownload() },
                        icon = { Icon(Icons.Regular.ArrowDownload, contentDescription = null) },
                        text = { Text("开始下载") },
                        trailing = { Text("Ctrl+D", fontSize = 11.sp, color = FluentTheme.colors.text.text.secondary) }
                    )
                    MenuFlyoutSeparator()
                    MenuFlyoutItem(
                        onClick = { viewModel.checkAllCategories() },
                        icon = { Icon(Icons.Regular.CheckboxChecked, contentDescription = null) },
                        text = { Text("全选") },
                        trailing = { Text("Ctrl+A", fontSize = 11.sp, color = FluentTheme.colors.text.text.secondary) }
                    )
                    MenuFlyoutItem(
                        onClick = { viewModel.uncheckAllCategories() },
                        icon = { Icon(Icons.Regular.CheckboxUnchecked, contentDescription = null) },
                        text = { Text("全不选") },
                        trailing = { Text("Ctrl+Shift+A", fontSize = 11.sp, color = FluentTheme.colors.text.text.secondary) }
                    )
                    MenuFlyoutSeparator()
                    MenuFlyoutItem(
                        onClick = { searchFocusRequester.requestFocus() },
                        icon = { Icon(Icons.Regular.Search, contentDescription = null) },
                        text = { Text("聚焦搜索框") },
                        trailing = { Text("Ctrl+F", fontSize = 11.sp, color = FluentTheme.colors.text.text.secondary) }
                    )
                    MenuFlyoutItem(
                        onClick = { performSearch() },
                        icon = { Icon(Icons.Regular.ArrowSync, contentDescription = null) },
                        text = { Text("重新搜索") },
                        trailing = { Text("F5", fontSize = 11.sp, color = FluentTheme.colors.text.text.secondary) }
                    )
                },
            )
            MenuBarItem(
                content = { Text("帮助") },
                items = {
                    MenuFlyoutItem(
                        onClick = { showShortcutsDialog = true },
                        icon = { Icon(Icons.Regular.Keyboard, contentDescription = null) },
                        text = { Text("键盘快捷键") }
                    )
                    MenuFlyoutSeparator()
                    MenuFlyoutItem(
                        onClick = { showDialog = true },
                        icon = { Icon(Icons.Regular.Info, contentDescription = null) },
                        text = { Text("关于") }
                    )
                },
            )
            // 用户入口
            MenuBarItem(
                content = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val iconColor = if (isUserLoggedIn) Color(0xFF4CAF50) else Color.Gray
                        Box(
                            Modifier.size(8.dp).clip(CircleShape).background(iconColor)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isUserLoggedIn) currentUser!!.name else "未登录")
                    }
                },
                items = {
                    MenuFlyoutItem(
                        onClick = { showUserInfoDialog = true },
                        text = { Text("个人信息 & 账号管理") },
                        icon = { Icon(Icons.Regular.Person, contentDescription = null) }
                    )
                    if (isUserLoggedIn) {
                        val user = currentUser!!
                        MenuFlyoutSeparator()
                        MenuFlyoutItem(
                            onClick = ::refreshCurrentUser,
                            text = { Text(if (isRefreshingUser) "正在刷新账号信息..." else "刷新账号信息") },
                            icon = { Icon(Icons.Regular.ArrowSync, contentDescription = null) }
                        )
                        MenuFlyoutSeparator()
                        MenuFlyoutItem(
                            onClick = { copyTextToClipboard(user.name) },
                            text = { Text("复制用户名") }
                        )
                        MenuFlyoutItem(
                            onClick = { copyTextToClipboard(user.id.toString()) },
                            text = { Text("复制用户 ID") }
                        )
                        user.email.takeIf { it.isNotBlank() }?.let { email ->
                            MenuFlyoutItem(
                                onClick = { copyTextToClipboard(email) },
                                text = { Text("复制邮箱") }
                            )
                        }
                        MenuFlyoutSeparator()
                        MenuFlyoutItem(
                            onClick = { openExternalUrl(userPageUrl(user.name)) },
                            text = { Text("打开用户页") }
                        )
                        MenuFlyoutItem(
                            onClick = { openExternalUrl(userContributionsUrl(user.name)) },
                            text = { Text("打开贡献页") }
                        )
                        MenuFlyoutItem(
                            onClick = { openExternalUrl(userUploadsUrl(user.name)) },
                            text = { Text("打开上传列表") }
                        )
                    }
                }
            )
        }
        // 上半部分：左右分栏
        Row(Modifier.weight(1f)) {

            // --- 左侧：导航与搜索 (Weight 0.3) ---
            Card(Modifier.weight(0.3f)) {
                Column(
                    Modifier.padding(12.dp)
                ) {
                    Text(if (searchMode == SearchMode.PORTRAIT) "立绘角色" else "搜索列表", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                    SelectorBar {
                        SelectorBarItem(
                            selected = searchMode == SearchMode.VOICE_ONLY,
                            onSelectedChange = { viewModel.onSearchModeChange(SearchMode.VOICE_ONLY) },
                            text = { Text("语音") },
                            icon = { Icon(Icons.Regular.MicSparkle, contentDescription = null) }
                        )
                        SelectorBarItem(
                            selected = searchMode == SearchMode.PORTRAIT,
                            onSelectedChange = { viewModel.onSearchModeChange(SearchMode.PORTRAIT) },
                            text = { Text("立绘") },
                            icon = { Icon(Icons.Regular.Person, contentDescription = null) }
                        )
                        SelectorBarItem(
                            selected = searchMode == SearchMode.ALL_CATEGORIES,
                            onSelectedChange = { viewModel.onSearchModeChange(SearchMode.ALL_CATEGORIES) },
                            text = { Text("分类") },
                            icon = { Icon(Icons.Regular.Apps, contentDescription = null) }
                        )
                        SelectorBarItem(
                            selected = searchMode == SearchMode.FILE_SEARCH,
                            onSelectedChange = { viewModel.onSearchModeChange(SearchMode.FILE_SEARCH) },
                            text = { Text("文件") },
                            icon = { Icon(Icons.Regular.DocumentSearch, contentDescription = null) }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (searchMode == SearchMode.FILE_SEARCH) {
                        // 文件搜索结果列表
                        if (fileSearchResults.isEmpty() && !isSearching) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("输入关键词后搜索", color = Color.Gray, fontSize = 12.sp)
                            }
                        } else if (isSearching) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ProgressRing(size = 40.dp)
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
                                    val listState = rememberLazyListState()
                                    val adapter = rememberScrollbarAdapter(scrollState = listState)
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
                    } else if (searchMode == SearchMode.PORTRAIT) {
                        if (portraitCharacters.isEmpty() && !isSearching) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("未找到可预览角色", color = Color.Gray, fontSize = 12.sp)
                            }
                        } else if (portraitCharacters.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ProgressRing(size = 40.dp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = characterListState,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(portraitCharacters, key = { it }) { characterName ->
                                    CharacterRow(
                                        characterName = characterName,
                                        isSelected = characterName == selectedPortraitCharacter,
                                        onSelect = { viewModel.onSelectPortraitCharacter(characterName) }
                                    )
                                }
                            }
                        }
                    } else {
                        if (characterGroups.isEmpty() && !isSearching) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("无数据", color = Color.Gray, fontSize = 12.sp)
                            }
                        } else if (isSearching) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ProgressRing(size = 40.dp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = characterListState,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(
                                    characterGroups,
                                    key = { it.characterName }
                                ) { group ->
                                    val isSelected = group == selectedGroup
                                    CharacterRow(
                                        characterName = group.characterName,
                                        isSelected = isSelected,
                                        enabled = !isDownloading,
                                        onSelect = { viewModel.onSelectGroup(group) }
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

                if (isUserLoggedIn && currentUser != null) {
                    LoggedInUserQuickPanel(
                        user = currentUser!!,
                        isRefreshing = isRefreshingUser,
                        message = userQuickActionMessage,
                        onOpenManager = { showUserInfoDialog = true },
                        onRefresh = ::refreshCurrentUser
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // 首次使用提示
                if (showCategoryHint && searchMode != SearchMode.PORTRAIT) {
                    InfoBar(
                        modifier = Modifier.fillMaxWidth(),
                        title = { Text("提示") },
                        message = { Text("双击分类条目或右键选择「选择文件...」可打开该分类的文件列表") },
                        closeAction = {
                            InfoBarDefaults.CloseActionButton(onClick = {
                                showCategoryHint = false
                                AppPrefs.categoryHintDismissed = true
                            })
                        },
                        severity = InfoBarSeverity.Informational
                    )
                    Spacer(Modifier.height(8.dp))
                }

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
                                    items(
                                        fileSearchResults.filter { it.second in fileSearchSelectedUrls },
                                        key = { it.second }) { (name, _) ->
                                        Text(
                                            name,
                                            fontSize = 13.sp,
                                            modifier = Modifier.fillMaxWidth()
                                                .padding(vertical = 3.dp, horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else if (searchMode == SearchMode.PORTRAIT) {
                            if (selectedPortraitCharacter == null) {
                                EmptyPlaceholder(
                                    icon = Icons.Regular.CursorClick,
                                    text = "请从左侧选择一个角色"
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(selectedPortraitCharacter!!, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Text("共 ${portraitCostumes.size} 套时装", color = Color.Gray, fontSize = 12.sp)
                                    Spacer(Modifier.weight(1f))
                                    if (isPortraitLoading) {
                                        ProgressRing(size = 18.dp)
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                ) {
                                    when {
                                        isPortraitLoading -> {
                                            ProgressRing(
                                                modifier = Modifier.align(Alignment.Center),
                                                size = 48.dp
                                            )
                                        }
                                        portraitCostumes.isEmpty() -> {
                                            EmptyPlaceholder(
                                                icon = Icons.Regular.Person,
                                                text = "未找到该角色的立绘或正背面预览图"
                                            )
                                        }
                                        else -> {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                items(count = portraitCostumes.size, key = { portraitCostumes[it].key }) { index ->
                                                    val costume: PortraitCostume = portraitCostumes[index]
                                                    PortraitCostumeCard(
                                                        costume = costume,
                                                        expanded = selectedPortraitCostumeKey == costume.key,
                                                        onToggle = { viewModel.togglePortraitCostume(costume.key) },
                                                        onOpenAsset = { asset ->
                                                            fileSearchPreviewUrl = asset.url
                                                            fileSearchPreviewName = asset.title
                                                        },
                                                        onSaveAsset = ::savePortraitAsset,
                                                        onSaveAsAsset = ::savePortraitAssetAs
                                                    )
                                                }
                                            }
                                        }
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
                                    Box(Modifier.fillMaxSize()) {
                                        val catListState = rememberLazyListState()
                                        val catAdapter =
                                            rememberScrollbarAdapter(scrollState = catListState)
                                        ScrollbarContainer(
                                            adapter = catAdapter,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            LazyColumn(
                                                state = catListState,
                                                modifier = Modifier.fillMaxSize().padding(4.dp)
                                            ) {
                                                items(subCategories, key = { it }) { cat ->
                                                    val isChecked = checkedCategories.contains(cat)
                                                    val isRoot = cat == group.rootCategory
                                                    val statusText = if (manualSelectionMap.containsKey(cat)) {
                                                        val count = manualSelectionMap[cat]?.size ?: 0
                                                        val total = categoryTotalCountMap[cat]
                                                        if (total != null) "已选 $count / $total" else "已选 $count 项"
                                                    } else {
                                                        if (isChecked) "默认全选" else ""
                                                    }
                                                    CategoryRow(
                                                        cat = cat,
                                                        isChecked = isChecked,
                                                        isRoot = isRoot,
                                                        statusText = statusText,
                                                        isDownloading = isDownloading,
                                                        onToggle = { viewModel.setCategoryChecked(cat, !isChecked) },
                                                        onOpenFiles = { viewModel.openFileDialog(cat) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (searchMode != SearchMode.PORTRAIT) {
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
                            var converterExpanded by remember { mutableStateOf(convertAfterDownload) }
                            LaunchedEffect(convertAfterDownload) {
                                if (convertAfterDownload) converterExpanded = true
                            }
                            Expander(
                                icon = { Icon(Icons.Regular.MusicNote2, "音频转换") },
                                expanded = converterExpanded,
                                onExpandedChanged = { expanded ->
                                    converterExpanded = expanded
                                    if (expanded) viewModel.onConvertAfterDownloadChange(true)
                                },
                                heading = {
                                    Text(
                                        if (isVoiceOnly) "WAV 转换" else "MP3/FLAC → WAV 转换",
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                caption = {
                                    Text(
                                        if (isVoiceOnly) "下载完成后批量转换为 WAV 格式"
                                        else "下载完成后将 MP3/FLAC 批量转换为 WAV（其他格式跳过）",
                                        color = FluentTheme.colors.text.text.secondary
                                    )
                                },
                                trailing = {
                                    Switcher(
                                        checked = convertAfterDownload,
                                        onCheckStateChange = { viewModel.onConvertAfterDownloadChange(it) },
                                        textBefore = true,
                                        text = if (convertAfterDownload) "开启" else "关闭"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // 1. 采样率 + 位深
                                ExpanderItem(
                                    heading = {
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
                                    }
                                )
                                ExpanderItemSeparator()
                                // 2. 删除原 MP3
                                ExpanderItem(
                                    heading = {
                                        Text(
                                            if (isVoiceOnly) "删除原始源文件"
                                            else "删除原始 MP3/FLAC（其他格式不受影响）"
                                        )
                                    },
                                    trailing = {
                                        Switcher(
                                            checked = deleteOriginalMp3,
                                            onCheckStateChange = { viewModel.onDeleteOriginalMp3Change(it) },
                                        )
                                    }
                                )
                                ExpanderItemSeparator()
                                // 3. 合并 WAV
                                ExpanderItem(
                                    heading = { Text("合并导出的 WAV 文件") },
                                    trailing = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (mergeWav) {
                                                TextField(
                                                    value = mergeWavMaxCountStr,
                                                    onValueChange = { viewModel.onMergeWavMaxCountStrChange(it) },
                                                    placeholder = { Text("每组上限（0为不限）") },
                                                    modifier = Modifier.width(240.dp),
                                                    singleLine = true
                                                )
                                            }
                                            Switcher(
                                                checked = mergeWav,
                                                onCheckStateChange = { viewModel.onMergeWavChange(it) },
                                                textBefore = true,
                                                text = if (!mergeWav) ""
                                                else if (mergeWavMaxCountStr == "0" || mergeWavMaxCountStr.isEmpty()) "全部合并"
                                                else "每 $mergeWavMaxCountStr 个/组"
                                            )
                                        }
                                    }
                                )
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
        }


    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun KeyboardShortcutsDialog(onClose: () -> Unit) {
    val windowState = rememberWindowState(width = 480.dp, height = 440.dp, position = WindowPosition(Alignment.Center))

    StyledWindow(
        title = "键盘快捷键",
        onCloseRequest = onClose,
        state = windowState,
        resizable = false,
        onKeyEvent = { keyEvent ->
            if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                onClose(); true
            } else false
        }
    ) { _ ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
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
                            "Ctrl + 2" to "切换至「立绘列表」模式",
                            "Ctrl + 3" to "切换至「全部分类」模式",
                            "Ctrl + 4" to "切换至「文件搜索」模式",
                            "↑ / ↓" to "在左侧角色列表中上下导航",
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    cat: String,
    isChecked: Boolean,
    isRoot: Boolean,
    statusText: String,
    isDownloading: Boolean,
    onToggle: () -> Unit,
    onOpenFiles: () -> Unit
) {
    val name = cat.replace("Category:", "").replace("分类:", "")
    ContextMenuArea(items = {
        listOf(ContextMenuItem("选择文件...") { onOpenFiles() })
    }) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .combinedClickable(
                    onClick = onToggle,
                    onDoubleClick = { if (!isDownloading) onOpenFiles() }
                )
                .padding(vertical = 6.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CheckBox(checked = isChecked, onCheckStateChange = { onToggle() })
            Spacer(Modifier.width(8.dp))
            Text(name + if (isRoot) " (主分类)" else "", modifier = Modifier.weight(1f))
            Text(statusText, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun CharacterRow(
    characterName: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onSelect: () -> Unit
) {
    val bgColor = if (isSelected) FluentTheme.colors.control.secondary else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CharacterAvatar(
            characterName = characterName,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            characterName,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun PortraitCostumeCard(
    costume: PortraitCostume,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenAsset: (PortraitAsset) -> Unit,
    onSaveAsset: (PortraitAsset) -> Unit,
    onSaveAsAsset: (PortraitAsset) -> Unit
) {
    val coreCount = listOfNotNull(costume.illustration, costume.frontPreview, costume.backPreview).size
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "chevron"
    )
    val headerInteractionSource = remember { MutableInteractionSource() }
    val headerHovered by headerInteractionSource.collectIsHoveredAsState()
    val secondaryColor = FluentTheme.colors.control.secondary
    val headerBg by animateColorAsState(
        targetValue = if (headerHovered) secondaryColor else secondaryColor.copy(alpha = 0f),
        animationSpec = tween(150),
        label = "headerBg"
    )

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // --- Header ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(headerBg)
                    .hoverable(headerInteractionSource)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 时装名
                Column(Modifier.weight(1f)) {
                    Text(costume.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 收录数量 badge
                        val badgeColor = when (coreCount) {
                            3 -> Color(0xFF4CAF50)
                            2 -> Color(0xFFFF9800)
                            else -> Color(0xFF9E9E9E)
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(badgeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "$coreCount / 3",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = badgeColor
                            )
                        }
                        if (costume.extraAssets.isNotEmpty()) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(FluentTheme.colors.fillAccent.default.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "+${costume.extraAssets.size}",
                                    fontSize = 11.sp,
                                    color = FluentTheme.colors.fillAccent.default
                                )
                            }
                        }
                    }
                }
                // 旋转箭头
                Icon(
                    imageVector = Icons.Regular.ChevronDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                    tint = FluentTheme.colors.text.text.secondary
                )
            }

            // --- 展开内容（带动画）---
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(180)),
                exit = shrinkVertically(
                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(120))
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PortraitAssetCard(label = "立绘", asset = costume.illustration, onOpenAsset = onOpenAsset, onSaveAsset = onSaveAsset, onSaveAsAsset = onSaveAsAsset)
                        PortraitAssetCard(label = "正面预览", asset = costume.frontPreview, onOpenAsset = onOpenAsset, onSaveAsset = onSaveAsset, onSaveAsAsset = onSaveAsAsset)
                        PortraitAssetCard(label = "背面预览", asset = costume.backPreview, onOpenAsset = onOpenAsset, onSaveAsset = onSaveAsset, onSaveAsAsset = onSaveAsAsset)
                    }

                    if (costume.extraAssets.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "其他相关图",
                                fontSize = 12.sp,
                                color = FluentTheme.colors.text.text.secondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                costume.extraAssets.forEach { asset ->
                                    Button(onClick = { onOpenAsset(asset) }, modifier = Modifier.height(28.dp)) {
                                        Text(asset.title.substringBeforeLast('.'), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortraitAssetCard(
    label: String,
    asset: PortraitAsset?,
    onOpenAsset: (PortraitAsset) -> Unit,
    onSaveAsset: (PortraitAsset) -> Unit,
    onSaveAsAsset: (PortraitAsset) -> Unit
) {
    var flyoutVisible by remember(asset?.url) { mutableStateOf(false) }
    var flyoutExpanded by remember(asset?.url) { mutableStateOf(false) }
    val imageInteractionSource = remember { MutableInteractionSource() }
    val imageHovered by imageInteractionSource.collectIsHoveredAsState()
    val overlayAlpha by animateFloatAsState(
        targetValue = if (imageHovered && asset != null) 1f else 0f,
        animationSpec = tween(150),
        label = "overlay"
    )
    val actions = remember(asset?.url) {
        listOf(
            Icons.Regular.TabDesktopImage to "预览",
            Icons.Regular.ArrowDownload to "保存"
        )
    }

    Card(Modifier.width(220.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Box {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(FluentTheme.colors.control.secondary)
                        .hoverable(imageInteractionSource)
                        .let { base ->
                            if (asset != null) {
                                base.clickable(
                                    interactionSource = imageInteractionSource,
                                    indication = null
                                ) { flyoutVisible = true }
                            } else base
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (asset == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Regular.Person,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = Color.Gray.copy(alpha = 0.4f)
                            )
                            Text("暂无图片", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else {
                        NetworkImage(
                            url = asset.url,
                            contentDescription = asset.title,
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            contentScale = ContentScale.Fit,
                            placeholder = {
                                ProgressRing(size = 32.dp)
                            }
                        )
                    }

                    // hover 遮罩 + 提示
                    if (asset != null && overlayAlpha > 0f) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.35f * overlayAlpha)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Regular.TabDesktopImage,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.White.copy(alpha = overlayAlpha)
                                )
                                Text(
                                    "点击操作",
                                    color = Color.White.copy(alpha = overlayAlpha),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                if (asset != null) {
                    LargeCommandBarFlyout(
                        visible = flyoutVisible,
                        onDismissRequest = { flyoutVisible = false },
                        expanded = flyoutExpanded,
                        onExpandedChanged = { flyoutExpanded = it },
                        positionProvider = rememberFlyoutPositionProvider(FlyoutPlacement.BottomAlignedStart),
                        secondary = { hasOverFlowItem ->
                            portraitSecondaryItems(hasOverFlowItem) {
                                onSaveAsAsset(asset)
                                flyoutVisible = false
                                flyoutExpanded = false
                            }
                        }
                    ) {
                        items(actions.size) { index ->
                            val (icon, text) = actions[index]
                            val action = {
                                when (index) {
                                    0 -> onOpenAsset(asset)
                                    1 -> onSaveAsset(asset)
                                }
                                flyoutVisible = false
                                flyoutExpanded = false
                            }
                            if (isOverflow) {
                                ListItem(
                                    onClick = action,
                                    text = { Text(text) },
                                    icon = { Icon(icon, contentDescription = null) }
                                )
                            } else {
                                CommandBarButton(
                                    onClick = action,
                                    content = {
                                        Icon(icon, contentDescription = null)
                                        Text(text)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Text(
                asset?.title?.substringBeforeLast('.') ?: "等待补充",
                fontSize = 11.sp,
                color = FluentTheme.colors.text.text.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MenuFlyoutScope.portraitSecondaryItems(
    hasOverFlowItem: Boolean,
    onClick: () -> Unit
) {
    if (hasOverFlowItem) {
        MenuFlyoutSeparator()
    }
    MenuFlyoutItem(
        text = { Text("另存为") },
        icon = { Icon(Icons.Regular.FolderOpen, contentDescription = null) },
        onClick = onClick
    )
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun LoggedInUserQuickPanel(
    user: WikiUserApi.UserInfo,
    isRefreshing: Boolean,
    message: String?,
    onOpenManager: () -> Unit,
    onRefresh: () -> Unit
) {
    val roleText = remember(user.groups) {
        user.groups.filter { it != "*" && it != "user" }.joinToString(" · ").ifBlank { "普通用户" }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(FluentTheme.colors.fillAccent.default.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        user.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFF4CAF50).copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("已登录", fontSize = 11.sp, color = Color(0xFF2E7D32))
                        }
                    }
                    Text(roleText, fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
                    user.realName.takeIf { it.isNotBlank() }?.let {
                        Text("实名：$it", fontSize = 11.sp, color = FluentTheme.colors.text.text.secondary)
                    }
                }
                Button(onClick = onOpenManager, modifier = Modifier.height(28.dp)) {
                    Text("账号管理", fontSize = 12.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                UserStatChip(label = "用户 ID", value = user.id.toString(), modifier = Modifier.weight(1f))
                UserStatChip(label = "编辑次数", value = user.editCount.toString(), modifier = Modifier.weight(1f))
                UserStatChip(
                    label = "注册时间",
                    value = WikiUserApi.formatTimestamp(user.registrationDate),
                    modifier = Modifier.weight(1f)
                )
            }

            user.email.takeIf { it.isNotBlank() }?.let {
                Text("邮箱：$it", fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onRefresh, modifier = Modifier.height(28.dp)) {
                    if (isRefreshing) {
                        ProgressRing(size = 14.dp)
                    } else {
                        Text("刷新资料", fontSize = 12.sp)
                    }
                }
                Button(onClick = { copyTextToClipboard(user.name) }, modifier = Modifier.height(28.dp)) {
                    Text("复制用户名", fontSize = 12.sp)
                }
                Button(onClick = { copyTextToClipboard(user.id.toString()) }, modifier = Modifier.height(28.dp)) {
                    Text("复制 ID", fontSize = 12.sp)
                }
                if (user.email.isNotBlank()) {
                    Button(onClick = { copyTextToClipboard(user.email) }, modifier = Modifier.height(28.dp)) {
                        Text("复制邮箱", fontSize = 12.sp)
                    }
                }
                Button(onClick = { openExternalUrl(userPageUrl(user.name)) }, modifier = Modifier.height(28.dp)) {
                    Text("用户页", fontSize = 12.sp)
                }
                Button(onClick = { openExternalUrl(userContributionsUrl(user.name)) }, modifier = Modifier.height(28.dp)) {
                    Text("贡献页", fontSize = 12.sp)
                }
                Button(onClick = { openExternalUrl(userUploadsUrl(user.name)) }, modifier = Modifier.height(28.dp)) {
                    Text("上传列表", fontSize = 12.sp)
                }
            }

            if (!message.isNullOrBlank()) {
                Text(
                    message,
                    fontSize = 12.sp,
                    color = if (message.startsWith("刷新失败")) Color(0xFFE57373) else FluentTheme.colors.text.text.secondary
                )
            }
        }
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun UserStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, fontSize = 11.sp, color = FluentTheme.colors.text.text.secondary)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private const val WIKI_BASE_URL = "https://wiki.biligame.com/klbq"

private fun userPageUrl(userName: String): String = wikiTitleUrl("User:$userName")

private fun userContributionsUrl(userName: String): String = wikiTitleUrl("Special:Contributions/$userName")

private fun userUploadsUrl(userName: String): String = wikiTitleUrl("Special:ListFiles/$userName")

private fun wikiTitleUrl(title: String): String =
    "$WIKI_BASE_URL/index.php?title=${URLEncoder.encode(title, StandardCharsets.UTF_8.toString())}"

private fun copyTextToClipboard(text: String): Boolean = runCatching {
    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
}.isSuccess

private fun openExternalUrl(url: String): Boolean = runCatching {
    java.awt.Desktop.getDesktop().browse(java.net.URI(url))
}.isSuccess
