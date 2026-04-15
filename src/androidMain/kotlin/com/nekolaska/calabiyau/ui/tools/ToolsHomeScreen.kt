package com.nekolaska.calabiyau.ui.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.data.AppPrefs
import com.nekolaska.calabiyau.ui.shared.rememberSnackbarLauncher
import com.nekolaska.calabiyau.ui.shared.smoothCapsuleShape
import com.nekolaska.calabiyau.ui.shared.smoothCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap

private enum class ToolsSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    IMAGE("图片工具", "压缩、转换、九宫格与裁切入口", Icons.Outlined.Image),
    TEXT("文本工具", "清洗文本、查找替换、文件名净化", Icons.Outlined.TextFields),
    AUDIO("音频工具", "查看音频信息与轻量整理", Icons.Outlined.AudioFile),
    ORGANIZE("文件整理", "分类整理、批量重命名、摘要扫描", Icons.Outlined.FolderOpen),
    SUBMISSION("投稿辅助", "体积检查、命名校验、清单导出", Icons.AutoMirrored.Outlined.FactCheck)
}

private enum class ExportFormat(val label: String, val extension: String) {
    JPG("JPG", "jpg"),
    PNG("PNG", "png"),
    WEBP("WEBP", "webp")
}

private enum class ChecklistFormat(val label: String, val extension: String) {
    TXT("TXT", "txt"),
    MARKDOWN("Markdown", "md")
}

private data class ToolOutput(
    val title: String,
    val message: String,
    val files: List<File> = emptyList(),
    val directory: File? = null
)

private data class PickedInput(
    val file: File? = null,
    val uri: Uri? = null
)

private fun List<String>.toPickedFileInputs(): List<PickedInput> = map { path ->
    PickedInput(file = File(path))
}

private fun List<Uri>.toPickedUriInputs(): List<PickedInput> = map { uri ->
    PickedInput(uri = uri)
}

private enum class ImagePreviewMode {
    COMPRESS,
    NINE_GRID,
    CROP
}

private enum class StitchDirection(val label: String) {
    HORIZONTAL("横向"),
    VERTICAL("纵向")
}

private val CROP_PRESETS = listOf(
    "1:1" to (1 to 1),
    "1:2" to (1 to 2),
    "16:9" to (16 to 9),
    "4:3" to (4 to 3)
)
private val CropPreviewNestedScrollBlocker = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = available

    override suspend fun onPreFling(available: Velocity): Velocity = available
}

private data class ImagePreviewSheetState(
    val mode: ImagePreviewMode,
    val items: List<PickedInput>,
    val initialIndex: Int = 0,
    val cropPreset: String = "1:1",
    val cropPreviewStates: MutableMap<Int, CropPreviewState> = mutableStateMapOf()
)

private data class CropPreviewState(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
    val viewportSize: IntSize = IntSize.Zero,
    val imageSize: IntSize = IntSize.Zero
)

private data class CompressPreviewData(
    val previewBytes: ByteArray,
    val originalSizeBytes: Long?,
    val compressedSizeBytes: Int
)

private data class AudioInfoItem(
    val name: String,
    val durationMs: Long,
    val mimeType: String,
    val artist: String?,
    val sampleRate: String?,
    val bitrate: String?,
    val size: Long
)

