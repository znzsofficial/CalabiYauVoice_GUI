package com.nekolaska.calabiyau.feature.tools

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private enum class ToolsSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    IMAGE("图片工具", "压缩、转换、九宫格与裁切入口", Icons.Outlined.Image),
    TEXT("文本工具", "时间轴编辑与字幕格式转换", Icons.Outlined.TextFields),
    AUDIO("音频工具", "查看音频信息与轻量整理", Icons.Outlined.AudioFile)
}

private fun resolveSectionOutputDirectory(basePath: String, section: ToolsSection): File {
    val child = when (section) {
        ToolsSection.IMAGE -> "图片工具"
        ToolsSection.TEXT -> "文本工具"
        ToolsSection.AUDIO -> "音频工具"
    }
    return resolveOutputDirectory(basePath, child)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsHomeScreen(
    onBack: () -> Unit,
    backEnabled: Boolean = true,
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

    BackHandler(enabled = backEnabled && currentSection != null) {
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
                BackNavButton(onClick = onBack)
            }
        )
    } else {
        TopAppBar(
            title = { Text(sectionTitle) },
            navigationIcon = {
                BackNavButton(onClick = onBackToHome)
            },
            actions = {
                if (onOpenOutputDirectory != null) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text("查看输出目录") } },
                        state = rememberTooltipState()
                    ) {
                        FilledTonalIconButton(
                            onClick = onOpenOutputDirectory,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
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
    var pickMenuExpanded by remember { mutableStateOf(false) }
    var openMenuExpanded by remember { mutableStateOf(false) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { pickMenuExpanded = true },
                        shape = smoothCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("更改目录")
                    }
                    DropdownMenu(
                        expanded = pickMenuExpanded,
                        onDismissRequest = { pickMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("使用系统选择器") },
                            onClick = {
                                pickMenuExpanded = false
                                onPickDirectory()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("在文件管理中选择") },
                            onClick = {
                                pickMenuExpanded = false
                                onPickDirectoryInFileManager()
                            },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null) }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    FilledTonalButton(
                        onClick = { openMenuExpanded = true },
                        shape = smoothCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("打开目录")
                    }
                    DropdownMenu(
                        expanded = openMenuExpanded,
                        onDismissRequest = { openMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("在其他应用中打开") },
                            onClick = {
                                openMenuExpanded = false
                                onOpenDirectory()
                            },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("在文件管理中查看") },
                            onClick = {
                                openMenuExpanded = false
                                onOpenInFileManager()
                            },
                            leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) }
                        )
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
internal fun ToolPageColumn(content: @Composable ColumnScope.() -> Unit) {
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
internal fun ToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expandedByDefault: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(expandedByDefault) }

    Card(
        shape = smoothCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.animateContentSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                content()
            }
        }
    }
}

@Composable
internal fun DirectorySelectionActions(
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
internal fun CurrentPathPanel(path: String) {
    Surface(
        shape = smoothCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
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


