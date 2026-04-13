package com.nekolaska.calabiyau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import com.nekolaska.calabiyau.data.AppPrefs
import com.nekolaska.calabiyau.viewmodel.DownloadTask
import com.nekolaska.calabiyau.viewmodel.DownloadViewModel
import com.nekolaska.calabiyau.viewmodel.PortraitViewModel
import com.nekolaska.calabiyau.viewmodel.SearchViewModel
import data.SearchMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloaderScreen(
    searchVM: SearchViewModel,
    downloadVM: DownloadViewModel,
    portraitVM: PortraitViewModel,
    onOpenDrawer: () -> Unit,
    onOpenFileManager: () -> Unit = {},
    onOpenDownloadHistory: () -> Unit = {}
) {
    // ── SearchViewModel 状态 ──
    val searchKeyword by searchVM.searchKeyword.collectAsState()
    val searchMode by searchVM.searchMode.collectAsState()
    val isSearching by searchVM.isSearching.collectAsState()
    val characterGroups by searchVM.characterGroups.collectAsState()
    val selectedGroup by searchVM.selectedGroup.collectAsState()
    val subCategories by searchVM.subCategories.collectAsState()
    val checkedCategories by searchVM.checkedCategories.collectAsState()
    val isScanningTree by searchVM.isScanningTree.collectAsState()
    val fileSearchResults by searchVM.fileSearchResults.collectAsState()
    val fileSearchSelectedUrls by searchVM.fileSearchSelectedUrls.collectAsState()
    val hasSearched by searchVM.hasSearched.collectAsState()
    val characterAvatars by searchVM.characterAvatars.collectAsState()
    val portraitCharacters by searchVM.portraitCharacters.collectAsState()
    val showFileDialog by searchVM.showFileDialog.collectAsState()
    val dialogCategoryName by searchVM.dialogCategoryName.collectAsState()
    val dialogFileList by searchVM.dialogFileList.collectAsState()
    val dialogIsLoading by searchVM.dialogIsLoading.collectAsState()
    val dialogSelectedUrls by searchVM.dialogSelectedUrls.collectAsState()
    val manualSelectionMap by searchVM.manualSelectionMap.collectAsState()
    val searchError by searchVM.searchError.collectAsState()

    // ── DownloadViewModel 状态 ──
    val isDownloading by downloadVM.isDownloading.collectAsState()
    val downloadProgress by downloadVM.downloadProgress.collectAsState()
    val downloadStatusText by downloadVM.downloadStatusText.collectAsState()
    val logs by downloadVM.logs.collectAsState()
    val favorites by downloadVM.favorites.collectAsState()
    val isNetworkAvailable by downloadVM.isNetworkAvailable.collectAsState()

    // ── PortraitViewModel 状态 ──
    val selectedPortraitCharacter by portraitVM.selectedPortraitCharacter.collectAsState()
    val portraitCatalog by portraitVM.portraitCatalog.collectAsState()
    val isLoadingPortrait by portraitVM.isLoadingPortrait.collectAsState()
    val selectedPortraitCostume by portraitVM.selectedPortraitCostume.collectAsState()

    // 连接 SearchViewModel 的日志到 DownloadViewModel
    LaunchedEffect(Unit) {
        searchVM.onLog = { downloadVM.addLog(it) }
    }

    /** 根据当前搜索模式构造下载任务 */
    fun buildDownloadTask(): DownloadTask? = when (searchMode) {
        SearchMode.PORTRAIT -> {
            val costume = selectedPortraitCostume ?: return@buildDownloadTask null
            val name = selectedPortraitCharacter ?: return@buildDownloadTask null
            DownloadTask.Portrait(name, costume)
        }
        SearchMode.FILE_SEARCH -> {
            val files = fileSearchResults.filter { it.second in fileSearchSelectedUrls }
            DownloadTask.FileSearch(files)
        }
        else -> {
            val group = selectedGroup ?: return@buildDownloadTask null
            DownloadTask.Category(
                group = group,
                checkedCategories = checkedCategories,
                manualSelectionMap = manualSelectionMap,
                voiceOnly = searchMode == SearchMode.VOICE_ONLY
            )
        }
    }

    val focusManager = LocalFocusManager.current
    var showLogs by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val bottomBarStyle by remember { mutableIntStateOf(AppPrefs.bottomBarStyle) }
    val useDockedToolbar = bottomBarStyle == AppPrefs.BAR_STYLE_DOCKED_TOOLBAR
    // 搜索历史
    var searchHistoryList by remember { mutableStateOf(AppPrefs.searchHistory) }
    // 每次搜索关键词变化时刷新历史
    LaunchedEffect(searchKeyword) {
        searchHistoryList = AppPrefs.searchHistory
    }

    // 收集错误事件并显示 Snackbar
    LaunchedEffect(Unit) {
        // 合并两个 VM 的错误事件
        launch {
            searchVM.errorEvent.collect { message ->
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message, "重试", duration = SnackbarDuration.Short)
                    .let { if (it == SnackbarResult.ActionPerformed) searchVM.performSearch() }
            }
        }
        downloadVM.errorEvent.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message, "重试", duration = SnackbarDuration.Short)
                .let { if (it == SnackbarResult.ActionPerformed) buildDownloadTask()?.let { t -> downloadVM.startDownload(t) } }
        }
    }

    // 处理返回键
    BackHandler(enabled = selectedPortraitCharacter != null) {
        portraitVM.clearSelectedPortraitCharacter()
    }
    BackHandler(enabled = selectedGroup != null && selectedPortraitCharacter == null) {
        searchVM.clearSelectedGroup()
    }

    Scaffold(
        topBar = {
            DownloaderTopBar(
                searchKeyword = searchKeyword,
                isSearching = isSearching,
                logs = logs,
                searchHistoryList = searchHistoryList,
                onOpenDrawer = onOpenDrawer,
                onOpenDownloadHistory = onOpenDownloadHistory,
                onShowLogs = { showLogs = true },
                onKeywordChange = { searchVM.onSearchKeywordChange(it) },
                onSearch = {
                    focusManager.clearFocus()
                    searchVM.performSearch()
                },
                onClearHistory = {
                    AppPrefs.clearSearchHistory()
                    searchHistoryList = emptyList()
                },
                onHistoryItemClick = { item ->
                    searchVM.onSearchKeywordChange(item)
                    focusManager.clearFocus()
                    searchVM.performSearch()
                }
            )
        },
        bottomBar = {
            Column {
                // 下载进度条
                AnimatedVisibility(
                    visible = isDownloading,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    DownloadStatusBar(
                        progress = downloadProgress,
                        statusText = downloadStatusText
                    )
                }
                // 经典底部应用栏模式
                if (!useDockedToolbar) {
                    BottomAppBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp
                    ) {
                        val modes = listOf(
                            Triple(SearchMode.VOICE_ONLY, "语音", Icons.Outlined.RecordVoiceOver),
                            Triple(SearchMode.ALL_CATEGORIES, "分类", Icons.Outlined.Category),
                            Triple(SearchMode.FILE_SEARCH, "文件搜索", Icons.Outlined.FindInPage),
                            Triple(SearchMode.PORTRAIT, "立绘", Icons.Outlined.Image)
                        )
                        modes.forEach { (mode, label, icon) ->
                            NavigationBarItem(
                                selected = searchMode == mode,
                                onClick = {
                                    searchVM.onSearchModeChange(mode)
                                    focusManager.clearFocus()
                                },
                                icon = {
                                    Icon(
                                        if (searchMode == mode) when (mode) {
                                            SearchMode.VOICE_ONLY -> Icons.Default.RecordVoiceOver
                                            SearchMode.ALL_CATEGORIES -> Icons.Default.Category
                                            SearchMode.FILE_SEARCH -> Icons.Default.FindInPage
                                            SearchMode.PORTRAIT -> Icons.Default.Image
                                        } else icon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = smoothCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val dlLiquidGlass = LocalLiquidGlassEnabled.current.value
        val dlBgColor = MaterialTheme.colorScheme.surface
        val dlBackdrop = if (dlLiquidGlass && useDockedToolbar) rememberLayerBackdrop {
            drawRect(dlBgColor)
            drawContent()
        } else null

        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (dlBackdrop != null) Modifier.layerBackdrop(dlBackdrop)
                        else Modifier
                    )
            ) {
                // 无网络横幅
                NetworkBanner(isNetworkAvailable)
                // 搜索失败提示横幅
                SearchErrorBanner(
                    searchError = searchError,
                    isNetworkAvailable = isNetworkAvailable,
                    onRetry = { searchVM.performSearch() }
                )
                // 内容区
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (searchMode) {
                        SearchMode.FILE_SEARCH -> {
                            FileSearchList(
                                results = fileSearchResults,
                                selectedUrls = fileSearchSelectedUrls,
                                hasSearched = hasSearched,
                                isDownloading = isDownloading,
                                searchError = searchError,
                                onRetry = { searchVM.performSearch() },
                                onToggle = { searchVM.toggleFileSearchSelection(it) },
                                onSelectAll = { searchVM.selectAllFileSearchResults() },
                                onDownload = { buildDownloadTask()?.let { downloadVM.startDownload(it) } }
                            )
                        }

                        SearchMode.PORTRAIT -> {
                            PortraitGrid(
                                characters = portraitCharacters,
                                characterAvatars = characterAvatars,
                                hasSearched = hasSearched,
                                favorites = favorites,
                                searchError = searchError,
                                onRetry = { searchVM.performSearch() },
                                onToggleFavorite = { downloadVM.toggleFavorite(it) },
                                onSelectCharacter = { portraitVM.onSelectPortraitCharacter(it) }
                            )
                        }

                        else -> {
                            CategoryGroupList(
                                characterGroups = characterGroups,
                                characterAvatars = characterAvatars,
                                hasSearched = hasSearched,
                                favorites = favorites,
                                searchError = searchError,
                                onRetry = { searchVM.performSearch() },
                                onToggleFavorite = { downloadVM.toggleFavorite(it) },
                                onSelectGroup = { searchVM.onSelectGroup(it) }
                            )
                        }
                    }
                } // content Box
            } // Column

            // 浮动工具栏模式
            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
            if (useDockedToolbar) {
                val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                val tintColor = MaterialTheme.colorScheme.secondaryContainer
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-16).dp)
                        .zIndex(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── 主工具栏 ──
                    if (dlBackdrop != null) {
                        // 液态玻璃模式：用 Row + drawBackdrop 替代 HorizontalFloatingToolbar
                        Row(
                            modifier = Modifier
                                .height(64.dp)
                                .drawBackdrop(
                                    backdrop = dlBackdrop,
                                    shape = { ContinuousCapsule },
                                    effects = {
                                        vibrancy()
                                        blur(4.dp.toPx())
                                        lens(16.dp.toPx(), 32.dp.toPx())
                                    },
                                    onDrawSurface = { drawRect(surfaceColor) }
                                )
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val modes = listOf(
                                Triple(SearchMode.VOICE_ONLY, "语音", Icons.Outlined.RecordVoiceOver),
                                Triple(SearchMode.ALL_CATEGORIES, "分类", Icons.Outlined.Category),
                                Triple(SearchMode.FILE_SEARCH, "文件搜索", Icons.Outlined.FindInPage),
                                Triple(SearchMode.PORTRAIT, "立绘", Icons.Outlined.Image)
                            )
                            modes.forEach { (mode, label, icon) ->
                                val isSelected = searchMode == mode
                                val selectedIcon = when (mode) {
                                    SearchMode.VOICE_ONLY -> Icons.Default.RecordVoiceOver
                                    SearchMode.ALL_CATEGORIES -> Icons.Default.Category
                                    SearchMode.FILE_SEARCH -> Icons.Default.FindInPage
                                    SearchMode.PORTRAIT -> Icons.Default.Image
                                }
                                IconButton(
                                    onClick = {
                                        searchVM.onSearchModeChange(mode)
                                        focusManager.clearFocus()
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        if (isSelected) selectedIcon else icon,
                                        contentDescription = label,
                                        modifier = Modifier.size(26.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        // 非玻璃模式：使用 HorizontalFloatingToolbar
                        HorizontalFloatingToolbar(
                            expanded = true,
                            content = {
                                val modes = listOf(
                                    Triple(SearchMode.VOICE_ONLY, "语音", Icons.Outlined.RecordVoiceOver),
                                    Triple(SearchMode.ALL_CATEGORIES, "分类", Icons.Outlined.Category),
                                    Triple(SearchMode.FILE_SEARCH, "文件搜索", Icons.Outlined.FindInPage),
                                    Triple(SearchMode.PORTRAIT, "立绘", Icons.Outlined.Image)
                                )
                                modes.forEach { (mode, label, icon) ->
                                    val isSelected = searchMode == mode
                                    val selectedIcon = when (mode) {
                                        SearchMode.VOICE_ONLY -> Icons.Default.RecordVoiceOver
                                        SearchMode.ALL_CATEGORIES -> Icons.Default.Category
                                        SearchMode.FILE_SEARCH -> Icons.Default.FindInPage
                                        SearchMode.PORTRAIT -> Icons.Default.Image
                                    }
                                    TooltipBox(
                                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                            TooltipAnchorPosition.Above
                                        ),
                                        tooltip = { PlainTooltip { Text(label) } },
                                        state = rememberTooltipState()
                                    ) {
                                        IconButton(
                                            onClick = {
                                                searchVM.onSearchModeChange(mode)
                                                focusManager.clearFocus()
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                if (isSelected) selectedIcon else icon,
                                                contentDescription = label,
                                                modifier = Modifier.size(26.dp),
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }

                    // ── FAB ──
                    if (dlBackdrop != null) {
                        // Tinted glass FAB
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .drawBackdrop(
                                    backdrop = dlBackdrop,
                                    shape = { ContinuousCapsule },
                                    effects = {
                                        vibrancy()
                                        blur(4.dp.toPx())
                                        lens(16.dp.toPx(), 32.dp.toPx())
                                    },
                                    onDrawSurface = {
                                        drawRect(tintColor, blendMode = BlendMode.Hue)
                                        drawRect(tintColor.copy(alpha = 0.75f))
                                    }
                                )
                                .clickable { onOpenFileManager() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.FolderOpen,
                                contentDescription = "文件管理",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        FloatingActionButton(
                            onClick = onOpenFileManager,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                Icons.Outlined.FolderOpen,
                                contentDescription = "文件管理"
                            )
                        }
                    }
                }
            }
        } // outer Box
    } // Scaffold content

    // --- Overlays / Sheets ---

    // 日志弹窗
    if (showLogs) {
        LogsDialog(logs = logs, onDismiss = { showLogs = false })
    }

    // 立绘详情 BottomSheet
    if (selectedPortraitCharacter != null) {
        ModalBottomSheet(
            onDismissRequest = { portraitVM.clearSelectedPortraitCharacter() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = smoothCornerShape(28.dp),
            tonalElevation = 0.dp
        ) {
            PortraitDetailContent(
                characterName = selectedPortraitCharacter!!,
                catalog = portraitCatalog,
                isLoading = isLoadingPortrait,
                selectedCostume = selectedPortraitCostume,
                isDownloading = isDownloading,
                onSelectCostume = { portraitVM.selectPortraitCostume(it) },
                onDownload = { buildDownloadTask()?.let { downloadVM.startDownload(it) } }
            )
        }
    }

    // 分类详情 BottomSheet
    if (selectedGroup != null) {
        ModalBottomSheet(
            onDismissRequest = { searchVM.clearSelectedGroup() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = smoothCornerShape(28.dp)
        ) {
            CategoryDetailContent(
                group = selectedGroup!!,
                subCategories = subCategories,
                checkedCategories = checkedCategories,
                isScanning = isScanningTree,
                isDownloading = isDownloading,
                manualSelectionMap = manualSelectionMap,
                onCheckAll = { searchVM.checkAllCategories() },
                onUncheckAll = { searchVM.uncheckAllCategories() },
                onCategoryChecked = { cat, checked -> searchVM.setCategoryChecked(cat, checked) },
                onOpenFileDialog = { searchVM.openFileDialog(it) },
                onDownload = { buildDownloadTask()?.let { downloadVM.startDownload(it) } }
            )
        }
    }

    // 分类文件选择 BottomSheet
    if (showFileDialog) {
        FileSelectionSheet(
            categoryName = dialogCategoryName,
            files = dialogFileList,
            selectedUrls = dialogSelectedUrls,
            isLoading = dialogIsLoading,
            onToggle = { searchVM.toggleDialogFileSelection(it) },
            onSelectAll = { searchVM.selectAllDialogFiles() },
            onClear = { searchVM.clearDialogSelection() },
            onConfirm = { searchVM.confirmFileDialog() },
            onDismiss = { searchVM.closeFileDialog() }
        )
    }
}

// ─────────────────────── 顶栏 ───────────────────────

@Composable
private fun DownloaderTopBar(
    searchKeyword: String,
    isSearching: Boolean,
    logs: List<String>,
    searchHistoryList: List<String>,
    onOpenDrawer: () -> Unit,
    onOpenDownloadHistory: () -> Unit,
    onShowLogs: () -> Unit,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearHistory: () -> Unit,
    onHistoryItemClick: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 侧栏菜单按钮
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "打开侧栏")
                }
                Text(
                    text = "卡拉彼丘",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                // 下载历史按钮
                FilledTonalIconButton(
                    onClick = onOpenDownloadHistory,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(Icons.Outlined.History, contentDescription = "下载历史", modifier = Modifier.size(20.dp))
                }
                // 日志按钮
                FilledTonalIconButton(
                    onClick = onShowLogs,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    BadgedBox(
                        badge = {
                            if (logs.isNotEmpty()) Badge { Text("${logs.size}") }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Article, "日志", modifier = Modifier.size(20.dp))
                    }
                }
            }

            // 搜索栏
            SearchBar(
                keyword = searchKeyword,
                onKeywordChange = onKeywordChange,
                onSearch = onSearch,
                onClear = { onKeywordChange("") },
                isSearching = isSearching,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 搜索历史 chips
            if (searchHistoryList.isNotEmpty() && searchKeyword.isBlank()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    item {
                        AssistChip(
                            onClick = onClearHistory,
                            label = { Text("清除历史", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(16.dp))
                            },
                            shape = smoothCornerShape(12.dp)
                        )
                    }
                    items(searchHistoryList.size) { index ->
                        val historyItem = searchHistoryList[index]
                        SuggestionChip(
                            onClick = { onHistoryItemClick(historyItem) },
                            label = { Text(historyItem, style = MaterialTheme.typography.labelSmall) },
                            icon = {
                                Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp))
                            },
                            shape = smoothCornerShape(12.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────── 横幅 ───────────────────────

@Composable
private fun NetworkBanner(isNetworkAvailable: Boolean) {
    AnimatedVisibility(
        visible = !isNetworkAvailable,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "当前无网络连接",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SearchErrorBanner(
    searchError: String?,
    isNetworkAvailable: Boolean,
    onRetry: () -> Unit
) {
    AnimatedVisibility(
        visible = searchError != null && isNetworkAvailable,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    searchError ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("重试", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
