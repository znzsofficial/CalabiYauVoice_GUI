package com.nekolaska.calabiyau.feature.download

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.media.AudioPlayButton
import com.nekolaska.calabiyau.core.ui.EmptyState
import com.nekolaska.calabiyau.core.ui.ZoomableImage
import com.nekolaska.calabiyau.core.ui.smoothCornerShape

@Composable
fun FileSearchList(
    results: List<Pair<String, String>>,
    selectedUrls: Set<String>,
    hasSearched: Boolean,
    isDownloading: Boolean,
    searchError: String? = null,
    onRetry: (() -> Unit)? = null,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onInvertSelection: () -> Unit,
    onDownload: () -> Unit
) {
    // 图片预览弹窗状态
    var previewImage by remember { mutableStateOf<Pair<String, String>?>(null) }

    if (results.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.FindInPage,
            message = if (hasSearched) "未找到文件" else "按关键词搜索 Wiki 文件",
            errorMessage = if (hasSearched) searchError else null,
            onRetry = onRetry
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        // Result header
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = smoothCornerShape(16.dp),
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val allSelected = selectedUrls.size == results.size
                    FilledTonalButton(
                        onClick = { if (allSelected) onClearSelection() else onSelectAll() },
                        shape = smoothCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(if (allSelected) "取消全选" else "全选", style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(
                        onClick = onInvertSelection,
                        shape = smoothCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("反选", style = MaterialTheme.typography.labelMedium)
                    }
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
                    shape = smoothCornerShape(28.dp)
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
    val isAudio = url.lowercase().let {
        it.endsWith(".ogg") || it.endsWith(".mp3") || it.endsWith(".wav") ||
                it.endsWith(".flac") || it.endsWith(".m4a") || it.endsWith(".aac")
    }
    Card(
        onClick = onToggle,
        shape = smoothCornerShape(16.dp),
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
                            .clip(smoothCornerShape(12.dp))
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
            } else if (isAudio) {
                AudioPlayButton(source = url, size = 48)
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = smoothCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Outlined.InsertDriveFile, null,
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
            shape = smoothCornerShape(28.dp),
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
                // Image — 全局 SingletonImageLoader 已注册 AnimatedImageDecoder / GifDecoder，
                // GIF 与静态图都直接走 ZoomableImage / AsyncImage。
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    ZoomableImage(
                        model = imageUrl,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(smoothCornerShape(16.dp))
                    )
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
    onInvert: () -> Unit,
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
        shape = smoothCornerShape(28.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(bottom = 16.dp)
        ) {
            // ── 标题区 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = smoothCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Folder, null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = categoryName.removePrefix("Category:").removePrefix("分类:"),
                        style = MaterialTheme.typography.titleMedium,
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

            // ── 搜索栏 ──
            TextField(
                value = searchKeyword,
                onValueChange = { searchKeyword = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = smoothCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                placeholder = { Text("搜索文件名…") },
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

            // ── 操作栏：全选/清空 + 语言筛选 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 全选/清空合并为一个 FilterChip
                val allSelected = files.isNotEmpty() && selectedUrls.size == files.size
                FilterChip(
                    selected = allSelected,
                    onClick = { if (allSelected) onClear() else onSelectAll() },
                    label = { Text(if (allSelected) "取消全选" else "全选") },
                    leadingIcon = {
                        Icon(
                            if (allSelected) Icons.Default.RemoveDone else Icons.Default.DoneAll,
                            null, modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = smoothCornerShape(12.dp)
                )

                FilterChip(
                    selected = false,
                    onClick = onInvert,
                    label = { Text("反选") },
                    leadingIcon = {
                        Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(16.dp))
                    },
                    shape = smoothCornerShape(12.dp)
                )

                Spacer(Modifier.weight(1f))

                // 语言快捷筛选
                listOf("CN" to "中", "JP" to "日", "EN" to "英").forEach { (lang, label) ->
                    val targets = files
                        .filter { (n, _) -> n.uppercase().let { it.endsWith(lang) || it.contains("$lang.") } }
                        .map { it.second }
                    if (targets.isNotEmpty()) {
                        val isLangSelected = targets.all { it in selectedUrls }
                        FilterChip(
                            selected = isLangSelected,
                            onClick = {
                                if (isLangSelected) {
                                    targets.forEach { onToggle(it) }
                                } else {
                                    targets.filter { it !in selectedUrls }.forEach { onToggle(it) }
                                }
                            },
                            label = { Text(label) },
                            shape = smoothCornerShape(12.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // ── 文件列表 ──
            when {
                isLoading -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("正在加载文件列表…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                filteredFiles.isEmpty() -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                if (files.isEmpty()) Icons.Default.FolderOff else Icons.Default.SearchOff,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (files.isEmpty()) "此分类下无文件" else "无匹配结果",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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

            // ── 确认按钮 ──
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 24.dp),
                enabled = !isLoading,
                shape = smoothCornerShape(28.dp)
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
