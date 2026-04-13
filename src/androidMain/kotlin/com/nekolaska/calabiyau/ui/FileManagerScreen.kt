package com.nekolaska.calabiyau.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.log10
import kotlin.math.pow

/**
 * 内置文件管理器，支持：
 * - 浏览保存目录下的文件和子文件夹
 * - 预览图片 / 打开文件
 * - 重命名、删除、分享
 * - 返回上级目录
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(rootPath: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val showSnack = rememberSnackbarLauncher()
    var currentDir by remember { mutableStateOf(File(rootPath)) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshCounter by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // 选中文件的操作菜单
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    // 图片画廊预览（所有图片文件列表 + 初始索引）
    var galleryImages by remember { mutableStateOf<List<File>>(emptyList()) }
    var galleryInitialIndex by remember { mutableIntStateOf(0) }
    var showGallery by remember { mutableStateOf(false) }

    // ── 多选模式状态 ──
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) } // 用绝对路径作 key
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    // 离开文件管理器时停止音频播放
    DisposableEffect(Unit) {
        onDispose { AudioPlayerManager.stop() }
    }

    // 切换目录时退出多选模式
    LaunchedEffect(currentDir) {
        isSelectionMode = false
        selectedFiles = emptySet()
    }

    // 加载当前目录文件列表
    LaunchedEffect(currentDir, refreshCounter) {
        isLoading = true
        files = withContext(Dispatchers.IO) {
            val dir = currentDir
            if (!dir.exists()) dir.mkdirs()
            val list = dir.listFiles()?.toList() ?: emptyList()
            // 文件夹在前，文件在后，各自按名称排序
            list.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
        isLoading = false
    }

    // 返回键处理：多选模式下先退出多选，子目录时返回上级，在根目录时退出
    val isAtRoot = currentDir.absolutePath == File(rootPath).absolutePath
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedFiles = emptySet()
    }
    BackHandler(enabled = !isAtRoot && !isSelectionMode) {
        currentDir = currentDir.parentFile ?: currentDir
    }

    // 多选模式下选中的文件对象列表
    val selectedFileObjects by remember(selectedFiles, files) {
        derivedStateOf {
            files.filter { it.absolutePath in selectedFiles }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = if (isSelectionMode) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    if (isSelectionMode) {
                        // ── 多选模式顶栏 ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                isSelectionMode = false
                                selectedFiles = emptySet()
                            }) {
                                Icon(Icons.Default.Close, "退出多选")
                            }
                            Text(
                                text = "已选 ${selectedFiles.size} 项",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            // 全选 / 取消全选
                            val allSelected = selectedFiles.size == files.size && files.isNotEmpty()
                            IconButton(onClick = {
                                selectedFiles = if (allSelected) emptySet()
                                    else files.map { it.absolutePath }.toSet()
                            }) {
                                Icon(
                                    if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                    if (allSelected) "取消全选" else "全选"
                                )
                            }
                            // 批量分享（仅文件，不含文件夹）
                            val shareableCount = selectedFileObjects.count { it.isFile }
                            IconButton(
                                onClick = { shareFiles(context, selectedFileObjects.filter { it.isFile }, showSnack) },
                                enabled = shareableCount > 0
                            ) {
                                Icon(Icons.Outlined.Share, "批量分享")
                            }
                            // 批量删除
                            IconButton(
                                onClick = { showBatchDeleteDialog = true },
                                enabled = selectedFiles.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Outlined.Delete, "批量删除",
                                    tint = if (selectedFiles.isNotEmpty())
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        }
                    } else {
                        // ── 普通模式顶栏 ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                if (!isAtRoot) {
                                    currentDir = currentDir.parentFile ?: currentDir
                                } else {
                                    onBack()
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isAtRoot) "文件管理" else currentDir.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${files.size} 项" + if (!isAtRoot) " · ${
                                        currentDir.absolutePath.removePrefix(rootPath).trimStart('/')
                                    }" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                files.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.FolderOpen, null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "文件夹为空",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(files, key = { it.absolutePath }) { file ->
                            val isSelected = file.absolutePath in selectedFiles
                            FileListItem(
                                file = file,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        // 多选模式下：点击切换选中
                                        selectedFiles = if (isSelected)
                                            selectedFiles - file.absolutePath
                                        else
                                            selectedFiles + file.absolutePath
                                    } else {
                                        if (file.isDirectory) {
                                            currentDir = file
                                        } else if (file.isImageFile()) {
                                            val images = files.filter { it.isImageFile() }
                                            galleryImages = images
                                            galleryInitialIndex = images.indexOf(file).coerceAtLeast(0)
                                            showGallery = true
                                        } else if (file.isAudioFile()) {
                                            AudioPlayerManager.play(file.absolutePath)
                                        } else {
                                            openFile(context, file, showSnack)
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        // 长按进入多选模式并选中当前项
                                        isSelectionMode = true
                                        selectedFiles = setOf(file.absolutePath)
                                    } else {
                                        // 已在多选模式：切换选中
                                        selectedFiles = if (isSelected)
                                            selectedFiles - file.absolutePath
                                        else
                                            selectedFiles + file.absolutePath
                                    }
                                },
                                onMoreClick = {
                                    selectedFile = file
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 文件操作菜单 BottomSheet ──
    if (selectedFile != null) {
        val file = selectedFile!!
        ModalBottomSheet(
            onDismissRequest = { selectedFile = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = smoothCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                // 文件信息头
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FileIcon(file, size = 48)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            file.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            buildString {
                                if (file.isFile) append(formatFileSize(file.length()))
                                append(" · ")
                                append(formatDate(file.lastModified()))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(Modifier.height(8.dp))

                // 打开
                if (file.isFile) {
                    FileActionItem(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        label = "打开",
                        onClick = {
                            openFile(context, file, showSnack)
                            selectedFile = null
                        }
                    )
                }

                // 预览（仅图片）
                if (file.isFile && file.isImageFile()) {
                    FileActionItem(
                        icon = Icons.Outlined.Image,
                        label = "预览",
                        onClick = {
                            val images = files.filter { it.isFile && it.isImageFile() }
                            galleryImages = images
                            galleryInitialIndex = images.indexOf(file).coerceAtLeast(0)
                            showGallery = true
                            selectedFile = null
                        }
                    )
                }

                // 播放（仅音频）
                if (file.isFile && file.isAudioFile()) {
                    val isThisPlaying = AudioPlayerManager.playingSource.value == file.absolutePath &&
                            AudioPlayerManager.isPlaying.value
                    FileActionItem(
                        icon = if (isThisPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        label = if (isThisPlaying) "停止播放" else "播放",
                        onClick = {
                            if (isThisPlaying) {
                                AudioPlayerManager.stop()
                            } else {
                                AudioPlayerManager.play(file.absolutePath)
                            }
                            selectedFile = null
                        }
                    )
                }

                // 分享
                if (file.isFile) {
                    FileActionItem(
                        icon = Icons.Outlined.Share,
                        label = "分享",
                        onClick = {
                            shareFile(context, file, showSnack)
                            selectedFile = null
                        }
                    )
                }

                // 重命名
                FileActionItem(
                    icon = Icons.Outlined.Edit,
                    label = "重命名",
                    onClick = {
                        showRenameDialog = true
                    }
                )

                // 删除
                FileActionItem(
                    icon = Icons.Outlined.Delete,
                    label = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = {
                        showDeleteDialog = true
                    }
                )
            }
        }
    }

    // ── 重命名对话框 ──
    if (showRenameDialog && selectedFile != null) {
        val file = selectedFile!!
        var newName by remember { mutableStateOf(file.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = smoothCornerShape(16.dp)
                )
            },
            shape = smoothCornerShape(28.dp),
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        val target = File(file.parent, newName)
                        if (target.exists()) {
                            showSnack("同名文件已存在")
                        } else if (file.renameTo(target)) {
                            showSnack("已重命名")
                            refreshCounter++
                            selectedFile = null
                            showRenameDialog = false
                        } else {
                            showSnack("重命名失败")
                        }
                    },
                    enabled = newName.isNotBlank() && newName != file.name
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("取消") }
            }
        )
    }

    // ── 删除确认对话框 ──
    if (showDeleteDialog && selectedFile != null) {
        val file = selectedFile!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除确认") },
            text = {
                Text(
                    if (file.isDirectory) "确定要删除文件夹「${file.name}」及其所有内容吗？"
                    else "确定要删除「${file.name}」吗？"
                )
            },
            shape = smoothCornerShape(28.dp),
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        showDeleteDialog = false
                        selectedFile = null
                        scope.launch {
                            val success = withContext(Dispatchers.IO) {
                                forceDelete(file)
                            }
                            if (success) {
                                showSnack("已删除")
                                refreshCounter++
                            } else {
                                showSnack("删除失败，请检查存储权限")
                            }
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    // ── 批量删除确认对话框 ──
    if (showBatchDeleteDialog && selectedFiles.isNotEmpty()) {
        val count = selectedFiles.size
        val folderCount = selectedFileObjects.count { it.isDirectory }
        val fileCount = count - folderCount
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("批量删除确认") },
            text = {
                Text(
                    buildString {
                        append("确定要删除选中的 $count 项")
                        val parts = mutableListOf<String>()
                        if (folderCount > 0) parts.add("$folderCount 个文件夹")
                        if (fileCount > 0) parts.add("$fileCount 个文件")
                        if (parts.isNotEmpty()) append("（${parts.joinToString("、")}）")
                        append("吗？")
                        if (folderCount > 0) append("\n\n⚠️ 文件夹将连同其中所有内容一起删除！")
                    }
                )
            },
            shape = smoothCornerShape(28.dp),
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        showBatchDeleteDialog = false
                        val toDelete = selectedFileObjects.toList()
                        isSelectionMode = false
                        selectedFiles = emptySet()
                        scope.launch {
                            var successCount = 0
                            var failCount = 0
                            withContext(Dispatchers.IO) {
                                toDelete.forEach { file ->
                                    if (forceDelete(file)) successCount++ else failCount++
                                }
                            }
                            val msg = buildString {
                                append("已删除 $successCount 项")
                                if (failCount > 0) append("，$failCount 项失败")
                            }
                            showSnack(msg)
                            refreshCounter++
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) { Text("删除 $count 项") }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    // ── 图片画廊预览对话框（支持左右滑动） ──
    if (showGallery && galleryImages.isNotEmpty()) {
        val pagerState = rememberPagerState(
            initialPage = galleryInitialIndex,
            pageCount = { galleryImages.size }
        )
        val currentFile = galleryImages.getOrNull(pagerState.currentPage)

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showGallery = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = smoothCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = currentFile?.name ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${pagerState.currentPage + 1} / ${galleryImages.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            if (currentFile != null) {
                                IconButton(onClick = { shareFile(context, currentFile, showSnack) }) {
                                    Icon(Icons.Outlined.Share, "分享", modifier = Modifier.size(20.dp))
                                }
                            }
                            IconButton(onClick = { showGallery = false }) {
                                Icon(Icons.Default.Close, "关闭", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        pageSpacing = 16.dp
                    ) { page ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            ZoomableImage(
                                model = galleryImages[page],
                                contentDescription = galleryImages[page].name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(smoothCornerShape(16.dp))
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (currentFile != null) {
                        Text(
                            text = "${formatFileSize(currentFile.length())} · ${formatDate(currentFile.lastModified())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────── 子组件 ───────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    file: File,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit = {}
) {
    val isAudio = file.isFile && file.isAudioFile()
    val isThisPlaying by remember {
        derivedStateOf {
            AudioPlayerManager.playingSource.value == file.absolutePath &&
                    AudioPlayerManager.isPlaying.value
        }
    }

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        isThisPlaying -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(smoothCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 多选模式显示勾选框
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.size(44.dp)
                )
            } else if (isAudio) {
                AudioPlayButton(
                    source = file.absolutePath,
                    size = 44
                )
            } else {
                FileIcon(file, size = 44)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (file.isDirectory) FontWeight.Medium else FontWeight.Normal
                )
                Text(
                    text = buildString {
                        if (file.isDirectory) {
                            val count = file.listFiles()?.size ?: 0
                            append("$count 项")
                        } else {
                            append(formatFileSize(file.length()))
                        }
                        append(" · ")
                        append(formatDate(file.lastModified()))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            // 多选模式下隐藏更多操作按钮
            if (!isSelectionMode) {
                IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.MoreVert, "操作",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FileIcon(file: File, size: Int) {
    if (file.isDirectory) {
        Surface(
            modifier = Modifier.size(size.dp),
            shape = smoothCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Folder, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size((size * 0.5).dp)
                )
            }
        }
    } else if (file.isImageFile()) {
        AsyncImage(
            model = file,
            contentDescription = null,
            modifier = Modifier
                .size(size.dp)
                .clip(smoothCornerShape(12.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        Surface(
            modifier = Modifier.size(size.dp),
            shape = smoothCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    getFileIcon(file), null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size((size * 0.5).dp)
                )
            }
        }
    }
}

@Composable
private fun FileActionItem(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
        }
    }
}

// ─────────────────────── 工具函数 ───────────────────────

/**
 * 强制删除文件/文件夹：先尝试 Java API，失败后尝试 shell rm 命令。
 * 公共 Downloads 目录在 Android 11+ 可能无法通过 Java File API 删除。
 */
