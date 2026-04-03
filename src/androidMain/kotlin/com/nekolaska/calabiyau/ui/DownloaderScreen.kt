package com.nekolaska.calabiyau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nekolaska.calabiyau.MainViewModel
import com.nekolaska.calabiyau.data.AppPrefs
import data.SearchMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloaderScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onOpenFileManager: () -> Unit = {},
    onOpenDownloadHistory: () -> Unit = {}
) {
    val searchKeyword by viewModel.searchKeyword.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val characterGroups by viewModel.characterGroups.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val subCategories by viewModel.subCategories.collectAsState()
    val checkedCategories by viewModel.checkedCategories.collectAsState()
    val isScanningTree by viewModel.isScanningTree.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadStatusText by viewModel.downloadStatusText.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val fileSearchResults by viewModel.fileSearchResults.collectAsState()
    val fileSearchSelectedUrls by viewModel.fileSearchSelectedUrls.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()
    val characterAvatars by viewModel.characterAvatars.collectAsState()
    val portraitCharacters by viewModel.portraitCharacters.collectAsState()
    val selectedPortraitCharacter by viewModel.selectedPortraitCharacter.collectAsState()
    val portraitCatalog by viewModel.portraitCatalog.collectAsState()
    val isLoadingPortrait by viewModel.isLoadingPortrait.collectAsState()
    val selectedPortraitCostume by viewModel.selectedPortraitCostume.collectAsState()

    val showFileDialog by viewModel.showFileDialog.collectAsState()
    val dialogCategoryName by viewModel.dialogCategoryName.collectAsState()
    val dialogFileList by viewModel.dialogFileList.collectAsState()
    val dialogIsLoading by viewModel.dialogIsLoading.collectAsState()
    val dialogSelectedUrls by viewModel.dialogSelectedUrls.collectAsState()
    val manualSelectionMap by viewModel.manualSelectionMap.collectAsState()

    val favorites by viewModel.favorites.collectAsState()

    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsState()
    val searchError by viewModel.searchError.collectAsState()

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
        viewModel.errorEvent.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "重试",
                duration = SnackbarDuration.Short
            ).let { result ->
                if (result == SnackbarResult.ActionPerformed) {
                    if (message.contains("下载")) viewModel.startDownload()
                    else viewModel.performSearch()
                }
            }
        }
    }

    // 处理返回键
    BackHandler(enabled = selectedPortraitCharacter != null) {
        viewModel.clearSelectedPortraitCharacter()
    }
    BackHandler(enabled = selectedGroup != null && selectedPortraitCharacter == null) {
        viewModel.clearSelectedGroup()
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
                onKeywordChange = { viewModel.onSearchKeywordChange(it) },
                onSearch = {
                    focusManager.clearFocus()
                    viewModel.performSearch()
                },
                onClearHistory = {
                    AppPrefs.clearSearchHistory()
                    searchHistoryList = emptyList()
                },
                onHistoryItemClick = { item ->
                    viewModel.onSearchKeywordChange(item)
                    focusManager.clearFocus()
                    viewModel.performSearch()
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
                                    viewModel.onSearchModeChange(mode)
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
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 无网络横幅
                NetworkBanner(isNetworkAvailable)
                // 搜索失败提示横幅
                SearchErrorBanner(
                    searchError = searchError,
                    isNetworkAvailable = isNetworkAvailable,
                    onRetry = { viewModel.performSearch() }
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
                                onRetry = { viewModel.performSearch() },
                                onToggle = { viewModel.toggleFileSearchSelection(it) },
                                onSelectAll = { viewModel.selectAllFileSearchResults() },
                                onDownload = { viewModel.startDownload() }
                            )
                        }

                        SearchMode.PORTRAIT -> {
                            PortraitGrid(
                                characters = portraitCharacters,
                                characterAvatars = characterAvatars,
                                hasSearched = hasSearched,
                                favorites = favorites,
                                searchError = searchError,
                                onRetry = { viewModel.performSearch() },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onSelectCharacter = { viewModel.onSelectPortraitCharacter(it) }
                            )
                        }

                        else -> {
                            CategoryGroupList(
                                characterGroups = characterGroups,
                                characterAvatars = characterAvatars,
                                hasSearched = hasSearched,
                                favorites = favorites,
                                searchError = searchError,
                                onRetry = { viewModel.performSearch() },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onSelectGroup = { viewModel.onSelectGroup(it) }
                            )
                        }
                    }
                } // content Box
            } // Column

            // 浮动工具栏模式
            if (useDockedToolbar) {
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-16).dp)
                        .zIndex(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                                            viewModel.onSearchModeChange(mode)
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
            onDismissRequest = { viewModel.clearSelectedPortraitCharacter() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 0.dp
        ) {
            PortraitDetailContent(
                characterName = selectedPortraitCharacter!!,
                catalog = portraitCatalog,
                isLoading = isLoadingPortrait,
                selectedCostume = selectedPortraitCostume,
                isDownloading = isDownloading,
                onSelectCostume = { viewModel.selectPortraitCostume(it) },
                onDownload = { viewModel.startDownload() }
            )
        }
    }

    // 分类详情 BottomSheet
    if (selectedGroup != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearSelectedGroup() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            CategoryDetailContent(
                group = selectedGroup!!,
                subCategories = subCategories,
                checkedCategories = checkedCategories,
                isScanning = isScanningTree,
                isDownloading = isDownloading,
                manualSelectionMap = manualSelectionMap,
                onCheckAll = { viewModel.checkAllCategories() },
                onUncheckAll = { viewModel.uncheckAllCategories() },
                onCategoryChecked = { cat, checked -> viewModel.setCategoryChecked(cat, checked) },
                onOpenFileDialog = { viewModel.openFileDialog(it) },
                onDownload = { viewModel.startDownload() }
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
            onToggle = { viewModel.toggleDialogFileSelection(it) },
            onSelectAll = { viewModel.selectAllDialogFiles() },
            onClear = { viewModel.clearDialogSelection() },
            onConfirm = { viewModel.confirmFileDialog() },
            onDismiss = { viewModel.closeFileDialog() }
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
                            shape = RoundedCornerShape(20.dp)
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
                            shape = RoundedCornerShape(20.dp)
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