private data class DirectorySummary(
    val totalFiles: Int,
    val totalDirectories: Int,
    val totalSize: Long,
    val extensionCounts: List<Pair<String, Int>>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsHomeScreen(
    onBack: () -> Unit,
    onOpenFileManager: (String?) -> Unit,
    onPickDirectoryFromFileManager: (
        initialPath: String?,
        title: String,
        description: String,
        onPicked: (String) -> Unit
    ) -> Unit,
    onPickFilesFromFileManager: (
        initialPath: String?,
        title: String,
        description: String,
        allowMultiSelect: Boolean,
        onOpenSystemPicker: () -> Unit,
        onPicked: (List<String>) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val showSnack = rememberSnackbarLauncher()
    val scope = rememberCoroutineScope()
    var currentSection by rememberSaveable { mutableStateOf<ToolsSection?>(null) }
    var outputPath by rememberSaveable { mutableStateOf(AppPrefs.toolsOutputPath) }
    var latestOutput by remember { mutableStateOf<ToolOutput?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    val activeSection = currentSection

    val outputDirPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = getPathFromUri(it)
            if (path != null) {
                outputPath = path
                AppPrefs.toolsOutputPath = path
                showSnack("已更新默认输出目录")
            } else {
                showSnack("无法解析所选目录")
            }
        }
    }

    BackHandler(enabled = currentSection != null) {
        currentSection = null
    }

    val openInFileManagerIfAvailable: (String?, String) -> Unit = remember(onOpenFileManager, showSnack) {
        { path, errorMessage ->
            if (path != null && path.startsWith(File(AppPrefs.savePath).absolutePath)) {
                onOpenFileManager(path)
            } else {
                showSnack(errorMessage)
            }
        }
    }

    Scaffold(
        topBar = {
            ToolsTopBar(
                sectionTitle = activeSection?.title,
                onBack = onBack,
                onBackToHome = { currentSection = null },
                onOpenOutputDirectory = if (activeSection != null) {
                    {
                        openInFileManagerIfAvailable(
                            resolveSectionOutputDirectory(outputPath, activeSection).absolutePath,
                            "仅支持在应用保存目录内跳转文件管理"
                        )
                    }
                } else {
                    null
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentSection,
            transitionSpec = {
                if (targetState != null) {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 8 } + scaleIn(tween(220), initialScale = 0.98f))
                        .togetherWith(fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { -it / 10 } + scaleOut(tween(180), targetScale = 0.98f))
                } else {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { -it / 8 } + scaleIn(tween(220), initialScale = 0.98f))
                        .togetherWith(fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 10 } + scaleOut(tween(180), targetScale = 0.98f))
                }
            },
            label = "ToolsSectionTransition"
        ) { sectionState ->
            if (sectionState == null) {
                ToolsHomeContent(
                    innerPadding = innerPadding,
                    outputPath = outputPath,
                    latestOutput = latestOutput,
                    onPickDirectory = { outputDirPicker.launch(null) },
                    onPickDirectoryInFileManager = {
                        onPickDirectoryFromFileManager(
                            outputPath,
                            "为素材工具选择输出目录",
                            "进入文件管理的目录选择模式后，浏览到目标目录并直接确认。"
                        ) { pickedPath ->
                            outputPath = pickedPath
                            AppPrefs.toolsOutputPath = pickedPath
                            showSnack("已通过文件管理更新输出目录")
                        }
                    },
                    onOpenDirectory = {
                        openDirectory(context, resolveOutputDirectory(outputPath), showSnack)
                    },
                    onOpenInFileManager = {
                        openInFileManagerIfAvailable(
                            File(outputPath).absolutePath,
                            "仅支持在应用保存目录内跳转文件管理"
                        )
                    },
                    onSelectSection = { currentSection = it },
                    onOpenResultDirectory = { output ->
                        output.directory?.let { openDirectory(context, it, showSnack) }
                    },
                    onOpenResultInFileManager = { output ->
                        openInFileManagerIfAvailable(
                            output.directory?.absolutePath ?: output.files.firstOrNull()?.parent,
                            "结果不在文件管理根目录内"
                        )
                    },
                    onShareResult = { output ->
                        when {
                            output.files.isNotEmpty() -> shareFiles(context, output.files, showSnack)
                            output.directory != null -> openDirectory(context, output.directory, showSnack)
                            else -> Unit
                        }
                    }
                )
            } else {
                ToolsSectionContent(
                    section = sectionState,
                    innerPadding = innerPadding,
                    outputPath = outputPath,
                    isBusy = isBusy,
                    onBusyChange = { isBusy = it },
                    onResult = { latestOutput = it },
                    onPickDirectoryFromFileManager = onPickDirectoryFromFileManager,
                    onPickFilesFromFileManager = onPickFilesFromFileManager
                )
            }
        }
    }

    LaunchedEffect(outputPath) {
        scope.launch(Dispatchers.IO) {
            resolveOutputDirectory(outputPath)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolsTopBar(
    sectionTitle: String?,
    onBack: () -> Unit,
    onBackToHome: () -> Unit,
    onOpenOutputDirectory: (() -> Unit)?
) {
    if (sectionTitle == null) {
        LargeTopAppBar(
            title = { Text("素材工具") },
            navigationIcon = {
                TextButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("返回")
                }
            }
        )
    } else {
        TopAppBar(
            title = { Text(sectionTitle) },
            navigationIcon = {
                TextButton(onClick = onBackToHome) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("返回")
                }
            },
            actions = {
                if (onOpenOutputDirectory != null) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text("查看输出目录") } },
                        state = rememberTooltipState()
                    ) {
                        FilledIconButton(onClick = onOpenOutputDirectory) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = "查看输出目录")
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ToolsHomeContent(
    innerPadding: PaddingValues,
    outputPath: String,
    latestOutput: ToolOutput?,
    onPickDirectory: () -> Unit,
    onPickDirectoryInFileManager: () -> Unit,
    onOpenDirectory: () -> Unit,
    onOpenInFileManager: () -> Unit,
    onSelectSection: (ToolsSection) -> Unit,
    onOpenResultDirectory: (ToolOutput) -> Unit,
    onOpenResultInFileManager: (ToolOutput) -> Unit,
    onShareResult: (ToolOutput) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ToolsOutputDirectoryCard(
                outputPath = outputPath,
                onPickDirectory = onPickDirectory,
                onPickDirectoryInFileManager = onPickDirectoryInFileManager,
                onOpenDirectory = onOpenDirectory,
                onOpenInFileManager = onOpenInFileManager
            )
        }

        item {
            Text(
                text = "本地素材工具箱",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        }

        items(ToolsSection.entries) { section ->
            ToolsSectionCard(section = section, onClick = { onSelectSection(section) })
        }

        latestOutput?.let { output ->
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f)
                ) {
                    ToolResultCard(
                        output = output,
                        onOpenDirectory = { onOpenResultDirectory(output) },
                        onOpenInFileManager = { onOpenResultInFileManager(output) },
                        onShare = { onShareResult(output) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolsSectionContent(
    section: ToolsSection,
    innerPadding: PaddingValues,
    outputPath: String,
    isBusy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onResult: (ToolOutput) -> Unit,
    onPickDirectoryFromFileManager: (
        initialPath: String?,
        title: String,
        description: String,
        onPicked: (String) -> Unit
    ) -> Unit,
    onPickFilesFromFileManager: (
        initialPath: String?,
        title: String,
        description: String,
        allowMultiSelect: Boolean,
        onOpenSystemPicker: () -> Unit,
        onPicked: (List<String>) -> Unit
    ) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        when (section) {
            ToolsSection.IMAGE -> ImageToolsPage(
                outputPath = outputPath,
                isBusy = isBusy,
                onBusyChange = onBusyChange,
                onResult = onResult,
                onPickFilesFromFileManager = onPickFilesFromFileManager
            )
            ToolsSection.TEXT -> TextToolsPage(
                outputPath = outputPath,
                isBusy = isBusy,
                onBusyChange = onBusyChange,
                onResult = onResult,
                onPickDirectoryFromFileManager = onPickDirectoryFromFileManager,
                onPickFilesFromFileManager = onPickFilesFromFileManager
            )
            ToolsSection.AUDIO -> AudioToolsPage(
                outputPath = outputPath,
                isBusy = isBusy,
                onBusyChange = onBusyChange,
                onResult = onResult,
                onPickDirectoryFromFileManager = onPickDirectoryFromFileManager,
                onPickFilesFromFileManager = onPickFilesFromFileManager
            )
            ToolsSection.ORGANIZE -> OrganizeToolsPage(
                isBusy = isBusy,
                onBusyChange = onBusyChange,
                onResult = onResult,
                onPickDirectoryFromFileManager = onPickDirectoryFromFileManager
            )
            ToolsSection.SUBMISSION -> SubmissionToolsPage(
                outputPath = outputPath,
                isBusy = isBusy,
                onBusyChange = onBusyChange,
                onResult = onResult,
                onPickDirectoryFromFileManager = onPickDirectoryFromFileManager
            )
        }

        if (isBusy) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = smoothCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("处理中…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ToolsOutputDirectoryCard(
    outputPath: String,
    onPickDirectory: () -> Unit,
    onPickDirectoryInFileManager: () -> Unit,
    onOpenDirectory: () -> Unit,
    onOpenInFileManager: () -> Unit
) {
    Card(
        shape = smoothCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = smoothCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Outlined.SaveAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("默认输出目录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        outputPath,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "设置保存位置",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickDirectory,
                        shape = smoothCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("系统选择")
                    }
                    OutlinedButton(
                        onClick = onPickDirectoryInFileManager,
                        shape = smoothCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("文件管理")
                    }
                }

                Text(
                    text = "查看输出目录",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenDirectory,
                        shape = smoothCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("其他应用打开")
                    }
                    FilledTonalButton(
                        onClick = onOpenInFileManager,
                        shape = smoothCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("文件管理")
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsSectionCard(section: ToolsSection, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = smoothCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = smoothCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    section.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    section.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Outlined.BuildCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ToolPageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = smoothCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = smoothCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun DirectorySelectionActions(
    onPickInFileManager: () -> Unit,
    onPickInSystem: () -> Unit,
    label: String = "选择目录"
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onPickInFileManager, shape = smoothCornerShape(24.dp)) {
            Text(label)
        }
        FilledTonalButton(onClick = onPickInSystem, shape = smoothCornerShape(24.dp)) {
            Text("系统选择器")
        }
    }
}

@Composable
private fun ImagePreviewStrip(
    items: List<PickedInput>,
    onPreviewClick: (Int) -> Unit
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "预览 ${items.size} 张",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.take(6).forEachIndexed { index, item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    AsyncImage(
                        model = item.file ?: item.uri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(88.dp)
                            .clickable { onPreviewClick(index) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImagePreviewBottomSheet(
    state: ImagePreviewSheetState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    compressQuality: Float = 0.82f,
    onCompressQualityChange: (Float) -> Unit = {},
    compressFormat: ExportFormat = ExportFormat.JPG,
    onCompressFormatChange: (ExportFormat) -> Unit = {}
) {
    val previewHeight = 320.dp
    var currentIndex by remember(state) { mutableIntStateOf(state.initialIndex.coerceIn(0, state.items.lastIndex)) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentItem = state.items.getOrNull(currentIndex)
    val confirmLabel = when (state.mode) {
        ImagePreviewMode.COMPRESS -> "导出压缩图片"
        ImagePreviewMode.NINE_GRID -> "确认切图"
        ImagePreviewMode.CROP -> "确认裁切"
    }
    ModalBottomSheet(
        sheetState = bottomSheetState,
        onDismissRequest = onDismiss,
        shape = smoothCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("处理前预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                currentItem?.let { item ->
                    when (state.mode) {
                        ImagePreviewMode.COMPRESS -> {
                            Surface(
                                shape = smoothCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AsyncImage(
                                    model = item.file ?: item.uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(previewHeight)
                                )
                            }
                            Text(
                                "输出质量：${(compressQuality * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = compressQuality,
                                onValueChange = onCompressQualityChange,
                                valueRange = 0.05f..1f
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ExportFormat.entries.forEach { format ->
                                    AssistChip(
                                        onClick = { onCompressFormatChange(format) },
                                        shape = smoothCapsuleShape(),
                                        label = { Text(format.label) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (format == compressFormat) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                            }
                                        )
                                    )
                                }
                            }
                            CompressRealtimePreview(
                                item = item,
                                quality = compressQuality,
                                format = compressFormat,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                            )
                        }
                        ImagePreviewMode.NINE_GRID -> {
                            OverlayImagePreview(
                                item = item,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(previewHeight),
                                overlay = { size ->
                                    NineGridOverlay(size = size)
                                }
                            )
                        }
                        ImagePreviewMode.CROP -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                InteractiveCropPreview(
                                    item = item,
                                    preset = state.cropPreset,
                                    previewState = state.cropPreviewStates[currentIndex] ?: CropPreviewState(),
                                    onPreviewStateChange = { updated ->
                                        state.cropPreviewStates[currentIndex] = updated
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(previewHeight)
                                )
                                Text(
                                    text = "双击可快速放大/还原，拖动可微调构图。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        inputDisplayName(LocalContext.current, item) ?: "未命名图片",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.items.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { currentIndex = (currentIndex - 1).coerceAtLeast(0) },
                            enabled = currentIndex > 0,
                            shape = smoothCornerShape(20.dp)
                        ) { Text("上一张") }
                        Text("${currentIndex + 1} / ${state.items.size}")
                        OutlinedButton(
                            onClick = { currentIndex = (currentIndex + 1).coerceAtMost(state.items.lastIndex) },
                            enabled = currentIndex < state.items.lastIndex,
                            shape = smoothCornerShape(20.dp)
                        ) { Text("下一张") }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                FilledTonalButton(onClick = onConfirm, shape = smoothCornerShape(24.dp)) {
                    Text(confirmLabel)
                }
            }
        }
    }
}

@Composable
private fun CompressRealtimePreview(
    item: PickedInput,
    quality: Float,
    format: ExportFormat,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewData by produceState<CompressPreviewData?>(initialValue = null, item, quality, format) {
        value = null
        delay(120)
        value = withContext(Dispatchers.IO) {
            buildCompressPreviewData(context, item, format, quality)
        }
    }

    Surface(
        shape = smoothCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        when (val data = previewData) {
            null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "压缩后预览",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val ratioText = data.originalSizeBytes?.takeIf { it > 0 }?.let {
                        "约 ${(data.compressedSizeBytes * 100 / it.toFloat()).toInt()}%"
                    } ?: ""
                    Text(
                        text = "${format.label}  ${formatFileSize(data.compressedSizeBytes.toLong())} $ratioText",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AsyncImage(
                        model = data.previewBytes,
                        contentDescription = "压缩后预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayImagePreview(
    item: PickedInput,
    modifier: Modifier = Modifier,
    overlay: @Composable (IntSize) -> Unit
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    Surface(
        shape = smoothCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { size = it }
        ) {
            AsyncImage(
                model = item.file ?: item.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            if (size != IntSize.Zero) {
                overlay(size)
            }
        }
    }
}

@Composable
private fun NineGridOverlay(size: IntSize) {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        val stroke = 1.5.dp.toPx()
        repeat(2) { index ->
            val x = width * (index + 1) / 3f
            val y = height * (index + 1) / 3f
            drawLine(Color.White.copy(alpha = 0.85f), Offset(x, 0f), Offset(x, height), strokeWidth = stroke)
            drawLine(Color.White.copy(alpha = 0.85f), Offset(0f, y), Offset(width, y), strokeWidth = stroke)
        }
    }
}

@Composable
private fun InteractiveCropPreview(
    item: PickedInput,
    preset: String,
    previewState: CropPreviewState,
    onPreviewStateChange: (CropPreviewState) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var imageSize by remember(item) { mutableStateOf(IntSize.Zero) }
    var interactionScale by remember(item, preset) { mutableFloatStateOf(previewState.scale.coerceAtLeast(1f)) }
    var interactionOffset by remember(item, preset) { mutableStateOf(previewState.offset) }

    LaunchedEffect(item) {
        imageSize = withContext(Dispatchers.IO) { readImageSize(context, item) ?: IntSize.Zero }
    }
    var viewportSize by remember(item, preset) { mutableStateOf(IntSize.Zero) }

    Surface(
        shape = smoothCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { viewportSize = it }
        ) {
            if (viewportSize != IntSize.Zero) {
                val cropRect = remember(viewportSize, preset) { buildCropOverlayRect(viewportSize, preset) }
                val baseSize = calculateBaseDisplaySize(viewportSize, imageSize)
                val minScale = calculateMinScaleToCoverCrop(baseSize, cropRect)
                val currentScale = interactionScale.coerceIn(minScale, 5f)
                fun clampOffset(offset: Offset, scale: Float): Offset = clampCropOffset(
                    offset = offset,
                    scale = scale,
                    viewportSize = viewportSize,
                    imageSize = imageSize,
                    cropRect = cropRect
                )

                fun updatePreview(scale: Float, offset: Offset) {
                    onPreviewStateChange(
                        previewState.copy(
                            scale = scale,
                            offset = offset,
                            viewportSize = viewportSize,
                            imageSize = imageSize
                        )
                    )
                }

                val clampedOffset = clampOffset(interactionOffset, currentScale)

                LaunchedEffect(viewportSize, imageSize, cropRect, currentScale, clampedOffset, minScale) {
                    interactionScale = currentScale
                    interactionOffset = clampedOffset
                    val normalized = previewState.copy(
                        scale = currentScale,
                        offset = clampedOffset,
                        viewportSize = viewportSize,
                        imageSize = imageSize
                    )
                    if (normalized != previewState) {
                        onPreviewStateChange(normalized)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(CropPreviewNestedScrollBlocker)
                        .pointerInput(viewportSize, imageSize, cropRect, minScale) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                if (imageSize == IntSize.Zero) return@detectTransformGestures
                                val nextScale = (interactionScale * zoom).coerceIn(minScale, 5f)
                                val nextOffset = clampOffset(interactionOffset + pan, nextScale)
                                interactionScale = nextScale
                                interactionOffset = nextOffset
                                updatePreview(nextScale, nextOffset)
                            }
                        }
                        .pointerInput(viewportSize, imageSize, cropRect, minScale, interactionScale, interactionOffset) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    val nextScale = if (interactionScale <= minScale + 0.06f) {
                                        (max(2f, minScale * 2f)).coerceAtMost(5f)
                                    } else {
                                        minScale
                                    }
                                    val nextOffset = if (nextScale == minScale) {
                                        Offset.Zero
                                    } else {
                                        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                                        val scaleFactor = if (interactionScale == 0f) 1f else nextScale / interactionScale
                                        val zoomTowardTapOffset = interactionOffset + (center - tapOffset) * (scaleFactor - 1f)
                                        clampOffset(zoomTowardTapOffset, nextScale)
                                    }
                                    interactionScale = nextScale
                                    interactionOffset = nextOffset
                                    updatePreview(nextScale, nextOffset)
                                }
                            )
                        }
                ) {
                    if (baseSize != IntSize.Zero) {
                        AsyncImage(
                            model = item.file ?: item.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(
                                    with(density) { baseSize.width.toDp() },
                                    with(density) { baseSize.height.toDp() }
                                )
                                .graphicsLayer {
                                    scaleX = currentScale
                                    scaleY = currentScale
                                    translationX = clampedOffset.x
                                    translationY = clampedOffset.y
                                }
                        )
                    }
                    CropOverlay(cropRect = cropRect)
                }
            }
        }
    }
}

@Composable
private fun CropOverlay(cropRect: Rect) {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val overlayColor = Color.Black.copy(alpha = 0.45f)
        val fullPath = Path().apply { addRect(Rect(Offset.Zero, size)) }
        val cutoutPath = Path().apply { addRoundRect(RoundRect(cropRect, 22.dp.toPx(), 22.dp.toPx())) }
        val maskPath = Path.combine(PathOperation.Difference, fullPath, cutoutPath)
        drawPath(maskPath, overlayColor)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.95f),
            topLeft = cropRect.topLeft,
            size = cropRect.size,
            cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

private fun buildCropOverlayRect(viewportSize: IntSize, preset: String): Rect {
    val (ratioW, ratioH) = presetToRatio(preset)
    val maxWidth = viewportSize.width * 0.84f
    val maxHeight = viewportSize.height * 0.72f
    val targetRatio = ratioW / ratioH
    val width: Float
    val height: Float
    if (maxWidth / maxHeight > targetRatio) {
        height = maxHeight
        width = height * targetRatio
    } else {
        width = maxWidth
        height = width / targetRatio
    }
    val left = (viewportSize.width - width) / 2f
    val top = (viewportSize.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun calculateBaseDisplaySize(viewportSize: IntSize, imageSize: IntSize): IntSize {
    if (viewportSize == IntSize.Zero || imageSize == IntSize.Zero) return viewportSize
    val viewportRatio = viewportSize.width.toFloat() / viewportSize.height.toFloat()
    val imageRatio = imageSize.width.toFloat() / imageSize.height.toFloat()
    return if (imageRatio > viewportRatio) {
        IntSize(viewportSize.width, (viewportSize.width / imageRatio).toInt())
    } else {
        IntSize((viewportSize.height * imageRatio).toInt(), viewportSize.height)
    }
}

private fun clampCropOffset(
    offset: Offset,
    scale: Float,
    viewportSize: IntSize,
    imageSize: IntSize,
    cropRect: Rect
): Offset {
    if (viewportSize == IntSize.Zero || imageSize == IntSize.Zero) return offset
    val baseSize = calculateBaseDisplaySize(viewportSize, imageSize)
    val scaledWidth = baseSize.width * scale
    val scaledHeight = baseSize.height * scale
    val horizontalSlack = ((scaledWidth - cropRect.width) / 2f).coerceAtLeast(0f)
    val verticalSlack = ((scaledHeight - cropRect.height) / 2f).coerceAtLeast(0f)
    return Offset(
        x = offset.x.coerceIn(-horizontalSlack, horizontalSlack),
        y = offset.y.coerceIn(-verticalSlack, verticalSlack)
    )
}

private fun calculateMinScaleToCoverCrop(baseSize: IntSize, cropRect: Rect): Float {
    if (baseSize == IntSize.Zero) return 1f
    val byWidth = if (baseSize.width == 0) 1f else cropRect.width / baseSize.width.toFloat()
    val byHeight = if (baseSize.height == 0) 1f else cropRect.height / baseSize.height.toFloat()
    return max(1f, max(byWidth, byHeight))
}

private fun presetToRatio(preset: String): Pair<Float, Float> {
    val parts = preset.split(':')
    if (parts.size != 2) return 1f to 1f
    val w = parts[0].toFloatOrNull() ?: return 1f to 1f
    val h = parts[1].toFloatOrNull() ?: return 1f to 1f
    if (w <= 0f || h <= 0f) return 1f to 1f
    return w to h
}

@Composable
private fun ToolResultCard(
    output: ToolOutput,
    onOpenDirectory: () -> Unit,
    onOpenInFileManager: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        shape = smoothCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(output.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(output.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpenDirectory, shape = smoothCornerShape(24.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("打开输出目录")
                }
                FilledTonalButton(onClick = onOpenInFileManager, shape = smoothCornerShape(24.dp)) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("文件管理中查看")
                }
                FilledTonalButton(onClick = onShare, shape = smoothCornerShape(24.dp)) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("分享结果")
                }
            }
        }
    }
}

@Composable
private fun ImageToolsPage(
    outputPath: String,
    isBusy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onResult: (ToolOutput) -> Unit,
    onPickFilesFromFileManager: (
        initialPath: String?,
        title: String,
        description: String,
        allowMultiSelect: Boolean,
        onOpenSystemPicker: () -> Unit,
        onPicked: (List<String>) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val showSnack = rememberSnackbarLauncher()
    val scope = rememberCoroutineScope()
    var imageQuality by rememberSaveable { mutableFloatStateOf(0.82f) }
    var exportFormat by rememberSaveable { mutableStateOf(ExportFormat.JPG) }
    var cropPreset by rememberSaveable { mutableStateOf("1:1") }
    var cropRatioW by rememberSaveable { mutableIntStateOf(1) }
    var cropRatioH by rememberSaveable { mutableIntStateOf(1) }
    var stitchDirection by rememberSaveable { mutableStateOf(StitchDirection.HORIZONTAL) }
    var stitchUpscaleSmall by rememberSaveable { mutableStateOf(false) }
    var compressSources by remember { mutableStateOf<List<PickedInput>>(emptyList()) }
    var nineGridSources by remember { mutableStateOf<List<PickedInput>>(emptyList()) }
    var cropSources by remember { mutableStateOf<List<PickedInput>>(emptyList()) }
    var stitchSources by remember { mutableStateOf<List<PickedInput>>(emptyList()) }
    val cropPreviewStates = remember { mutableStateMapOf<Int, CropPreviewState>() }
    var previewSheetState by remember { mutableStateOf<ImagePreviewSheetState?>(null) }

    fun setCropRatio(width: Int, height: Int) {
        val safeW = width.coerceIn(1, 20)
        val safeH = height.coerceIn(1, 20)
        cropRatioW = safeW
        cropRatioH = safeH
        cropPreset = "${safeW}:${safeH}"
        cropPreviewStates.clear()
    }

    fun showPreview(
        mode: ImagePreviewMode,
        items: List<PickedInput>,
        index: Int = 0,
        preset: String = cropPreset,
        sharedCropStates: MutableMap<Int, CropPreviewState> = cropPreviewStates
    ) {
        if (items.isNotEmpty()) {
            previewSheetState = ImagePreviewSheetState(
                mode = mode,
                items = items,
                initialIndex = index,
                cropPreset = preset,
                cropPreviewStates = sharedCropStates
            )
        }
    }

    fun updatePreviewItems(
        mode: ImagePreviewMode,
        items: List<PickedInput>,
        onUpdate: (List<PickedInput>) -> Unit,
        preset: String = cropPreset
    ) {
        onUpdate(items)
        if (items.size == 1) {
            showPreview(mode, items, preset = preset)
        }
    }

    fun runCompress(inputs: List<PickedInput>) {
        if (inputs.isEmpty()) return
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "图片工具")
                    val created = inputs.mapNotNull { input ->
                        val name = inputDisplayName(context, input)?.substringBeforeLast('.') ?: "image_${System.currentTimeMillis()}"
                        compressOrConvertImage(context, input, File(outputDir, "${sanitizeFileName(name)}.${exportFormat.extension}"), exportFormat, (imageQuality * 100).toInt())
                    }
                    scanMediaLibrary(context, created)
                    ToolOutput(
                        title = "图片处理完成",
                        message = "已生成 ${created.size} 个文件，输出到 ${outputDir.name}",
                        files = created,
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
                compressSources = emptyList()
            }.onFailure {
                showSnack("图片处理失败：${it.message ?: "未知错误"}")
            }
            onBusyChange(false)
        }
    }

    fun runNineGrid(inputs: List<PickedInput>) {
        if (inputs.isEmpty()) return
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "图片工具/九宫格")
                    val created = mutableListOf<File>()
                    inputs.forEach { input ->
                        created += splitToNineGrid(context, input, outputDir)
                    }
                    scanMediaLibrary(context, created)
                    ToolOutput(
                        title = "九宫格切图完成",
                        message = "共导出 ${created.size} 张切片",
                        files = created,
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
                nineGridSources = emptyList()
            }.onFailure {
                showSnack("九宫格切图失败")
            }
            onBusyChange(false)
        }
    }

    fun runCrop(inputs: List<PickedInput>, cropPreviewStateMap: Map<Int, CropPreviewState> = emptyMap()) {
        if (inputs.isEmpty()) return
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "图片工具/比例裁切")
                    val created = inputs.mapIndexedNotNull { index, input ->
                        cropImageWithPreset(context, input, outputDir, cropPreset, cropPreviewStateMap[index])
                    }
                    scanMediaLibrary(context, created)
                    ToolOutput(
                        title = "比例裁切完成",
                        message = "已输出 ${created.size} 张 $cropPreset 裁切图片",
                        files = created,
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
                cropSources = emptyList()
                cropPreviewStates.clear()
            }.onFailure {
                showSnack("比例裁切失败")
            }
            onBusyChange(false)
        }
    }

    fun runStitch(inputs: List<PickedInput>) {
        if (inputs.size < 2) {
            showSnack("请至少选择 2 张图片")
            return
        }
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "图片工具/拼图")
                    val merged = stitchImages(
                        context = context,
                        inputs = inputs,
                        outputDir = outputDir,
                        direction = stitchDirection,
                        upscaleSmall = stitchUpscaleSmall
                    ) ?: throw IllegalStateException("拼图失败")
                    scanMediaLibrary(context, listOf(merged))
                    ToolOutput(
                        title = "拼图完成",
                        message = "已导出 ${merged.name}",
                        files = listOf(merged),
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
                stitchSources = emptyList()
            }.onFailure {
                showSnack("拼图失败：${it.message ?: "未知错误"}")
            }
            onBusyChange(false)
        }
    }

    val pickImagesForCompress = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        updatePreviewItems(
            mode = ImagePreviewMode.COMPRESS,
            items = uris.toPickedUriInputs(),
            onUpdate = { compressSources = it }
        )
    }

    val pickImagesForNineGrid = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        updatePreviewItems(
            mode = ImagePreviewMode.NINE_GRID,
            items = uris.toPickedUriInputs(),
            onUpdate = { nineGridSources = it }
        )
    }

    val pickImagesForCrop = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        updatePreviewItems(
            mode = ImagePreviewMode.CROP,
            items = uris.toPickedUriInputs(),
            onUpdate = { cropSources = it },
            preset = cropPreset
        )
    }

    val pickImagesForStitch = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        stitchSources = uris.toPickedUriInputs()
    }

    ToolPageColumn {
        ToolCard(
            title = "压缩图片",
            subtitle = "调整格式与质量后导出图片",
            icon = Icons.Outlined.Compress
        ) {
            FilledTonalButton(
                onClick = {
                    onPickFilesFromFileManager(
                        AppPrefs.savePath,
                        "选择要压缩的图片",
                        "可在文件管理中多选图片，也可以使用系统选择器。",
                        true,
                        { pickImagesForCompress.launch("image/*") }
                    ) { paths ->
                        updatePreviewItems(
                            mode = ImagePreviewMode.COMPRESS,
                            items = paths.toPickedFileInputs(),
                            onUpdate = { compressSources = it }
                        )
                    }
                },
                enabled = !isBusy,
                shape = smoothCornerShape(24.dp)
            ) { Text("选择图片") }
            if (compressSources.isNotEmpty()) {
                Text("已选 ${compressSources.size} 张图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ImagePreviewStrip(items = compressSources) { index ->
                showPreview(ImagePreviewMode.COMPRESS, compressSources, index)
            }
            if (compressSources.isNotEmpty()) {
                FilledTonalButton(onClick = { showPreview(ImagePreviewMode.COMPRESS, compressSources) }, enabled = !isBusy, shape = smoothCornerShape(
                    24.dp
                )
                ) {
                    Text("在弹窗中导出")
                }
            }
        }

        ToolCard(
            title = "九宫格切图",
            subtitle = "按 3×3 规则切图并导出",
            icon = Icons.Outlined.ImageSearch
        ) {
            FilledTonalButton(onClick = {
                onPickFilesFromFileManager(
                    AppPrefs.savePath,
                    "选择要切图的图片",
                    "可在文件管理中多选图片，也可以使用系统选择器。",
                    true,
                    { pickImagesForNineGrid.launch("image/*") }
                ) { paths ->
                    updatePreviewItems(
                        mode = ImagePreviewMode.NINE_GRID,
                        items = paths.toPickedFileInputs(),
                        onUpdate = { nineGridSources = it }
                    )
                }
            }, enabled = !isBusy, shape = smoothCornerShape(24.dp)) {
                Text("选择图片")
            }
            if (nineGridSources.isNotEmpty()) {
                Text("已选 ${nineGridSources.size} 张图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ImagePreviewStrip(items = nineGridSources) { index ->
                showPreview(ImagePreviewMode.NINE_GRID, nineGridSources, index)
            }
            if (nineGridSources.isNotEmpty()) {
                FilledTonalButton(onClick = { runNineGrid(nineGridSources) }, enabled = !isBusy, shape = smoothCornerShape(
                    24.dp
                )
                ) {
                    Text("开始切图")
                }
            }
        }

        ToolCard(
            title = "图片拼图",
            subtitle = "多图横向/纵向拼接并导出",
            icon = Icons.Outlined.ViewAgenda
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StitchDirection.entries.forEach { direction ->
                    AssistChip(
                        onClick = { stitchDirection = direction },
                        shape = smoothCapsuleShape(),
                        label = { Text(direction.label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (stitchDirection == direction) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        )
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("放大小图到统一${if (stitchDirection == StitchDirection.HORIZONTAL) "高度" else "宽度"}")
                Switch(
                    checked = stitchUpscaleSmall,
                    onCheckedChange = { stitchUpscaleSmall = it }
                )
            }
            FilledTonalButton(onClick = {
                onPickFilesFromFileManager(
                    AppPrefs.savePath,
                    "选择要拼图的图片",
                    "可在文件管理中多选图片，也可以使用系统选择器。",
                    true,
                    { pickImagesForStitch.launch("image/*") }
                ) { paths ->
                    stitchSources = paths.toPickedFileInputs()
                }
            }, enabled = !isBusy, shape = smoothCornerShape(24.dp)) {
                Text("选择图片")
            }
            if (stitchSources.isNotEmpty()) {
                Text(
                    "已选 ${stitchSources.size} 张图片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ImagePreviewStrip(items = stitchSources) { }
            if (stitchSources.isNotEmpty()) {
                FilledTonalButton(
                    onClick = { runStitch(stitchSources) },
                    enabled = !isBusy,
                    shape = smoothCornerShape(24.dp)
                ) {
                    Text("开始拼图并导出")
                }
            }
        }

        ToolCard(
            title = "按比例裁切",
            subtitle = "按比例裁切图片并导出",
            icon = Icons.Outlined.ContentCut
        ) {
            Text("裁切比例：$cropPreset", style = MaterialTheme.typography.bodyMedium)
            Text("横向比例：$cropRatioW", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = cropRatioW.toFloat(),
                onValueChange = { setCropRatio(it.toInt(), cropRatioH) },
                valueRange = 1f..20f,
                steps = 18
            )
            Text("纵向比例：$cropRatioH", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = cropRatioH.toFloat(),
                onValueChange = { setCropRatio(cropRatioW, it.toInt()) },
                valueRange = 1f..20f,
                steps = 18
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CROP_PRESETS.forEach { (label, ratio) ->
                    AssistChip(
                        onClick = {
                            setCropRatio(ratio.first, ratio.second)
                        },
                        shape = smoothCapsuleShape(),
                        label = { Text(label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (cropPreset == label) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                }
            }
            FilledTonalButton(onClick = {
                onPickFilesFromFileManager(
                    AppPrefs.savePath,
                    "选择要裁切的图片",
                    "可在文件管理中多选图片，也可以使用系统选择器。",
                    true,
                    { pickImagesForCrop.launch("image/*") }
                ) { paths ->
                    cropPreviewStates.clear()
                    updatePreviewItems(
                        mode = ImagePreviewMode.CROP,
                        items = paths.toPickedFileInputs(),
                        onUpdate = { cropSources = it },
                        preset = cropPreset
                    )
                }
            }, enabled = !isBusy, shape = smoothCornerShape(24.dp)) {
                Text("选择图片")
            }
            if (cropSources.isNotEmpty()) {
                Text("已选 ${cropSources.size} 张图片，输出比例 $cropPreset", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ImagePreviewStrip(items = cropSources) { index ->
                showPreview(
                    mode = ImagePreviewMode.CROP,
                    items = cropSources,
                    index = index,
                    preset = cropPreset,
                    sharedCropStates = cropPreviewStates
                )
            }
            if (cropSources.isNotEmpty()) {
                FilledTonalButton(onClick = { runCrop(cropSources, cropPreviewStates) }, enabled = !isBusy, shape = smoothCornerShape(
                    24.dp
                )
                ) {
                    Text("开始裁切并导出")
                }
            }
        }

    }

    previewSheetState?.let { sheetState ->
        ImagePreviewBottomSheet(
            state = sheetState,
            onDismiss = { previewSheetState = null },
            compressQuality = imageQuality,
            onCompressQualityChange = { imageQuality = it },
            compressFormat = exportFormat,
            onCompressFormatChange = { exportFormat = it },
            onConfirm = {
                when (sheetState.mode) {
                    ImagePreviewMode.COMPRESS -> runCompress(sheetState.items)
                    ImagePreviewMode.NINE_GRID -> runNineGrid(sheetState.items)
                    ImagePreviewMode.CROP -> runCrop(sheetState.items, sheetState.cropPreviewStates)
                }
                previewSheetState = null
            }
        )
    }
}

@Composable
private fun TextToolsPage(
    outputPath: String,
    isBusy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onResult: (ToolOutput) -> Unit,
    onPickDirectoryFromFileManager: (
        initialPath: String?,
        title: String,
        description: String,
        onPicked: (String) -> Unit
    ) -> Unit,
    onPickFilesFromFileManager: (
        initialPath: String?,
        title: String,
        description: String,
        allowMultiSelect: Boolean,
        onOpenSystemPicker: () -> Unit,
        onPicked: (List<String>) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val showSnack = rememberSnackbarLauncher()
    val scope = rememberCoroutineScope()
    var findText by rememberSaveable { mutableStateOf("") }
    var replaceText by rememberSaveable { mutableStateOf("") }
    var collapseBlankLines by rememberSaveable { mutableStateOf(true) }
    var trimLines by rememberSaveable { mutableStateOf(true) }
    var normalizeSpaces by rememberSaveable { mutableStateOf(true) }
    var directoryPath by rememberSaveable { mutableStateOf(AppPrefs.savePath) }

    fun processTextInputs(inputs: List<PickedInput>) {
        if (inputs.isEmpty()) return
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "文本工具")
                    val created = inputs.mapNotNull { input ->
                        val source = readTextFromInput(context, input) ?: return@mapNotNull null
                        val cleaned = cleanTextContent(source, collapseBlankLines, trimLines, normalizeSpaces)
                        val replaced = if (findText.isNotEmpty()) cleaned.replace(findText, replaceText) else cleaned
                        val display = inputDisplayName(context, input) ?: "text_${System.currentTimeMillis()}.txt"
                        val target = File(outputDir, sanitizeFileName(display))
                        target.writeText(replaced)
                        target
                    }
                    ToolOutput(
                        title = "文本处理完成",
                        message = "已输出 ${created.size} 个文本文件",
                        files = created,
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
            }.onFailure {
                showSnack("文本处理失败")
            }
            onBusyChange(false)
        }
    }

    val textPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        processTextInputs(uris.toPickedUriInputs())
    }

    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { getPathFromUri(it)?.let { path -> directoryPath = path } }
    }

    ToolPageColumn {
        ToolCard(
            title = "清洗文本",
            subtitle = "清洗文本并按需执行查找替换",
            icon = Icons.Outlined.CleaningServices
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "折叠空行" to collapseBlankLines,
                    "裁掉行首尾空格" to trimLines,
                    "压缩连续空格" to normalizeSpaces
                ).forEach { (label, checked) ->
                    AssistChip(
                        onClick = {
                            when (label) {
                                "折叠空行" -> collapseBlankLines = !collapseBlankLines
                                "裁掉行首尾空格" -> trimLines = !trimLines
                                else -> normalizeSpaces = !normalizeSpaces
                            }
                        },
                        shape = smoothCapsuleShape(),
                        label = { Text(label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                }
            }
            OutlinedTextField(
                value = findText,
                onValueChange = { findText = it },
                label = { Text("查找内容（可选）") },
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(16.dp)
            )
            OutlinedTextField(
                value = replaceText,
                onValueChange = { replaceText = it },
                label = { Text("替换为（可选）") },
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(16.dp)
            )
            FilledTonalButton(onClick = {
                onPickFilesFromFileManager(
                    directoryPath,
                    "选择文本文件",
                    "优先在文件管理中选择文本文件，也可改用系统选择器。",
                    true,
                    { textPicker.launch("*/*") }
                ) { paths ->
                    processTextInputs(paths.toPickedFileInputs())
                }
            }, enabled = !isBusy, shape = smoothCornerShape(24.dp)) {
                Text("选择文本文件")
            }
        }

        ToolCard(
            title = "规范文件名",
            subtitle = "选择目录后统一清理非法字符和多余空白",
            icon = Icons.Outlined.SortByAlpha
        ) {
            Text(directoryPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DirectorySelectionActions(
                onPickInFileManager = {
                    onPickDirectoryFromFileManager(
                        directoryPath,
                        "选择要净化命名的目录",
                        "在文件管理中浏览并确认目录，或使用系统选择器。"
                    ) { pickedPath ->
                        directoryPath = pickedPath
                    }
                },
                onPickInSystem = { dirPicker.launch(null) }
            )
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        onBusyChange(true)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val dir = File(directoryPath)
                                val renamed = sanitizeNamesInDirectory(dir)
                                ToolOutput(
                                    title = "文件名净化完成",
                                    message = "共重命名 ${renamed.size} 个项目",
                                    files = renamed,
                                    directory = dir
                                )
                            }
                        }.onSuccess {
                            onResult(it)
                            showSnack(it.message)
                        }.onFailure {
                            showSnack("文件名净化失败")
                        }
                        onBusyChange(false)
                    }
                },
                enabled = !isBusy,
                shape = smoothCornerShape(24.dp)
            ) { Text("开始整理文件名") }
        }

    }
}

@Composable
private fun AudioToolsPage(
    outputPath: String,
    isBusy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onResult: (ToolOutput) -> Unit,
    onPickDirectoryFromFileManager: (
        initialPath: String?,
        title: String,
        description: String,
        onPicked: (String) -> Unit
    ) -> Unit,
    onPickFilesFromFileManager: (
        initialPath: String?,
        title: String,
        description: String,
        allowMultiSelect: Boolean,
        onOpenSystemPicker: () -> Unit,
        onPicked: (List<String>) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val showSnack = rememberSnackbarLauncher()
    val scope = rememberCoroutineScope()
    val audioInfos = remember { mutableStateListOf<AudioInfoItem>() }
    var directoryPath by rememberSaveable { mutableStateOf(AppPrefs.savePath) }
    var prefix by rememberSaveable { mutableStateOf("audio_") }

    fun processAudioInputs(inputs: List<PickedInput>) {
        if (inputs.isEmpty()) return
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val infos = inputs.mapNotNull { readAudioInfo(context, it) }
                    audioInfos.clear()
                    audioInfos.addAll(infos)
                    ToolOutput(
                        title = "音频信息已读取",
                        message = "已分析 ${infos.size} 个音频文件",
                        directory = resolveOutputDirectory(outputPath, "音频工具")
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
            }.onFailure {
                showSnack("读取音频信息失败")
            }
            onBusyChange(false)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        processAudioInputs(uris.toPickedUriInputs())
    }

    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { getPathFromUri(it)?.let { path -> directoryPath = path } }
    }

    ToolPageColumn {
        ToolCard(
            title = "查看音频信息",
            subtitle = "查看音频时长、大小与元数据",
            icon = Icons.Outlined.Info
        ) {
            FilledTonalButton(onClick = {
                onPickFilesFromFileManager(
                    directoryPath,
                    "选择音频文件",
                    "优先在文件管理中选择音频文件，也可改用系统选择器。",
                    true,
                    { audioPicker.launch("audio/*") }
                ) { paths ->
                    processAudioInputs(paths.toPickedFileInputs())
                }
            }, enabled = !isBusy, shape = smoothCornerShape(24.dp)) {
                Text("选择音频")
            }
            if (audioInfos.isNotEmpty()) {
                HorizontalDivider()
                audioInfos.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${formatDuration(item.durationMs)} · ${formatFileSize(item.size)} · ${item.mimeType}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            listOfNotNull(item.artist, item.sampleRate?.let { "${it}Hz" }, item.bitrate?.let { "${it}bps" }).joinToString(" · ").ifBlank { "无更多元数据" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }

        ToolCard(
            title = "批量重命名音频",
            subtitle = "按前缀和序号批量重命名音频",
            icon = Icons.Outlined.Transform
        ) {
            OutlinedTextField(
                value = prefix,
                onValueChange = { prefix = it },
                label = { Text("文件名前缀") },
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(16.dp)
            )
            Text(directoryPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DirectorySelectionActions(
                onPickInFileManager = {
                    onPickDirectoryFromFileManager(
                        directoryPath,
                        "选择音频目录",
                        "在文件管理中浏览并确认目录，或使用系统选择器。"
                    ) { pickedPath ->
                        directoryPath = pickedPath
                    }
                },
                onPickInSystem = { dirPicker.launch(null) }
            )
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        onBusyChange(true)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val dir = File(directoryPath)
                                val renamed = batchRenameByPrefix(dir, prefix) { it.extension.lowercase() in AUDIO_EXTENSIONS }
                                ToolOutput(
                                    title = "音频批量重命名完成",
                                    message = "已处理 ${renamed.size} 个音频文件",
                                    files = renamed,
                                    directory = dir
                                )
                            }
                        }.onSuccess {
                            onResult(it)
                            showSnack(it.message)
                        }.onFailure {
                            showSnack("音频重命名失败")
                        }
                        onBusyChange(false)
                    }
                },
                enabled = !isBusy,
                shape = smoothCornerShape(24.dp)
            ) { Text("开始重命名音频") }
        }

    }
}

@Composable
private fun OrganizeToolsPage(
    isBusy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onResult: (ToolOutput) -> Unit,
    onPickDirectoryFromFileManager: (
        initialPath: String?,
        title: String,
        description: String,
        onPicked: (String) -> Unit
    ) -> Unit
) {
    val showSnack = rememberSnackbarLauncher()
    val scope = rememberCoroutineScope()
    var directoryPath by rememberSaveable { mutableStateOf(AppPrefs.savePath) }
    var renamePrefix by rememberSaveable { mutableStateOf("asset_") }
    var summary by remember { mutableStateOf<DirectorySummary?>(null) }

    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { getPathFromUri(it)?.let { path -> directoryPath = path } }
    }

    ToolPageColumn {
        ToolCard(
            title = "按类型整理文件",
            subtitle = "按后缀自动分类文件",
            icon = Icons.Outlined.FolderOpen
        ) {
            Text(directoryPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DirectorySelectionActions(
                onPickInFileManager = {
                    onPickDirectoryFromFileManager(
                        directoryPath,
                        "选择整理目录",
                        "在文件管理中浏览并确认目录，或使用系统选择器。"
                    ) { pickedPath ->
                        directoryPath = pickedPath
                    }
                },
                onPickInSystem = { dirPicker.launch(null) }
            )
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        onBusyChange(true)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val dir = File(directoryPath)
                                val moved = classifyFilesByExtension(dir)
                                ToolOutput(
                                    title = "分类整理完成",
                                    message = "共移动 ${moved.size} 个文件",
                                    files = moved,
                                    directory = dir
                                )
                            }
                        }.onSuccess {
                            onResult(it)
                            showSnack(it.message)
                        }.onFailure {
                            showSnack("分类整理失败")
                        }
                        onBusyChange(false)
                    }
                },
                enabled = !isBusy,
                shape = smoothCornerShape(24.dp)
            ) { Text("开始分类整理") }
        }

        ToolCard(
            title = "批量重命名文件",
            subtitle = "为当前目录文件批量添加前缀与序号",
            icon = Icons.Outlined.SortByAlpha
        ) {
            OutlinedTextField(
                value = renamePrefix,
                onValueChange = { renamePrefix = it },
                label = { Text("前缀") },
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(16.dp)
            )
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        onBusyChange(true)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val dir = File(directoryPath)
                                val renamed = batchRenameByPrefix(dir, renamePrefix) { it.isFile }
                                ToolOutput(
                                    title = "批量重命名完成",
                                    message = "共重命名 ${renamed.size} 个文件",
                                    files = renamed,
                                    directory = dir
                                )
                            }
                        }.onSuccess {
                            onResult(it)
                            showSnack(it.message)
                        }.onFailure {
                            showSnack("批量重命名失败")
                        }
                        onBusyChange(false)
                    }
                },
                enabled = !isBusy,
                shape = smoothCornerShape(24.dp)
            ) { Text("开始批量重命名") }
        }

        ToolCard(
            title = "扫描目录摘要",
            subtitle = "清理空目录并统计目录概况",
            icon = Icons.Outlined.Info
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            onBusyChange(true)
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    val dir = File(directoryPath)
                                    val deleted = deleteEmptyDirectories(dir)
                                    summary = scanDirectorySummary(dir)
                                    ToolOutput(
                                        title = "目录扫描完成",
                                        message = "删除空文件夹 $deleted 个，统计 ${summary?.totalFiles ?: 0} 个文件",
                                        directory = dir
                                    )
                                }
                            }.onSuccess {
                                onResult(it)
                                showSnack(it.message)
                            }.onFailure {
                                showSnack("目录扫描失败")
                            }
                            onBusyChange(false)
                        }
                    },
                    enabled = !isBusy,
                    shape = smoothCornerShape(24.dp)
                ) { Text("扫描并清理空目录") }
            }
            summary?.let { info ->
                HorizontalDivider()
                Text("文件 ${info.totalFiles} · 文件夹 ${info.totalDirectories} · ${formatFileSize(info.totalSize)}")
                Text(
                    info.extensionCounts.take(5).joinToString("  ·  ") { (ext, count) -> "$ext: $count" }.ifBlank { "暂无文件" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SubmissionToolsPage(
    outputPath: String,
    isBusy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onResult: (ToolOutput) -> Unit,
    onPickDirectoryFromFileManager: (
        initialPath: String?,
        title: String,
        description: String,
        onPicked: (String) -> Unit
    ) -> Unit
) {
    val showSnack = rememberSnackbarLauncher()
    val scope = rememberCoroutineScope()
    var directoryPath by rememberSaveable { mutableStateOf(AppPrefs.savePath) }
    var sizeLimitMb by rememberSaveable { mutableIntStateOf(20) }
    var checklistFormat by rememberSaveable { mutableStateOf(ChecklistFormat.MARKDOWN) }
    var namingRegex by rememberSaveable { mutableStateOf("^[A-Za-z0-9_\\-\\u4e00-\\u9fa5. ]+$") }
    var reportPreview by remember { mutableStateOf("") }

    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { getPathFromUri(it)?.let { path -> directoryPath = path } }
    }

    ToolPageColumn {
        ToolCard(
            title = "检查投稿素材",
            subtitle = "检查素材体积与命名规则",
            icon = Icons.AutoMirrored.Outlined.FactCheck
        ) {
            Text(directoryPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = sizeLimitMb.toString(),
                onValueChange = { sizeLimitMb = it.toIntOrNull()?.coerceAtLeast(1) ?: sizeLimitMb },
                label = { Text("单文件体积上限（MB）") },
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(16.dp)
            )
            OutlinedTextField(
                value = namingRegex,
                onValueChange = { namingRegex = it },
                label = { Text("命名校验正则") },
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(16.dp)
            )
            DirectorySelectionActions(
                onPickInFileManager = {
                    onPickDirectoryFromFileManager(
                        directoryPath,
                        "选择素材目录",
                        "在文件管理中浏览并确认目录，或使用系统选择器。"
                    ) { pickedPath ->
                        directoryPath = pickedPath
                    }
                },
                onPickInSystem = { dirPicker.launch(null) },
                label = "选择素材目录"
            )
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        onBusyChange(true)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val report = buildSubmissionReport(File(directoryPath), sizeLimitMb.toLong() * 1024 * 1024, namingRegex)
                                reportPreview = report
                                ToolOutput(
                                    title = "投稿检查完成",
                                    message = "已完成目录检查，可继续导出清单",
                                    directory = File(directoryPath)
                                )
                            }
                        }.onSuccess {
                            onResult(it)
                            showSnack(it.message)
                        }.onFailure {
                            showSnack("投稿检查失败")
                        }
                        onBusyChange(false)
                    }
                },
                enabled = !isBusy,
                shape = smoothCornerShape(24.dp)
            ) { Text("开始检查素材") }
            if (reportPreview.isNotBlank()) {
                HorizontalDivider()
                Text(reportPreview.take(800), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        ToolCard(
            title = "导出投稿清单",
            subtitle = "导出 TXT 或 Markdown 检查清单",
            icon = Icons.Outlined.SaveAlt
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChecklistFormat.entries.forEach { format ->
                    AssistChip(
                        onClick = { checklistFormat = format },
                        shape = smoothCapsuleShape(),
                        label = { Text(format.label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (format == checklistFormat) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                }
            }
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        onBusyChange(true)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val outputDir = resolveOutputDirectory(outputPath, "投稿辅助")
                                val report = reportPreview.ifBlank { buildSubmissionReport(File(directoryPath), sizeLimitMb.toLong() * 1024 * 1024, namingRegex) }
                                val file = File(outputDir, "投稿清单_${System.currentTimeMillis()}.${checklistFormat.extension}")
                                file.writeText(if (checklistFormat == ChecklistFormat.MARKDOWN) reportToMarkdown(report) else report)
                                ToolOutput(
                                    title = "投稿清单已导出",
                                    message = "已导出 ${file.name}",
                                    files = listOf(file),
                                    directory = outputDir
                                )
                            }
                        }.onSuccess {
                            onResult(it)
                            showSnack(it.message)
                        }.onFailure {
                            showSnack("导出投稿清单失败")
                        }
                        onBusyChange(false)
                    }
                },
                enabled = !isBusy,
                shape = smoothCornerShape(24.dp)
            ) { Text("导出检查清单") }
        }
    }
}

