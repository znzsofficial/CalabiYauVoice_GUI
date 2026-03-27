package com.nekolaska.calabiyau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.os.Build
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.nekolaska.calabiyau.MainViewModel
import com.nekolaska.calabiyau.SearchMode
import com.nekolaska.calabiyau.data.WikiEngine
import kotlinx.coroutines.launch
import portrait.CharacterPortraitCatalog
import portrait.PortraitCostume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
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
    val maxConcurrencyStr by viewModel.maxConcurrencyStr.collectAsState()
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

    val focusManager = LocalFocusManager.current
    var showLogs by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // 设置页面返回键拦截
    BackHandler(enabled = showSettings) {
        showSettings = false
    }

    // 设置页面
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
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
                            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "卡拉彼丘",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.weight(1f))
                        FilledTonalIconButton(
                            onClick = { showLogs = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            BadgedBox(
                                badge = {
                                    if (logs.isNotEmpty()) Badge { Text("${logs.size}") }
                                }
                            ) {
                                Icon(Icons.Outlined.Article, "日志", modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        FilledTonalIconButton(
                            onClick = { showSettings = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(Icons.Outlined.Settings, "设置", modifier = Modifier.size(20.dp))
                        }
                    }

                    // 搜索栏
                    SearchBar(
                        keyword = searchKeyword,
                        onKeywordChange = { viewModel.onSearchKeywordChange(it) },
                        onSearch = {
                            focusManager.clearFocus()
                            viewModel.performSearch()
                        },
                        onClear = { viewModel.onSearchKeywordChange("") },
                        isSearching = isSearching,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
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
                // 底部导航栏
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp
                ) {
                    val modes = listOf(
                        Triple(SearchMode.VOICE_ONLY, "语音", Icons.Outlined.RecordVoiceOver),
                        Triple(SearchMode.ALL_CATEGORIES, "全部分类", Icons.Outlined.Category),
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
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (searchMode) {
                SearchMode.FILE_SEARCH -> {
                    FileSearchList(
                        results = fileSearchResults,
                        selectedUrls = fileSearchSelectedUrls,
                        hasSearched = hasSearched,
                        isDownloading = isDownloading,
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
                        onSelectCharacter = { viewModel.onSelectPortraitCharacter(it) }
                    )
                }
                else -> {
                    CategoryGroupList(
                        characterGroups = characterGroups,
                        characterAvatars = characterAvatars,
                        hasSearched = hasSearched,
                        onSelectGroup = { viewModel.onSelectGroup(it) }
                    )
                }
            }
        }
    }

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

// --- Components ---

@Composable
fun SearchBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    TextField(
        value = keyword,
        onValueChange = onKeywordChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        placeholder = {
            Text(
                "搜索角色名称...",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            if (isSearching) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.Search, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingIcon = {
            if (keyword.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, "清空")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

@Composable
fun PortraitGrid(
    characters: List<String>,
    characterAvatars: Map<String, String>,
    hasSearched: Boolean,
    onSelectCharacter: (String) -> Unit
) {
    if (characters.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Image,
            message = if (hasSearched) "未找到该角色" else "输入关键词搜索立绘"
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(characters) { name ->
            val avatarUrl = characterAvatars[name]
            Card(
                onClick = { onSelectCharacter(name) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = name,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = name.firstOrNull()?.toString() ?: "?",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortraitDetailContent(
    characterName: String,
    catalog: CharacterPortraitCatalog?,
    isLoading: Boolean,
    selectedCostume: PortraitCostume?,
    isDownloading: Boolean,
    onSelectCostume: (PortraitCostume) -> Unit,
    onDownload: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope() // Add scope

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f) // Limit height
            .padding(bottom = 16.dp)
    ) {
        // Header
        Text(
            text = characterName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (catalog == null || catalog.costumes.isEmpty()) {
            EmptyState(icon = Icons.Outlined.HideImage, message = "该角色暂无立绘数据")
            return
        }

        val costumes = catalog.costumes
        val pagerState = rememberPagerState(pageCount = { costumes.size })

        // Sync selected costume with pager
        LaunchedEffect(pagerState.currentPage) {
            onSelectCostume(costumes[pagerState.currentPage])
        }

        // Costume Tabs
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 24.dp,
            divider = {},
            containerColor = Color.Transparent,
            indicator = { tabPositions ->
                if (pagerState.currentPage < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        height = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            costumes.forEachIndexed { index, costume ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(costume.name) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Content Pager
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 32.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.weight(1f)
        ) { page ->
            val costume = costumes[page]
            CostumeCard(costume = costume)
        }

        Spacer(Modifier.height(16.dp))

        // Download Action
        Button(
            onClick = onDownload,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 24.dp),
            enabled = !isDownloading,
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(8.dp))
            Text("下载此装扮资产", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun CostumeCard(costume: PortraitCostume) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(Modifier.fillMaxSize()) {
            // Priority: Illustration -> Front -> Back
            val previewUrl = costume.illustration?.url
                ?: costume.frontPreview?.url
                ?: costume.backPreview?.url

            if (previewUrl != null) {
                AsyncImage(
                    model = previewUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.HideImage, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("无预览图", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Info Badge — pill style
            val fileCount = costume.extraAssets.size + listOfNotNull(costume.illustration, costume.frontPreview, costume.backPreview).size
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.75f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Outlined.Collections, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.inverseOnSurface
                    )
                    Text(
                        text = "$fileCount 个文件",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryGroupList(
    characterGroups: List<WikiEngine.CharacterGroup>,
    characterAvatars: Map<String, String>,
    hasSearched: Boolean,
    onSelectGroup: (WikiEngine.CharacterGroup) -> Unit
) {
    if (characterGroups.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.RecordVoiceOver,
            message = if (hasSearched) "未找到相关角色" else "输入关键词搜索角色语音"
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(characterGroups) { group ->
            GroupCard(group, characterAvatars[group.characterName], onClick = { onSelectGroup(group) })
        }
    }
}

@Composable
fun GroupCard(
    group: WikiEngine.CharacterGroup,
    avatarUrl: String?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = group.characterName.take(1),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.characterName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${group.subCategories.size} 个分类",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalIconButton(
                onClick = onClick,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, null,
                    modifier = Modifier.size(18.dp).rotate(180f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CategoryDetailContent(
    group: WikiEngine.CharacterGroup,
    subCategories: List<String>,
    checkedCategories: List<String>,
    isScanning: Boolean,
    isDownloading: Boolean,
    manualSelectionMap: Map<String, List<Pair<String, String>>>,
    onCheckAll: () -> Unit,
    onUncheckAll: () -> Unit,
    onCategoryChecked: (String, Boolean) -> Unit,
    onOpenFileDialog: (String) -> Unit,
    onDownload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.characterName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (subCategories.isNotEmpty()) {
                    Text(
                        text = "已选 ${checkedCategories.size}/${subCategories.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isScanning) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        }

        if (!isScanning && subCategories.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                FilledTonalButton(
                    onClick = onCheckAll,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("全选")
                }
                OutlinedButton(
                    onClick = onUncheckAll,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.RemoveDone, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("清空")
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(subCategories) { cat ->
                    val isRoot = cat == group.rootCategory
                    val cleanName = cat.removePrefix("Category:").removePrefix("分类:")
                    val displayName = if (isRoot) "根分类" else cleanName.replace(group.characterName, "").trimStart('/', ' ', '-')
                    val manualFiles = manualSelectionMap[cat]

                    CategoryItem(
                        name = displayName.ifBlank { cleanName },
                        checked = cat in checkedCategories,
                        isRoot = isRoot,
                        manualCount = manualFiles?.size,
                        onCheckedChange = { onCategoryChecked(cat, it) },
                        onSelectFiles = { onOpenFileDialog(cat) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onDownload,
                enabled = !isDownloading && checkedCategories.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("下载选中 (${checkedCategories.size})", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun CategoryItem(
    name: String,
    checked: Boolean,
    isRoot: Boolean,
    manualCount: Int?,
    onCheckedChange: (Boolean) -> Unit,
    onSelectFiles: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = if (checked)
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        else
            Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isRoot) FontWeight.Bold else FontWeight.Normal,
                    color = if (isRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (manualCount != null) {
                    Text(
                        text = "已选 $manualCount 个文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            // 文件选择按钮
            IconButton(
                onClick = onSelectFiles,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.Checklist, "选择文件",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FileSearchList(
    results: List<Pair<String, String>>,
    selectedUrls: Set<String>,
    hasSearched: Boolean,
    isDownloading: Boolean,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit
) {
    // 图片预览弹窗状态
    var previewImage by remember { mutableStateOf<Pair<String, String>?>(null) }

    if (results.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.FindInPage,
            message = if (hasSearched) "未找到文件" else "按关键词搜索 Wiki 文件"
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        // Result header
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "找到 ${results.size} 个文件",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = onSelectAll,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("全选", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results) { (name, url) ->
                FileItem(
                    name = name,
                    url = url,
                    isSelected = url in selectedUrls,
                    onToggle = { onToggle(url) },
                    onPreview = { previewImage = Pair(name, url) }
                )
            }
        }

        // Download FAB bar
        AnimatedVisibility(
            visible = selectedUrls.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    enabled = !isDownloading,
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("下载选中文件 (${selectedUrls.size})", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    // 图片预览弹窗
    previewImage?.let { (name, url) ->
        ImagePreviewDialog(
            title = name,
            imageUrl = url,
            onDismiss = { previewImage = null }
        )
    }
}

@Composable
fun FileItem(
    name: String,
    url: String,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onPreview: () -> Unit
) {
    val isImage = url.lowercase().let {
        it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") ||
                it.endsWith(".webp") || it.endsWith(".gif")
    }
    Card(
        onClick = onToggle,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isImage) {
                Box {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPreview() },
                        contentScale = ContentScale.Crop
                    )
                    // 预览图标提示
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.6f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ZoomIn, null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.inverseOnSurface
                            )
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.InsertDriveFile, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                color = if (isSelected)
                    MaterialTheme.colorScheme.onSecondaryContainer
                else
                    MaterialTheme.colorScheme.onSurface
            )
            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ImagePreviewDialog(
    title: String,
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                // Header
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Image
                val context = LocalContext.current
                val isGif = imageUrl.lowercase().let {
                    it.endsWith(".gif") || it.contains(".gif?")
                }
                val imageLoader = remember(isGif) {
                    if (isGif) {
                        ImageLoader.Builder(context)
                            .components {
                                if (Build.VERSION.SDK_INT >= 28) {
                                    add(AnimatedImageDecoder.Factory())
                                } else {
                                    add(GifDecoder.Factory())
                                }
                            }
                            .build()
                    } else null
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGif && imageLoader != null) {
                        val request = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .build()
                        AsyncImage(
                            model = request,
                            imageLoader = imageLoader,
                            contentDescription = title,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = title,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSelectionSheet(
    categoryName: String,
    files: List<Pair<String, String>>,
    selectedUrls: Set<String>,
    isLoading: Boolean,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var previewImage by remember { mutableStateOf<Pair<String, String>?>(null) }
    var searchKeyword by remember { mutableStateOf("") }

    val filteredFiles = remember(files, searchKeyword) {
        if (searchKeyword.isBlank()) files
        else files.filter { (name, _) -> name.contains(searchKeyword, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(bottom = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = categoryName.removePrefix("Category:").removePrefix("分类:"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!isLoading && files.isNotEmpty()) {
                        Text(
                            text = "已选 ${selectedUrls.size} / ${files.size} 个文件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledTonalIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                }
            }

            // Search field
            TextField(
                value = searchKeyword,
                onValueChange = { searchKeyword = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                placeholder = { Text("搜索文件名...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (searchKeyword.isNotEmpty()) {
                        IconButton(onClick = { searchKeyword = "" }) {
                            Icon(Icons.Default.Close, "清空")
                        }
                    }
                },
                singleLine = true
            )

            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onSelectAll,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("全选", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onClear,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("清空", style = MaterialTheme.typography.labelMedium)
                }

                // Language suffix filters
                Spacer(Modifier.weight(1f))
                listOf("CN", "JP", "EN").forEach { lang ->
                    val targets = files
                        .filter { (n, _) -> n.uppercase().let { it.endsWith(lang) || it.contains("$lang.") } }
                        .map { it.second }
                    if (targets.isNotEmpty()) {
                        val isSelected = targets.all { it in selectedUrls }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    targets.forEach { onToggle(it) }
                                } else {
                                    targets.filter { it !in selectedUrls }.forEach { onToggle(it) }
                                }
                            },
                            label = { Text(lang, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            // File list
            when {
                isLoading -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("正在加载文件列表...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                filteredFiles.isEmpty() -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            if (files.isEmpty()) "无文件" else "无搜索结果",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredFiles) { (name, url) ->
                            FileItem(
                                name = name,
                                url = url,
                                isSelected = url in selectedUrls,
                                onToggle = { onToggle(url) },
                                onPreview = { previewImage = Pair(name, url) }
                            )
                        }
                    }
                }
            }

            // Confirm button
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 24.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("确认选择 (${selectedUrls.size})", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    // 图片预览弹窗
    previewImage?.let { (name, url) ->
        ImagePreviewDialog(
            title = name,
            imageUrl = url,
            onDismiss = { previewImage = null }
        )
    }
}

@Composable
fun DownloadStatusBar(progress: Float, statusText: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "下载中",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LogsDialog(logs: List<String>, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "运行日志",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    FilledTonalIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (logs.isEmpty()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无日志",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(logs.reversed()) { log ->
                            val isError = log.startsWith("[错误]")
                            Surface(
                                color = if (isError)
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                else
                                    Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 18.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector = Icons.Outlined.SearchOff,
    message: String
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon, null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
