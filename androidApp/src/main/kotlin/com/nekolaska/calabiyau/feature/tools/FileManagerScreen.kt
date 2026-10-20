package com.nekolaska.calabiyau.feature.tools

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.media.AudioPlayButton
import com.nekolaska.calabiyau.core.media.AudioPlayerManager
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.AppTextColors
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.core.ui.ZoomableImage
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FileManagerDirectoryPickerConfig(
    val title: String,
    val description: String,
    val actionLabel: String = "选择当前目录",
    val pickMode: FileManagerPickerMode = FileManagerPickerMode.DIRECTORY,
    val allowMultiSelect: Boolean = true,
    val systemPickerLabel: String? = null,
    val onOpenSystemPicker: (() -> Unit)? = null
)

private data class ApkFileInfo(
    val versionName: String?,
    val versionCode: Long?
)

enum class FileManagerPickerMode {
    DIRECTORY,
    FILES
}

/**
 * 内置文件管理器，支持：
 * - 浏览保存目录下的文件和子文件夹
 * - 预览图片 / 打开文件
 * - 重命名、删除、分享
 * - 返回上级目录
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    rootPath: String,
    onBack: () -> Unit,
    initialPath: String? = null,
    directoryPickerConfig: FileManagerDirectoryPickerConfig? = null,
    onDirectoryPicked: ((File) -> Unit)? = null,
    onFilesPicked: ((List<File>) -> Unit)? = null
) {
    val context = LocalContext.current
    val showSnack = rememberSnackbarLauncher()
    val rootDir = remember(rootPath) { File(rootPath).absoluteFile.normalize() }
    var currentDir by remember(rootPath, initialPath) {
        mutableStateOf(
            initialPath
                ?.let { File(it).absoluteFile.normalize() }
                ?.takeIf { it.isDirectory && runCatching { it.isWithinManagedRoot(rootDir) }.getOrDefault(false) }
                ?: rootDir
        )
    }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var entries by remember { mutableStateOf<List<ManagedFileEntry>>(emptyList()) }
    var metadataByPath by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loadedDirectory by remember { mutableStateOf<File?>(null) }
    var isMutating by remember { mutableStateOf(false) }
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
    LaunchedEffect(currentDir, directoryPickerConfig?.pickMode, directoryPickerConfig?.allowMultiSelect) {
        isSelectionMode = false
        selectedFiles = emptySet()
        selectedFile = null
        showRenameDialog = false; showDeleteDialog = false; showBatchDeleteDialog = false
        showGallery = false
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshCounter++ }

    // 加载当前目录文件列表
    LaunchedEffect(currentDir, refreshCounter) {
        isLoading = true
        loadError = null
        try {
            val directory = currentDir
            val snapshot = withContext(Dispatchers.IO) {
                val rows = readManagedDirectory(rootDir, directory)
                val apks = rows.filter { !it.directory && it.file.isApkFile() }
                    .mapNotNull { row -> readApkFileInfo(context, row.file)?.let { row.file.absolutePath to it } }.toMap()
                rows to rows.associate { it.file.absolutePath to fileMetaText(it, apks[it.file.absolutePath]) }
            }
            entries = snapshot.first
            files = entries.map { it.file }
            metadataByPath = snapshot.second
            loadedDirectory = directory
            selectedFiles = selectedFiles.intersect(files.map { it.absolutePath }.toSet())
            if (selectedFiles.isEmpty()) isSelectionMode = false
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            files = emptyList(); entries = emptyList(); metadataByPath = emptyMap()
            selectedFiles = emptySet(); isSelectionMode = false
            loadError = e.message ?: "读取目录失败，请检查存储权限"
        } finally {
            isLoading = false
        }
    }

    val isPickerMode = directoryPickerConfig != null && (onDirectoryPicked != null || onFilesPicked != null)
    val pickerConfig = if (isPickerMode) directoryPickerConfig else null
    val pickerMode = pickerConfig?.pickMode ?: FileManagerPickerMode.DIRECTORY
    val pickDirectory = if (pickerMode == FileManagerPickerMode.DIRECTORY) onDirectoryPicked else null
    val pickFiles = if (pickerMode == FileManagerPickerMode.FILES) onFilesPicked else null
    val canPickFiles = pickerMode == FileManagerPickerMode.FILES && pickFiles != null
    val eligibleFiles = selectableManagedFiles(entries, isPickerMode)
    val fileOnlyPaths = remember(entries) { entries.filter { !it.directory }.map { it.file.absolutePath }.toSet() }

    fun submitPickedFiles(selection: List<File>) {
        if (isLoading || isMutating || loadError != null || loadedDirectory != currentDir) return
        isMutating = true
        scope.launch {
            try {
                val checked = withContext(Dispatchers.IO) {
                    validateManagedFileSelection(rootDir, selection, pickerConfig?.allowMultiSelect == true)
                }
                pickFiles?.invoke(checked)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showSnack(e.message ?: "所选文件不可用"); refreshCounter++ }
            finally { isMutating = false }
        }
    }

    fun clearSelection() {
        isSelectionMode = false
        selectedFiles = emptySet()
    }

    fun toggleSelectedPath(path: String, isSelected: Boolean) {
        selectedFiles = if (isSelected) selectedFiles - path else selectedFiles + path
    }

    fun enterSelectionWith(path: String) {
        isSelectionMode = true
        selectedFiles = setOf(path)
    }

    fun toggleAllSelection() {
        selectedFiles = if (selectedFiles.size == eligibleFiles.size && eligibleFiles.isNotEmpty()) {
            emptySet()
        } else {
            eligibleFiles.map { it.absolutePath }.toSet()
        }
    }

    // 返回键处理：多选模式下先退出多选，子目录时返回上级，在根目录时退出
    val isAtRoot = currentDir.isAtSamePath(rootDir)
    BackHandler(enabled = isSelectionMode) {
        clearSelection()
    }
    BackHandler(enabled = !isAtRoot && !isSelectionMode) {
        currentDir = currentDir.parentFile ?: currentDir
    }
    BackHandler(enabled = isAtRoot && !isSelectionMode) { onBack() }

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
                                .padding(horizontal = AppSpacing.medium, vertical = AppSpacing.xSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                clearSelection()
                            }) {
                                Icon(Icons.Default.Close, "退出多选")
                            }
                            Text(
                                text = "已选 ${selectedFiles.size} 项",
                                style = AppTextStyles.topBarTitle,
                                modifier = Modifier.weight(1f)
                            )
                            // 全选 / 取消全选
                            val allSelected = selectedFiles.size == eligibleFiles.size && eligibleFiles.isNotEmpty()
                            IconButton(onClick = {
                                toggleAllSelection()
                            }) {
                                Icon(
                                    if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                    if (allSelected) "取消全选" else "全选"
                                )
                            }
                            if (!isPickerMode) {
                                val shareableFiles = entries
                                    .filter { !it.directory && it.file.absolutePath in selectedFiles }.map { it.file }
                                IconButton(
                                    onClick = { shareFiles(context, shareableFiles, showSnack) },
                                    enabled = shareableFiles.isNotEmpty() && !isMutating
                                ) {
                                    Icon(Icons.Outlined.Share, "批量分享")
                                }
                                IconButton(
                                    onClick = { showBatchDeleteDialog = true },
                                    enabled = selectedFiles.isNotEmpty() && !isMutating
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete, "批量删除",
                                        tint = if (selectedFiles.isNotEmpty()) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                }
                            }
                        }
                    } else {
                        // ── 普通模式顶栏 ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = AppSpacing.medium, vertical = AppSpacing.xSmall),
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
                                    text = when {
                                        pickerConfig != null && isAtRoot -> pickerConfig.title
                                        isAtRoot -> "文件管理"
                                        else -> currentDir.name
                                    },
                                    style = AppTextStyles.topBarTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = (when {
                                        loadError != null -> "读取失败"
                                        isLoading || loadedDirectory != currentDir -> "读取中…"
                                        else -> "${files.size} 项"
                                    }) + if (!isAtRoot) " · ${
                                        currentDir.relativePathWithin(rootDir).orEmpty()
                                    }" else "",
                                    style = AppTextStyles.topBarSubtitle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { refreshCounter++ }, enabled = !isLoading && !isMutating) {
                                Icon(Icons.Default.Refresh, "刷新文件列表")
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isMutating) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (isPickerMode) {
                DirectoryPickerBanner(
                    config = pickerConfig!!,
                    currentDir = currentDir,
                    selectedFiles = selectedFileObjects.filter { it.absolutePath in fileOnlyPaths },
                    enabled = !isLoading && !isMutating && loadError == null && loadedDirectory == currentDir,
                    onPickCurrentDirectory = {
                        if (!isLoading && !isMutating && loadError == null && loadedDirectory == currentDir) {
                            val pickedDirectory = currentDir
                            isMutating = true
                            scope.launch {
                                try {
                                    val directory = withContext(Dispatchers.IO) {
                                        require(pickedDirectory.isDirectory && pickedDirectory.isWithinManagedRoot(rootDir)) { "当前目录不可用" }
                                        pickedDirectory
                                    }
                                    pickDirectory?.invoke(directory)
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { showSnack(e.message ?: "目录不可用"); refreshCounter++ }
                                finally { isMutating = false }
                            }
                        }
                    },
                    onPickSelectedFiles = { submitPickedFiles(selectedFileObjects) }
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading || loadedDirectory != currentDir && loadError == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                loadError != null -> {
                    Column(Modifier.fillMaxSize().padding(AppSpacing.screen),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text(loadError!!, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { refreshCounter++ }) { Text("重试读取") }
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
                            Spacer(Modifier.height(AppSpacing.sectionGap))
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
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)
                    ) {
                        items(entries, key = { it.file.absolutePath }) { entry ->
                            val file = entry.file
                            val isSelected = file.absolutePath in selectedFiles
                            FileListItem(
                                file = file,
                                directory = entry.directory,
                                metadata = metadataByPath[file.absolutePath].orEmpty(),
                                showMore = !isPickerMode && !isMutating,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isMutating || loadedDirectory != currentDir) return@FileListItem
                                    if (isSelectionMode) {
                                        // 多选模式下：点击切换选中
                                        if (isPickerMode && entry.directory) showSnack("请选择文件，不能选择文件夹")
                                        else toggleSelectedPath(file.absolutePath, isSelected)
                                    } else {
                                        if (entry.directory) {
                                            currentDir = file
                                        } else if (pickerMode == FileManagerPickerMode.DIRECTORY && isPickerMode) {
                                            showSnack("当前模式仅支持选择目录")
                                        } else if (canPickFiles) {
                                            if (pickerConfig?.allowMultiSelect == true) {
                                                toggleSelectedPath(file.absolutePath, isSelected)
                                                isSelectionMode = selectedFiles.isNotEmpty()
                                            } else {
                                                submitPickedFiles(listOf(file))
                                            }
                                        } else if (file.isImageFile()) {
                                            val images = managedGalleryFiles(entries)
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
                                    if (isMutating || loadedDirectory != currentDir) return@FileListItem
                                    if (isPickerMode && entry.directory) {
                                        if (!isSelectionMode) currentDir = file
                                    } else if (canPickFiles) {
                                        if (pickerConfig?.allowMultiSelect == true) {
                                            isSelectionMode = true
                                            toggleSelectedPath(file.absolutePath, isSelected)
                                        } else {
                                            submitPickedFiles(listOf(file))
                                        }
                                    } else if (isPickerMode) {
                                        showSnack("当前模式仅支持选择目录")
                                    } else if (!isSelectionMode) {
                                        // 长按进入多选模式并选中当前项
                                        enterSelectionWith(file.absolutePath)
                                    } else {
                                        // 已在多选模式：切换选中
                                        toggleSelectedPath(file.absolutePath, isSelected)
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
    }

    // ── 文件操作菜单 BottomSheet ──
    if (selectedFile != null) {
        val file = selectedFile!!
        val directory = entries.firstOrNull { it.file == file }?.directory ?: false
        ModalBottomSheet(
            onDismissRequest = { selectedFile = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = AppShapes.sheet
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.screen, vertical = AppSpacing.medium)
                    .padding(bottom = AppSpacing.xxxLarge)
            ) {
                // 文件信息头
                Row(
                    modifier = Modifier.padding(horizontal = AppSpacing.medium, vertical = AppSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FileIcon(file, size = 48, directory = directory)
                    Spacer(Modifier.width(AppSpacing.sectionGap))
                    Column(Modifier.weight(1f)) {
                        Text(
                            file.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            metadataByPath[file.absolutePath].orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(AppSpacing.itemGap))
                HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.medium))
                Spacer(Modifier.height(AppSpacing.itemGap))

                // 打开
                if (!directory) {
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
                if (!directory && file.isImageFile()) {
                    FileActionItem(
                        icon = Icons.Outlined.Image,
                        label = "预览",
                        onClick = {
                            val images = managedGalleryFiles(entries)
                            galleryImages = images
                            galleryInitialIndex = images.indexOf(file).coerceAtLeast(0)
                            showGallery = true
                            selectedFile = null
                        }
                    )
                }

                // 播放（仅音频）
                if (!directory && file.isAudioFile()) {
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
                if (!directory) {
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
        var newName by remember(file.absolutePath) { mutableStateOf(file.name) }
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
            shape = AppShapes.dialog,
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        if (!isMutating) {
                            val requestedName = newName
                            isMutating = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { renameManagedFile(rootDir, file, requestedName) }
                                    showSnack("已重命名")
                                    selectedFile = null; showRenameDialog = false
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { showSnack(e.message ?: "重命名失败") }
                                finally { isMutating = false; refreshCounter++ }
                            }
                        }
                    },
                    enabled = validManagedFileName(newName) && newName != file.name && !isMutating
                ) { Text(if (isMutating) "处理中…" else "确定") }
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
                    if (entries.any { it.file == file && it.directory }) "确定要删除文件夹「${file.name}」及其所有内容吗？"
                    else "确定要删除「${file.name}」吗？"
                )
            },
            shape = AppShapes.dialog,
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        if (isMutating) return@FilledTonalButton
                        isMutating = true
                        showDeleteDialog = false
                        selectedFile = null
                        scope.launch {
                            try {
                                val success = withContext(Dispatchers.IO) { deleteManagedFile(rootDir, file, ::isAndroidFileLink) }
                                showSnack(if (success) "已删除" else "部分或全部删除失败，请检查存储权限")
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { showSnack(e.message ?: "删除失败") }
                            finally { isMutating = false; refreshCounter++ }
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
        val folderCount = entries.count { it.directory && it.file.absolutePath in selectedFiles }
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
            shape = AppShapes.dialog,
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        if (isMutating) return@FilledTonalButton
                        isMutating = true
                        showBatchDeleteDialog = false
                        // Selections are restricted to this directory, never ancestors and descendants together.
                        val toDelete = selectedFileObjects.toList()
                        isSelectionMode = false
                        selectedFiles = emptySet()
                        scope.launch {
                            var successCount = 0
                            var failCount = 0
                            try {
                                withContext(Dispatchers.IO) {
                                    toDelete.forEach { file ->
                                        val deleted = try { deleteManagedFile(rootDir, file, ::isAndroidFileLink) }
                                            catch (e: CancellationException) { throw e }
                                            catch (_: Exception) { false }
                                        if (deleted) successCount++ else failCount++
                                    }
                                }
                                val msg = buildString {
                                    append("已删除 $successCount 项")
                                    if (failCount > 0) append("，$failCount 项失败")
                                }
                                showSnack(msg)
                            } finally { isMutating = false; refreshCounter++ }
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

        Dialog(
            onDismissRequest = { showGallery = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppSpacing.dialogMargin),
                shape = AppShapes.dialog,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(Modifier.padding(AppSpacing.screen)) {
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
                    Spacer(Modifier.height(AppSpacing.itemGap))
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
                                loadOriginalSize = true,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(smoothCornerShape(16.dp))
                            )
                        }
                    }
                    Spacer(Modifier.height(AppSpacing.itemGap))
                    if (currentFile != null) {
                        Text(
                            text = metadataByPath[currentFile.absolutePath].orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryPickerBanner(
    config: FileManagerDirectoryPickerConfig,
    currentDir: File,
    selectedFiles: List<File>,
    enabled: Boolean,
    onPickCurrentDirectory: () -> Unit,
    onPickSelectedFiles: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xSmall, vertical = AppSpacing.small),
        shape = AppShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(config.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                config.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = smoothCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    currentDir.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.large, vertical = AppSpacing.medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (config.pickMode == FileManagerPickerMode.FILES && config.allowMultiSelect) {
                Text(
                    text = if (selectedFiles.isEmpty()) "尚未选择文件" else "已选择 ${selectedFiles.size} 个文件",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when (config.pickMode) {
                FileManagerPickerMode.DIRECTORY -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)
                    ) {
                        FilledTonalButton(
                            onClick = onPickCurrentDirectory,
                            enabled = enabled,
                            shape = AppShapes.capsule,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(config.actionLabel)
                        }
                        config.systemPickerLabel?.let { label ->
                            OutlinedButton(
                                onClick = { config.onOpenSystemPicker?.invoke() },
                                enabled = config.onOpenSystemPicker != null,
                                shape = AppShapes.capsule,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
                FileManagerPickerMode.FILES -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)
                    ) {
                        FilledTonalButton(
                            onClick = onPickSelectedFiles,
                            enabled = enabled && selectedFiles.isNotEmpty() && (config.allowMultiSelect || selectedFiles.size == 1),
                            shape = AppShapes.capsule,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (config.allowMultiSelect) {
                                    "确认所选（${selectedFiles.size}）"
                                } else {
                                    config.actionLabel
                                }
                            )
                        }
                        config.systemPickerLabel?.let { label ->
                            OutlinedButton(
                                onClick = { config.onOpenSystemPicker?.invoke() },
                                enabled = config.onOpenSystemPicker != null,
                                shape = AppShapes.capsule,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────── 子组件 ───────────────────────

@Composable
private fun FileListItem(
    file: File,
    directory: Boolean,
    metadata: String,
    showMore: Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit = {}
) {
    val isAudio = !directory && file.isAudioFile()
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
            modifier = Modifier.padding(horizontal = AppSpacing.large, vertical = 10.dp),
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
                FileIcon(file, size = 44, directory = directory)
            }
            Spacer(Modifier.width(AppSpacing.iconGap))
            Column(Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = AppTextStyles.listItemTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (directory) FontWeight.Medium else FontWeight.Normal
                )
                Text(
                    text = metadata,
                    style = AppTextStyles.listItemMeta,
                    color = AppTextColors.muted,
                    maxLines = 1
                )
            }
            // 多选模式下隐藏更多操作按钮
            if (!isSelectionMode && showMore) {
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
private fun FileIcon(file: File, size: Int, directory: Boolean) {
    if (directory) {
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
            contentScale = ContentScale.Crop
        )
    } else if (file.isApkFile()) {
        Surface(
            modifier = Modifier.size(size.dp),
            shape = smoothCornerShape(12.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.InstallMobile, null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size((size * 0.5).dp)
                )
            }
        }
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
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.screen, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(AppSpacing.sectionGap))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
        }
    }
}

// ─────────────────────── 工具函数 ───────────────────────

private fun File.normalizePath(): String = absolutePath.replace('\\', '/')

private fun File.isAtSamePath(rootDir: File): Boolean =
    absoluteFile.normalizePath() == rootDir.absoluteFile.normalizePath()

private fun File.relativePathWithin(rootDir: File): String? {
    val rootPath = rootDir.absoluteFile.normalizePath().trimEnd('/')
    val candidatePath = absoluteFile.normalizePath().trimEnd('/')
    // Display only. All access/mutations use canonical checks in FileManagerFileSystem on IO.
    if (candidatePath != rootPath && !candidatePath.startsWith("$rootPath/")) return null
    return candidatePath
        .removePrefix(rootPath)
        .trimStart('/')
        .takeIf { it.isNotBlank() }
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

private fun File.isApkFile(): Boolean = extension.equals("apk", ignoreCase = true)

private fun fileMetaText(entry: ManagedFileEntry, apkInfo: ApkFileInfo? = null): String {
    val file = entry.file
    return buildString {
        if (entry.directory) {
            append(entry.childCount?.let { "$it 项" } ?: "无法读取")
        } else {
            append(formatFileSize(entry.size))
            if (file.isApkFile()) {
                append(" · 安装包")
                apkInfo?.versionName?.takeIf { it.isNotBlank() }?.let { versionName ->
                    append(" v")
                    append(versionName.removePrefix("v").removePrefix("V"))
                }
                apkInfo?.versionCode?.let { versionCode ->
                    append(" ($versionCode)")
                }
            }
        }
        append(" · ")
        append(formatDateTime(entry.modified))
    }
}

@Suppress("DEPRECATION")
private fun readApkFileInfo(context: Context, file: File): ApkFileInfo? {
    return runCatching {
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        ApkFileInfo(info.versionName, versionCode)
    }.getOrNull()
}

private fun getFileIcon(file: File): ImageVector {
    return when {
        file.isApkFile() -> Icons.Outlined.InstallMobile
        file.isAudioFile() -> Icons.Outlined.AudioFile
        file.isVideoFile() -> Icons.Outlined.VideoFile
        file.isImageFile() -> Icons.Outlined.Image
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