private fun openBitmap(context: Context, input: PickedInput): Bitmap? {
    input.file?.let { return BitmapFactory.decodeFile(it.absolutePath) }
    return input.uri?.let { uri ->
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }
}

private fun inputDisplayName(context: Context, input: PickedInput): String? {
    return input.file?.name ?: input.uri?.let(context::queryDisplayName)
}

private fun readTextFromInput(context: Context, input: PickedInput): String? {
    input.file?.takeIf { it.isFile }?.let { return it.readText() }
    return input.uri?.let(context::readTextFromUri)
}

private fun compressOrConvertImage(
    context: Context,
    input: PickedInput,
    target: File,
    format: ExportFormat,
    quality: Int
): File? {
    val bitmap = openBitmap(context, input) ?: return null
    target.parentFile?.mkdirs()
    FileOutputStream(target).use { out ->
        bitmap.compress(resolveCompressFormat(format), quality.coerceIn(1, 100), out)
    }
    bitmap.recycle()
    return target
}

private fun resolveCompressFormat(format: ExportFormat): Bitmap.CompressFormat {
    return when (format) {
        ExportFormat.JPG -> Bitmap.CompressFormat.JPEG
        ExportFormat.PNG -> Bitmap.CompressFormat.PNG
        ExportFormat.WEBP -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        }
    }
}

