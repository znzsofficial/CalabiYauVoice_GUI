package com.nekolaska.calabiyau.ui

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.ImageRequest

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
    val isAudio = url.lowercase().let {
        it.endsWith(".ogg") || it.endsWith(".mp3") || it.endsWith(".wav") ||
                it.endsWith(".flac") || it.endsWith(".m4a") || it.endsWith(".aac")
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
            } else if (isAudio) {
                AudioPlayButton(source = url, size = 48)
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
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
                        ZoomableImage(
                            model = imageUrl,
                            contentDescription = title,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
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
