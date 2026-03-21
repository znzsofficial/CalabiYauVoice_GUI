package com.nekolaska.calabiyau.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.MainViewModel
import com.nekolaska.calabiyau.SearchMode
import com.nekolaska.calabiyau.data.WikiEngine

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

    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "卡拉彼丘 Wiki 资源下载器",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 搜索模式 Tab
            item {
                SearchModeSelector(
                    current = searchMode,
                    onSelect = {
                        viewModel.onSearchModeChange(it)
                        focusManager.clearFocus()
                    }
                )
            }

            // 搜索栏（含搜索按钮）
            item {
                SearchBar(
                    keyword = searchKeyword,
                    onKeywordChange = { viewModel.onSearchKeywordChange(it) },
                    onSearch = {
                        focusManager.clearFocus()
                        viewModel.performSearch()
                    },
                    onClear = { viewModel.onSearchKeywordChange("") },
                    isSearching = isSearching
                )
            }

            // 并发数
            item {
                OutlinedTextField(
                    value = maxConcurrencyStr,
                    onValueChange = { viewModel.onMaxConcurrencyChange(it) },
                    label = { Text("最大并发数") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // 下载进度
            item {
                AnimatedVisibility(
                    visible = isDownloading,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    ElevatedCard(shape = RoundedCornerShape(12.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "下载中",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth(),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            if (downloadStatusText.isNotEmpty()) {
                                Text(
                                    text = downloadStatusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 主内容
            when (searchMode) {
                SearchMode.FILE_SEARCH -> {
                    item {
                        FileSearchContent(
                            results = fileSearchResults,
                            selectedUrls = fileSearchSelectedUrls,
                            onToggle = { viewModel.toggleFileSearchSelection(it) },
                            onSelectAll = { viewModel.selectAllFileSearchResults() },
                            onClearAll = { viewModel.clearFileSearchSelection() },
                            onDownload = { viewModel.startDownload() },
                            isDownloading = isDownloading,
                            hasSearched = hasSearched
                        )
                    }
                }
                else -> {
                    item {
                        CharacterSearchContent(
                            characterGroups = characterGroups,
                            characterAvatars = characterAvatars,
                            selectedGroup = selectedGroup,
                            subCategories = subCategories,
                            checkedCategories = checkedCategories,
                            isScanningTree = isScanningTree,
                            isDownloading = isDownloading,
                            hasSearched = hasSearched,
                            onSelectGroup = { viewModel.onSelectGroup(it) },
                            onCategoryChecked = { cat, checked -> viewModel.setCategoryChecked(cat, checked) },
                            onCheckAll = { viewModel.checkAllCategories() },
                            onUncheckAll = { viewModel.uncheckAllCategories() },
                            onDownload = { viewModel.startDownload() }
                        )
                    }
                }
            }

            // 日志
            if (logs.isNotEmpty()) {
                item {
                    Text(
                        "运行日志",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                item {
                    ElevatedCard(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            logs.takeLast(30).reversed().forEach { log ->
                                val isError = log.startsWith("[错误]")
                                Text(
                                    text = log,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SearchModeSelector(current: SearchMode, onSelect: (SearchMode) -> Unit) {
    val modes = listOf(
        SearchMode.VOICE_ONLY to "语音",
        SearchMode.ALL_CATEGORIES to "全部分类",
        SearchMode.FILE_SEARCH to "文件搜索"
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = current == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                icon = {
                    if (mode == SearchMode.ALL_CATEGORIES) {
                        SegmentedButtonDefaults.Icon(active = current == mode) {
                            Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        SegmentedButtonDefaults.Icon(active = current == mode)
                    }
                }
            ) {
                Text(label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SearchBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    isSearching: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text("搜索关键词") },
            placeholder = { Text("输入角色名称...") },
            trailingIcon = {
                if (keyword.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Clear, contentDescription = "清空")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = RoundedCornerShape(12.dp)
        )
        Button(
            onClick = onSearch,
            enabled = keyword.isNotBlank() && !isSearching,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
        ) {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.Search, contentDescription = "搜索", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun FileSearchContent(
    results: List<Pair<String, String>>,
    selectedUrls: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onDownload: () -> Unit,
    isDownloading: Boolean,
    hasSearched: Boolean
) {
    var previewUrl by remember { mutableStateOf<String?>(null) }

    if (previewUrl != null) {
        ImagePreviewDialog(url = previewUrl!!, onDismiss = { previewUrl = null })
    }

    if (results.isEmpty()) {
        if (hasSearched) {
            EmptyStateCard(message = "未找到匹配的文件，请尝试其他关键词")
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElevatedCard(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "找到 ${results.size} 个文件，已选 ${selectedUrls.size} 个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onSelectAll) { Text("全选") }
                    TextButton(onClick = onClearAll) { Text("清空") }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                results.forEach { (name, url) ->
                    val isImage = isImageUrl(url)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = url in selectedUrls,
                            onCheckedChange = { onToggle(url) }
                        )
                        if (isImage) {
                            AsyncImage(
                                model = url,
                                contentDescription = name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { previewUrl = url }
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (isImage) Modifier.clickable { previewUrl = url }
                                    else Modifier
                                )
                        )
                    }
                }
            }
        }
        Button(
            onClick = onDownload,
            enabled = selectedUrls.isNotEmpty() && !isDownloading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("下载选中文件 (${selectedUrls.size})")
        }
    }
}

@Composable
private fun CharacterSearchContent(
    characterGroups: List<WikiEngine.CharacterGroup>,
    characterAvatars: Map<String, String> = emptyMap(),
    selectedGroup: WikiEngine.CharacterGroup?,
    subCategories: List<String>,
    checkedCategories: List<String>,
    isScanningTree: Boolean,
    isDownloading: Boolean,
    hasSearched: Boolean,
    onSelectGroup: (WikiEngine.CharacterGroup) -> Unit,
    onCategoryChecked: (String, Boolean) -> Unit,
    onCheckAll: () -> Unit,
    onUncheckAll: () -> Unit,
    onDownload: () -> Unit
) {
    if (characterGroups.isEmpty()) {
        if (hasSearched) {
            EmptyStateCard(message = "未找到匹配的角色，请尝试其他关键词")
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 角色列表
        ElevatedCard(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    "角色列表（${characterGroups.size} 个结果）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider()
                characterGroups.forEach { group ->
                    val isSelected = selectedGroup == group
                    Surface(
                        onClick = { onSelectGroup(group) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surface
                    ) {
                        val avatarUrl = characterAvatars[group.characterName]
                        ListItem(
                            headlineContent = {
                                Text(
                                    group.characterName,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            supportingContent = {
                                Text(
                                    "${group.subCategories.size} 个分类",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            leadingContent = {
                                if (avatarUrl != null) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = group.characterName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = group.characterName.take(1),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            trailingContent = if (isSelected) ({
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }) else null,
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent
                            )
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }

        // 分类树
        if (selectedGroup != null) {
            ElevatedCard(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${selectedGroup.characterName} 的分类",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (isScanningTree) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }

                    if (!isScanningTree && subCategories.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onCheckAll, modifier = Modifier.weight(1f)) { Text("全选") }
                            OutlinedButton(onClick = onUncheckAll, modifier = Modifier.weight(1f)) { Text("清空") }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // 分类层级显示
                        val rootCategory = selectedGroup.rootCategory
                        subCategories.forEach { cat ->
                            val isRoot = cat == rootCategory
                            val displayName = cat
                                .removePrefix("Category:")
                                .removePrefix("分类:")
                                .let { name ->
                                    // 去掉角色名前缀，只保留子分类名
                                    val charName = selectedGroup.characterName
                                    if (!isRoot && name.startsWith(charName)) {
                                        name.removePrefix(charName).trimStart('/')
                                            .trimStart('-').trimStart('_').trim()
                                            .ifEmpty { name }
                                    } else name
                                }
                            val indent = if (isRoot) 0.dp else 16.dp

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = indent, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = cat in checkedCategories,
                                    onCheckedChange = { onCategoryChecked(cat, it) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayName,
                                        style = if (isRoot) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        else MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isRoot) {
                                        Text(
                                            text = "根分类",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onDownload,
                            enabled = checkedCategories.isNotEmpty() && !isDownloading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("下载选中分类 (${checkedCategories.size})")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImagePreviewDialog(url: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { /* 阻止点击穿透 */ }
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

private fun isImageUrl(url: String): Boolean {
    val clean = url.substringBefore('?').lowercase()
    return clean.endsWith(".png") || clean.endsWith(".jpg") || clean.endsWith(".jpeg") ||
            clean.endsWith(".webp") || clean.endsWith(".gif")
}