private fun buildCompressPreviewData(
    context: Context,
    input: PickedInput,
    format: ExportFormat,
    quality: Float
): CompressPreviewData? {
    val bitmap = openBitmap(context, input) ?: return null
    val output = ByteArrayOutputStream()
    bitmap.compress(resolveCompressFormat(format), (quality * 100).toInt().coerceIn(1, 100), output)
    val previewBytes = output.toByteArray()
    output.close()
    val originalSize = when {
        input.file != null -> input.file.length().takeIf { it > 0 }
        input.uri != null -> runCatching {
            context.contentResolver.openAssetFileDescriptor(input.uri, "r")?.use { fd ->
                fd.length.takeIf { it > 0 }
            }
        }.getOrNull()
        else -> null
    }
    bitmap.recycle()
    return CompressPreviewData(
        previewBytes = previewBytes,
        originalSizeBytes = originalSize,
        compressedSizeBytes = previewBytes.size
    )
}

private fun resolveSectionOutputDirectory(basePath: String, section: ToolsSection): File {
    val child = when (section) {
        ToolsSection.IMAGE -> "图片工具"
        ToolsSection.TEXT -> "文本工具"
        ToolsSection.AUDIO -> "音频工具"
        ToolsSection.ORGANIZE -> null
        ToolsSection.SUBMISSION -> "投稿辅助"
    }
    return resolveOutputDirectory(basePath, child)
}

