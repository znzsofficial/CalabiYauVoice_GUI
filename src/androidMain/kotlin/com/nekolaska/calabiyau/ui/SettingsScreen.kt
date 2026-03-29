package com.nekolaska.calabiyau.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.LocalThemeMode
import com.nekolaska.calabiyau.data.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var savePath by remember { mutableStateOf(AppPrefs.savePath) }
    var maxConcurrency by remember { mutableStateOf(AppPrefs.maxConcurrency.toString()) }
    var showAbout by remember { mutableStateOf(false) }
    var themeMode by remember { mutableIntStateOf(AppPrefs.themeMode) }
    val globalThemeMode = LocalThemeMode.current
    var wikiCacheMode by remember { mutableIntStateOf(AppPrefs.wikiCacheMode) }
    var bottomBarStyle by remember { mutableIntStateOf(AppPrefs.bottomBarStyle) }

    val context = LocalContext.current

    // SAF 目录选择器
    val dirPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // 获取实际路径或使用 URI 的 path 部分
            val path = getPathFromUri(context, it)
            if (path != null) {
                savePath = path
                AppPrefs.savePath = path
            }
        }
    }

    // 拦截返回键：关于 > 设置 > 主界面
    BackHandler(enabled = showAbout) {
        showAbout = false
    }
    BackHandler(enabled = !showAbout) {
        onBack()
    }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "设置",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))

            // 下载设置分组
            SettingsGroupHeader("下载设置")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    // 保存路径
                    SettingsItem(
                        icon = Icons.Outlined.Folder,
                        title = "保存路径",
                        subtitle = savePath,
                        onClick = { dirPicker.launch(null) }
                    )

                    // 保存路径手动输入
                    var showPathDialog by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Outlined.Edit,
                        title = "手动设置路径",
                        subtitle = "手动输入自定义保存路径",
                        onClick = { showPathDialog = true }
                    )

                    if (showPathDialog) {
                        var tempPath by remember { mutableStateOf(savePath) }
                        AlertDialog(
                            onDismissRequest = { showPathDialog = false },
                            title = { Text("设置保存路径") },
                            text = {
                                OutlinedTextField(
                                    value = tempPath,
                                    onValueChange = { tempPath = it },
                                    label = { Text("路径") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    maxLines = 3,
                                    shape = RoundedCornerShape(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(28.dp),
                            confirmButton = {
                                FilledTonalButton(onClick = {
                                    savePath = tempPath
                                    AppPrefs.savePath = tempPath
                                    showPathDialog = false
                                }) { Text("确定") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPathDialog = false }) { Text("取消") }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 性能设置
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    // 最大并发数
                    var showConcurrencyDialog by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Outlined.Speed,
                        title = "最大并发下载数",
                        subtitle = "$maxConcurrency 个并发连接",
                        onClick = { showConcurrencyDialog = true }
                    )

                    if (showConcurrencyDialog) {
                        var tempConcurrency by remember { mutableStateOf(maxConcurrency) }
                        AlertDialog(
                            onDismissRequest = { showConcurrencyDialog = false },
                            title = { Text("最大并发下载数") },
                            text = {
                                Column {
                                    Text(
                                        "设置同时下载文件的最大数量（1-32）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = tempConcurrency,
                                        onValueChange = { tempConcurrency = it.filter { c -> c.isDigit() } },
                                        label = { Text("并发数") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(28.dp),
                            confirmButton = {
                                FilledTonalButton(onClick = {
                                    val value = tempConcurrency.toIntOrNull()?.coerceIn(1, 32) ?: 8
                                    maxConcurrency = value.toString()
                                    AppPrefs.maxConcurrency = value
                                    showConcurrencyDialog = false
                                }) { Text("确定") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showConcurrencyDialog = false }) { Text("取消") }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 外观设置
            SettingsGroupHeader("外观")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    var showThemeDialog by remember { mutableStateOf(false) }
                    val themeName = when (themeMode) {
                        AppPrefs.THEME_LIGHT -> "浅色"
                        AppPrefs.THEME_DARK -> "深色"
                        else -> "跟随系统"
                    }
                    SettingsItem(
                        icon = Icons.Outlined.Palette,
                        title = "主题模式",
                        subtitle = themeName,
                        onClick = { showThemeDialog = true }
                    )

                    if (showThemeDialog) {
                        AlertDialog(
                            onDismissRequest = { showThemeDialog = false },
                            title = { Text("选择主题") },
                            text = {
                                Column {
                                    listOf(
                                        AppPrefs.THEME_SYSTEM to "跟随系统",
                                        AppPrefs.THEME_LIGHT to "浅色模式",
                                        AppPrefs.THEME_DARK to "深色模式"
                                    ).forEach { (mode, label) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    themeMode = mode
                                                    AppPrefs.themeMode = mode
                                                    globalThemeMode.intValue = mode
                                                    showThemeDialog = false
                                                }
                                                .padding(vertical = 12.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = themeMode == mode,
                                                onClick = {
                                                    themeMode = mode
                                                    AppPrefs.themeMode = mode
                                                    globalThemeMode.intValue = mode
                                                    showThemeDialog = false
                                                }
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(label, style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(28.dp),
                            confirmButton = {}
                        )
                    }

                    // 底栏样式
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    var showBarStyleDialog by remember { mutableStateOf(false) }
                    val barStyleName = when (bottomBarStyle) {
                        AppPrefs.BAR_STYLE_DOCKED_TOOLBAR -> "悬浮工具栏"
                        else -> "经典导航栏"
                    }
                    SettingsItem(
                        icon = Icons.Outlined.ViewDay,
                        title = "底栏样式",
                        subtitle = "$barStyleName（重启页面生效）",
                        onClick = { showBarStyleDialog = true }
                    )

                    if (showBarStyleDialog) {
                        AlertDialog(
                            onDismissRequest = { showBarStyleDialog = false },
                            title = { Text("底栏样式") },
                            text = {
                                Column {
                                    listOf(
                                        AppPrefs.BAR_STYLE_DOCKED_TOOLBAR to "悬浮工具栏",
                                        AppPrefs.BAR_STYLE_BOTTOM_APP_BAR to "经典导航栏"
                                    ).forEach { (style, label) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    bottomBarStyle = style
                                                    AppPrefs.bottomBarStyle = style
                                                    showBarStyleDialog = false
                                                }
                                                .padding(vertical = 12.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = bottomBarStyle == style,
                                                onClick = {
                                                    bottomBarStyle = style
                                                    AppPrefs.bottomBarStyle = style
                                                    showBarStyleDialog = false
                                                }
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(label, style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(28.dp),
                            confirmButton = {}
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Wiki 设置
            SettingsGroupHeader("Wiki")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    val cacheName = when (wikiCacheMode) {
                        AppPrefs.WIKI_CACHE_OFFLINE_FIRST -> "优先使用缓存（弱网可用）"
                        else -> "默认（联网加载）"
                    }
                    SettingsItem(
                        icon = Icons.Outlined.OfflinePin,
                        title = "Wiki 缓存策略",
                        subtitle = cacheName,
                        onClick = {
                            val newMode = if (wikiCacheMode == AppPrefs.WIKI_CACHE_DEFAULT)
                                AppPrefs.WIKI_CACHE_OFFLINE_FIRST else AppPrefs.WIKI_CACHE_DEFAULT
                            wikiCacheMode = newMode
                            AppPrefs.wikiCacheMode = newMode
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 存储统计
            SettingsGroupHeader("存储")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                StorageStatisticsCard(savePath = savePath)
            }

            Spacer(Modifier.height(16.dp))

            // 关于分组
            SettingsGroupHeader("关于")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    SettingsItem(
                        icon = Icons.Outlined.Info,
                        title = "关于",
                        subtitle = "版本信息与版权声明",
                        onClick = { showAbout = true }
                    )

                    SettingsItem(
                        icon = Icons.Outlined.Code,
                        title = "开源仓库",
                        subtitle = "github.com/znzsofficial/CalabiYauVoice_GUI",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW,
                                "https://github.com/znzsofficial/CalabiYauVoice_GUI".toUri())
                            context.startActivity(intent)
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 10.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ─────────────────────── 存储统计 ───────────────────────

data class DirSizeInfo(
    val name: String,
    val size: Long
)

@Composable
private fun StorageStatisticsCard(savePath: String) {
    var totalSize by remember { mutableStateOf<Long?>(null) }
    var subDirSizes by remember { mutableStateOf<List<DirSizeInfo>>(emptyList()) }
    var fileCount by remember { mutableIntStateOf(0) }
    var isCalculating by remember { mutableStateOf(true) }

    // 计算目录大小
    LaunchedEffect(savePath) {
        isCalculating = true
        withContext(Dispatchers.IO) {
            try {
                val root = File(savePath)
                if (root.exists() && root.isDirectory) {
                    totalSize = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    fileCount = root.walkTopDown().count { it.isFile }
                    subDirSizes = root.listFiles()
                        ?.filter { it.isDirectory }
                        ?.map { dir ->
                            DirSizeInfo(
                                name = dir.name,
                                size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                            )
                        }
                        ?.filter { it.size > 0 }
                        ?.sortedByDescending { it.size }
                        ?.take(8) // 最多显示 8 个子目录
                        ?: emptyList()
                } else {
                    totalSize = 0L
                    fileCount = 0
                    subDirSizes = emptyList()
                }
            } catch (_: Exception) {
                totalSize = 0L
            }
        }
        isCalculating = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Storage, null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "已用空间",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (isCalculating) {
                    Text(
                        "计算中...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "${formatFileSize(totalSize ?: 0)}  ·  $fileCount 个文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isCalculating) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        // 子目录明细
        if (!isCalculating && subDirSizes.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(12.dp))

            subDirSizes.forEach { dirInfo ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Folder,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = dirInfo.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatFileSize(dirInfo.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

/**
 * 从 SAF 返回的 URI 中提取可读路径
 */
private fun getPathFromUri(context: android.content.Context, uri: Uri): String? {
    // 对于 content:// URI，尝试解析实际路径
    val docId = try {
        android.provider.DocumentsContract.getTreeDocumentId(uri)
    } catch (_: Exception) {
        return uri.path
    }

    // 常见的 ExternalStorageProvider 格式: "primary:some/path"
    val split = docId.split(":")
    return when {
        split.size == 2 && split[0].equals("primary", ignoreCase = true) -> {
            "${android.os.Environment.getExternalStorageDirectory().absolutePath}/${split[1]}"
        }
        split.size == 2 -> {
            // 外部 SD 卡等
            "/storage/${split[0]}/${split[1]}"
        }
        else -> uri.path
    }
}