private fun forceDelete(file: File): Boolean {
    // 方式1：Java API
    val javaSuccess = try {
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    } catch (_: Exception) {
        false
    }
    if (javaSuccess && !file.exists()) return true

    // 方式2：shell rm 命令（回退方案）
    return try {
        val cmd = if (file.isDirectory) "rm -rf" else "rm -f"
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "$cmd '${file.absolutePath}'"))
        val exitCode = process.waitFor()
        exitCode == 0 && !file.exists()
    } catch (_: Exception) {
        false
    }
}

private fun File.isImageFile(): Boolean {
    val ext = extension.lowercase()
    return ext in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
}

private fun File.isAudioFile(): Boolean {
    val ext = extension.lowercase()
    return ext in listOf("ogg", "mp3", "wav", "flac", "m4a", "aac")
}

private fun File.isVideoFile(): Boolean {
    val ext = extension.lowercase()
    return ext in listOf("mp4", "mkv", "avi", "mov", "webm")
}

private fun getFileIcon(file: File): ImageVector {
    return when {
        file.isDirectory -> Icons.Default.Folder
        file.isAudioFile() -> Icons.Outlined.AudioFile
        file.isVideoFile() -> Icons.Outlined.VideoFile
        file.isImageFile() -> Icons.Outlined.Image
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    val idx = digitGroups.coerceIn(0, units.size - 1)
    return DecimalFormat("#,##0.#").format(bytes / 1024.0.pow(idx.toDouble())) + " " + units[idx]
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return ""
    return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun getMimeType(file: File): String {
    val ext = MimeTypeMap.getFileExtensionFromUrl(file.name)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}

private fun getFileUri(context: Context, file: File): Uri {
    return try {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    } catch (_: Exception) {
        Uri.fromFile(file)
    }
}

private fun openFile(context: Context, file: File, onError: (String) -> Unit) {
    try {
        val uri = getFileUri(context, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, getMimeType(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        onError("无法打开该文件")
    }
}

private fun shareFile(context: Context, file: File, onError: (String) -> Unit) {
    try {
        val uri = getFileUri(context, file)
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(file)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            null
        )
        context.startActivity(intent)
    } catch (_: Exception) {
        onError("分享失败")
    }
}

private fun shareFiles(context: Context, fileList: List<File>, onError: (String) -> Unit) {
    if (fileList.isEmpty()) return
    if (fileList.size == 1) {
        shareFile(context, fileList.first(), onError)
        return
    }
    try {
        val uris = ArrayList<Uri>()
        for (f in fileList) {
            uris.add(getFileUri(context, f))
        }
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            null
        )
        context.startActivity(intent)
    } catch (_: Exception) {
        onError("分享失败")
    }
}