private fun splitToNineGrid(context: Context, input: PickedInput, outputDir: File): List<File> {
    val bitmap = openBitmap(context, input) ?: return emptyList()
    val baseName = sanitizeFileName(inputDisplayName(context, input)?.substringBeforeLast('.') ?: "grid")
    val cellWidth = max(1, bitmap.width / 3)
    val cellHeight = max(1, bitmap.height / 3)
    val outputs = mutableListOf<File>()
    repeat(3) { row ->
        repeat(3) { col ->
            val x = min(bitmap.width - cellWidth, col * cellWidth)
            val y = min(bitmap.height - cellHeight, row * cellHeight)
            val piece = Bitmap.createBitmap(bitmap, x, y, min(cellWidth, bitmap.width - x), min(cellHeight, bitmap.height - y))
            val target = File(outputDir, "${baseName}_${row + 1}_${col + 1}.jpg")
            FileOutputStream(target).use { piece.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            piece.recycle()
            outputs += target
        }
    }
    bitmap.recycle()
    return outputs
}

private fun stitchImages(
    context: Context,
    inputs: List<PickedInput>,
    outputDir: File,
    direction: StitchDirection,
    upscaleSmall: Boolean
): File? {
    val bitmaps = inputs.mapNotNull { openBitmap(context, it) }
    if (bitmaps.size < 2) {
        bitmaps.forEach { it.recycle() }
        return null
    }

    val targetCross = when (direction) {
        StitchDirection.HORIZONTAL -> {
            val values = bitmaps.map { it.height }
            if (upscaleSmall) values.maxOrNull() ?: 0 else values.minOrNull() ?: 0
        }
        StitchDirection.VERTICAL -> {
            val values = bitmaps.map { it.width }
            if (upscaleSmall) values.maxOrNull() ?: 0 else values.minOrNull() ?: 0
        }
    }.coerceAtLeast(1)

    val scaledBitmaps = bitmaps.map { bitmap ->
        when (direction) {
            StitchDirection.HORIZONTAL -> {
                val scale = targetCross.toFloat() / bitmap.height.toFloat()
                val targetW = max(1, (bitmap.width * scale).roundToInt())
                val targetH = targetCross
                if (bitmap.width == targetW && bitmap.height == targetH) bitmap
                else bitmap.scale(targetW, targetH)
            }
            StitchDirection.VERTICAL -> {
                val scale = targetCross.toFloat() / bitmap.width.toFloat()
                val targetW = targetCross
                val targetH = max(1, (bitmap.height * scale).roundToInt())
                if (bitmap.width == targetW && bitmap.height == targetH) bitmap
                else bitmap.scale(targetW, targetH)
            }
        }
    }

    val (outputWidth, outputHeight) = when (direction) {
        StitchDirection.HORIZONTAL -> scaledBitmaps.sumOf { it.width } to targetCross
        StitchDirection.VERTICAL -> targetCross to scaledBitmaps.sumOf { it.height }
    }

    val merged = createBitmap(outputWidth, outputHeight)
    val canvas = Canvas(merged)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    var cursor = 0f
    scaledBitmaps.forEach { bmp ->
        when (direction) {
            StitchDirection.HORIZONTAL -> {
                canvas.drawBitmap(bmp, cursor, 0f, paint)
                cursor += bmp.width
            }
            StitchDirection.VERTICAL -> {
                canvas.drawBitmap(bmp, 0f, cursor, paint)
                cursor += bmp.height
            }
        }
    }

    outputDir.mkdirs()
    val target = File(outputDir, "拼图_${direction.label}_${System.currentTimeMillis()}.jpg")
    FileOutputStream(target).use { merged.compress(Bitmap.CompressFormat.JPEG, 94, it) }

    merged.recycle()
    scaledBitmaps.forEachIndexed { index, scaled ->
        if (scaled !== bitmaps[index]) scaled.recycle()
    }
    bitmaps.forEach { it.recycle() }
    return target
}

private fun cropImageWithPreset(
    context: Context,
    input: PickedInput,
    outputDir: File,
    preset: String,
    previewState: CropPreviewState? = null
): File? {
    val bitmap = openBitmap(context, input) ?: return null
    if (previewState != null && previewState.viewportSize != IntSize.Zero && previewState.imageSize != IntSize.Zero) {
        val cropRect = buildCropOverlayRect(previewState.viewportSize, preset)
        val baseSize = calculateBaseDisplaySize(previewState.viewportSize, previewState.imageSize)
        val displayWidth = baseSize.width * previewState.scale
        val displayHeight = baseSize.height * previewState.scale
        if (displayWidth > 0f && displayHeight > 0f) {
            val imageLeft = (previewState.viewportSize.width - displayWidth) / 2f + previewState.offset.x
            val imageTop = (previewState.viewportSize.height - displayHeight) / 2f + previewState.offset.y
            val srcLeft = ((cropRect.left - imageLeft) / displayWidth * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val srcTop = ((cropRect.top - imageTop) / displayHeight * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val srcRight = ((cropRect.right - imageLeft) / displayWidth * bitmap.width).toInt().coerceIn(srcLeft + 1, bitmap.width)
            val srcBottom = ((cropRect.bottom - imageTop) / displayHeight * bitmap.height).toInt().coerceIn(srcTop + 1, bitmap.height)
            val cropped = Bitmap.createBitmap(bitmap, srcLeft, srcTop, srcRight - srcLeft, srcBottom - srcTop)
            val baseName = sanitizeFileName(inputDisplayName(context, input)?.substringBeforeLast('.') ?: "crop")
            val target = File(outputDir, "${baseName}_${preset.replace(':', 'x')}.jpg")
            FileOutputStream(target).use { cropped.compress(Bitmap.CompressFormat.JPEG, 94, it) }
            cropped.recycle()
            bitmap.recycle()
            return target
        }
    }
    val (ratioW, ratioH) = presetToRatio(preset)
    val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
    val targetRatio = ratioW / ratioH
    val cropWidth: Int
    val cropHeight: Int
    if (srcRatio > targetRatio) {
        cropHeight = bitmap.height
        cropWidth = (cropHeight * targetRatio).toInt()
    } else {
        cropWidth = bitmap.width
        cropHeight = (cropWidth / targetRatio).toInt()
    }
    val x = max(0, (bitmap.width - cropWidth) / 2)
    val y = max(0, (bitmap.height - cropHeight) / 2)
    val cropped = Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
    val baseName = sanitizeFileName(inputDisplayName(context, input)?.substringBeforeLast('.') ?: "crop")
    val target = File(outputDir, "${baseName}_${preset.replace(':', 'x')}.jpg")
    FileOutputStream(target).use { cropped.compress(Bitmap.CompressFormat.JPEG, 94, it) }
    cropped.recycle()
    bitmap.recycle()
    return target
}

private fun readImageSize(context: Context, input: PickedInput): IntSize? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    when {
        input.file != null -> BitmapFactory.decodeFile(input.file.absolutePath, options)
        input.uri != null -> context.contentResolver.openInputStream(input.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        else -> return null
    }
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    return IntSize(options.outWidth, options.outHeight)
}

private fun cleanTextContent(source: String, collapseBlankLines: Boolean, trimLines: Boolean, normalizeSpaces: Boolean): String {
    var text = source.replace("\r\n", "\n")
    text = text.lines().joinToString("\n") { line ->
        var current = line
        if (normalizeSpaces) current = current.replace(Regex("[\\t ]+"), " ")
        if (trimLines) current = current.trim()
        current
    }
    if (collapseBlankLines) {
        text = text.replace(Regex("\n{3,}"), "\n\n")
    }
    return text.trim() + "\n"
}

private fun sanitizeFileName(name: String): String {
    return name
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "untitled" }
}

private fun sanitizeNamesInDirectory(directory: File): List<File> {
    if (!directory.exists() || !directory.isDirectory) return emptyList()
    val renamed = mutableListOf<File>()
    directory.listFiles()?.forEach { file ->
        val target = File(file.parentFile, sanitizeFileName(file.name))
        if (target.absolutePath != file.absolutePath && !target.exists() && file.renameTo(target)) {
            renamed += target
        }
    }
    return renamed
}

private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "m4a", "flac", "ogg", "aac")

private fun readAudioInfo(context: Context, input: PickedInput): AudioInfoItem? {
    val retriever = MediaMetadataRetriever()
    return runCatching {
        input.file?.let { file ->
            retriever.setDataSource(file.absolutePath)
        } ?: input.uri?.let { uri ->
            retriever.setDataSource(context, uri)
        } ?: return null
        val name = inputDisplayName(context, input) ?: "audio"
        val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "audio/*"
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
        } else {
            null
        }
        val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
        val size = input.file?.length() ?: input.uri?.let { uri ->
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.statSize
            } ?: 0L
        } ?: 0L
        AudioInfoItem(name, duration, mime, artist, sampleRate, bitrate, size)
    }.getOrNull().also {
        runCatching { retriever.release() }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private fun batchRenameByPrefix(directory: File, prefix: String, predicate: (File) -> Boolean): List<File> {
    if (!directory.exists() || !directory.isDirectory) return emptyList()
    val files = directory.listFiles()?.filter(predicate)?.sortedBy { it.name.lowercase() } ?: return emptyList()
    val renamed = mutableListOf<File>()
    files.forEachIndexed { index, file ->
        val ext = file.extension.takeIf { it.isNotBlank() }?.let { ".$it" } ?: ""
        val target = File(directory, "${sanitizeFileName(prefix)}${(index + 1).toString().padStart(3, '0')}$ext")
        if (target.absolutePath != file.absolutePath && !target.exists() && file.renameTo(target)) {
            renamed += target
        }
    }
    return renamed
}

private fun classifyFilesByExtension(directory: File): List<File> {
    if (!directory.exists() || !directory.isDirectory) return emptyList()
    val moved = mutableListOf<File>()
    directory.listFiles()?.filter { it.isFile }?.forEach { file ->
        val ext = file.extension.lowercase().ifBlank { "others" }
        val targetDir = File(directory, ext)
        targetDir.mkdirs()
        val target = File(targetDir, file.name)
        if (!target.exists() && file.renameTo(target)) {
            moved += target
        }
    }
    return moved
}

private fun deleteEmptyDirectories(directory: File): Int {
    if (!directory.exists() || !directory.isDirectory) return 0
    var deleted = 0
    directory.listFiles()?.filter { it.isDirectory }?.forEach { child ->
        deleted += deleteEmptyDirectories(child)
        if (child.listFiles().isNullOrEmpty() && child.delete()) {
            deleted++
        }
    }
    return deleted
}

private fun scanDirectorySummary(directory: File): DirectorySummary {
    var files = 0
    var dirs = 0
    var totalSize = 0L
    val extCount = linkedMapOf<String, Int>()
    directory.walkTopDown().forEach { file ->
        if (file == directory) return@forEach
        if (file.isDirectory) {
            dirs++
        } else {
            files++
            totalSize += file.length()
            val ext = file.extension.lowercase().ifBlank { "others" }
            extCount[ext] = (extCount[ext] ?: 0) + 1
        }
    }
    return DirectorySummary(files, dirs, totalSize, extCount.toList().sortedByDescending { it.second })
}

private fun buildSubmissionReport(directory: File, sizeLimitBytes: Long, namingRegex: String): String {
    val regex = namingRegex.toRegex()
    val allFiles = directory.walkTopDown().filter { it.isFile }.toList()
    val oversized = allFiles.filter { it.length() > sizeLimitBytes }
    val invalidNames = allFiles.filterNot { regex.matches(it.name) }
    return buildString {
        appendLine("目录：${directory.absolutePath}")
        appendLine("文件总数：${allFiles.size}")
        appendLine("总体积：${formatFileSize(allFiles.sumOf { it.length() })}")
        appendLine("超限文件：${oversized.size}")
        oversized.forEach { appendLine("- 超限: ${it.name} (${formatFileSize(it.length())})") }
        appendLine("命名不合规：${invalidNames.size}")
        invalidNames.forEach { appendLine("- 命名: ${it.name}") }
    }
}

private fun reportToMarkdown(report: String): String {
    return buildString {
        appendLine("# 投稿清单")
        appendLine()
        report.lineSequence().forEach { line ->
            when {
                line.startsWith("- ") -> appendLine(line)
                line.contains("：") -> appendLine("- $line")
                else -> appendLine(line)
            }
        }
    }
}
