package com.nekolaska.calabiyau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import coil3.compose.AsyncImage
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
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "卡拉彼丘 Wiki 下载器",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    actions = {
                        IconButton(onClick = { showLogs = true }) {
                            BadgedBox(
                                badge = {
                                    if (logs.isNotEmpty()) Badge { Text("${logs.size}") }
                                }
                            ) {
                                Icon(Icons.Default.Article, "日志")
                            }
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, "设置")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // 搜索栏和模式切换固定在顶部
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        SearchModeTabs(
                            current = searchMode,
                            onSelect = {
                                viewModel.onSearchModeChange(it)
                                focusManager.clearFocus()
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        SearchBar(
                            keyword = searchKeyword,
                            onKeywordChange = { viewModel.onSearchKeywordChange(it) },
                            onSearch = {
                                focusManager.clearFocus()
                                viewModel.performSearch()
                            },
                            onClear = { viewModel.onSearchKeywordChange("") },
                            isSearching = isSearching,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (isDownloading) {
                DownloadStatusBar(
                    progress = downloadProgress,
                    statusText = downloadStatusText
                )
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
            containerColor = MaterialTheme.colorScheme.surface,
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
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            CategoryDetailContent(
                group = selectedGroup!!,
                subCategories = subCategories,
                checkedCategories = checkedCategories,
                isScanning = isScanningTree,
                isDownloading = isDownloading,
                onCheckAll = { viewModel.checkAllCategories() },
                onUncheckAll = { viewModel.uncheckAllCategories() },
                onCategoryChecked = { cat, checked -> viewModel.setCategoryChecked(cat, checked) },
                onDownload = { viewModel.startDownload() }
            )
        }
    }
}

// --- Components ---

@Composable
fun SearchModeTabs(current: SearchMode, onSelect: (SearchMode) -> Unit) {
    val modes = listOf(
        SearchMode.VOICE_ONLY to "语音",
        SearchMode.ALL_CATEGORIES to "全部分类",
        SearchMode.FILE_SEARCH to "文件搜索",
        SearchMode.PORTRAIT to "立绘"
    )
    val selectedIndex = modes.indexOfFirst { it.first == current }.takeIf { it >= 0 } ?: 0

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 16.dp,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        divider = {}
    ) {
        modes.forEach { (mode, label) ->
            Tab(
                selected = current == mode,
                onClick = { onSelect(mode) },
                text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SearchBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary
        ),
        placeholder = { Text("搜索角色名称...") },
        leadingIcon = {
            if (isSearching) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Search, null)
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
    hasSearched: Boolean,
    onSelectCharacter: (String) -> Unit
) {
    if (characters.isEmpty()) {
        EmptyState(if (hasSearched) "未找到该角色" else "输入关键词搜索立绘")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(characters) { name ->
            ElevatedCard(
                onClick = { onSelectCharacter(name) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 如果有头像可以在这里显示
                    Text(
                        text = name.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
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
            EmptyState("该角色暂无立绘数据")
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
                .padding(horizontal = 24.dp),
            enabled = !isDownloading
        ) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(8.dp))
            Text("下载此装扮资产")
        }
    }
}

@Composable
fun CostumeCard(costume: PortraitCostume) {
    Card(
        shape = RoundedCornerShape(16.dp),
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
                    Text("无预览图", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Info Badge
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    text = "包含 ${costume.extraAssets.size + listOfNotNull(costume.illustration, costume.frontPreview, costume.backPreview).size} 个文件",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
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
        EmptyState(if (hasSearched) "未找到相关角色" else "输入关键词搜索角色语音")
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
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = group.characterName.take(1),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.characterName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "包含 ${group.subCategories.size} 个分类",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.rotate(180f), tint = MaterialTheme.colorScheme.outline) // Right arrow
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
    onCheckAll: () -> Unit,
    onUncheckAll: () -> Unit,
    onCategoryChecked: (String, Boolean) -> Unit,
    onDownload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(
                text = group.characterName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (isScanning) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        }

        if (!isScanning && subCategories.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                FilledTonalButton(onClick = onCheckAll, modifier = Modifier.weight(1f)) { Text("全选") }
                OutlinedButton(onClick = onUncheckAll, modifier = Modifier.weight(1f)) { Text("清空") }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(subCategories) { cat ->
                    val isRoot = cat == group.rootCategory
                    val cleanName = cat.removePrefix("Category:").removePrefix("分类:")
                    val displayName = if (isRoot) "根分类" else cleanName.replace(group.characterName, "").trimStart('/', ' ', '-')

                    CategoryItem(
                        name = displayName.ifBlank { cleanName },
                        checked = cat in checkedCategories,
                        isRoot = isRoot,
                        onCheckedChange = { onCategoryChecked(cat, it) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onDownload,
                enabled = !isDownloading && checkedCategories.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("下载选中 (${checkedCategories.size})")
            }
        }
    }
}

@Composable
fun CategoryItem(name: String, checked: Boolean, isRoot: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isRoot) FontWeight.Bold else FontWeight.Normal,
            color = if (isRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
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
    if (results.isEmpty()) {
        EmptyState(if (hasSearched) "未找到文件" else "按关键词搜索 Wiki 文件")
        return
    }
    
    // We need a state box to handle selection mode ideally, but simple list for now
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("找到 ${results.size} 个文件", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = onSelectAll) { Text("全选") }
        }
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results) { (name, url) ->
                FileItem(name, url, url in selectedUrls) { onToggle(url) }
            }
        }

        if (selectedUrls.isNotEmpty()) {
            Button(
                onClick = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = !isDownloading
            ) {
                Text("下载选中文件 (${selectedUrls.size})")
            }
        }
    }
}

@Composable
fun FileItem(name: String, url: String, isSelected: Boolean, onToggle: () -> Unit) {
    val isImage = url.endsWith(".png") || url.endsWith(".jpg")
    ElevatedCard(
        onClick = onToggle,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isImage) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)), Alignment.Center) {
                    Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
fun DownloadStatusBar(progress: Float, statusText: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 8.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("下载中...", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("运行日志", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(logs.reversed()) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = if (log.startsWith("[错误]")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
