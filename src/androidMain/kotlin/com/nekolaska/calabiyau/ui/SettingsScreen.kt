package com.nekolaska.calabiyau.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nekolaska.calabiyau.LocalSeedColor
import com.nekolaska.calabiyau.LocalThemeMode
import com.nekolaska.calabiyau.data.AppPrefs
import com.nekolaska.calabiyau.data.WallpaperApi
import com.nekolaska.calabiyau.data.WikiEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var savePath by remember { mutableStateOf(AppPrefs.savePath) }
    var maxConcurrency by remember { mutableStateOf(AppPrefs.maxConcurrency.toString()) }
    var showAbout by remember { mutableStateOf(false) }
    var themeMode by remember { mutableIntStateOf(AppPrefs.themeMode) }
    val globalThemeMode = LocalThemeMode.current
    var seedColorInt by remember { mutableIntStateOf(AppPrefs.customSeedColor) }
    val globalSeedColor = LocalSeedColor.current
    var wikiCacheMode by remember { mutableIntStateOf(AppPrefs.wikiCacheMode) }
    var wikiDesktopMode by remember { mutableStateOf(AppPrefs.wikiDesktopMode) }
    var bottomBarStyle by remember { mutableIntStateOf(AppPrefs.bottomBarStyle) }
    var liquidGlassEnabled by remember { mutableStateOf(AppPrefs.liquidGlassEnabled) }
    val globalLiquidGlass = LocalLiquidGlassEnabled.current
    var g2CornersEnabled by remember { mutableStateOf(AppPrefs.g2CornersEnabled) }
    val globalG2Corners = LocalG2CornersEnabled.current

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

            // ═══════════════════════════════════
            //  外观
            // ═══════════════════════════════════
            SettingsGroupHeader("外观")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = smoothCornerShape(24.dp),
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
                                                .clip(smoothCornerShape(12.dp))
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
                            shape = smoothCornerShape(28.dp),
                            confirmButton = {}
                        )
                    }

                    // 主题色
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ThemeColorPicker(
                        currentSeedColor = seedColorInt,
                        onColorSelected = { argb ->
                            seedColorInt = argb
                            AppPrefs.customSeedColor = argb
                            globalSeedColor.intValue = argb
                        }
                    )

                    // G2 连续圆角
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleItem(
                        icon = Icons.Outlined.RoundedCorner,
                        title = "G2 连续圆角",
                        subtitle = "更平滑的圆角曲线",
                        checked = g2CornersEnabled || liquidGlassEnabled,
                        onCheckedChange = {
                            if (!liquidGlassEnabled) {
                                // 液态玻璃关闭时，G2 可独立切换
                                g2CornersEnabled = it
                                AppPrefs.g2CornersEnabled = it
                                globalG2Corners.value = it
                            }
                            // 液态玻璃开启时，G2 强制开启不可关闭
                        }
                    )

                    // 液态玻璃效果（开启时自动开启 G2）
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleItem(
                        icon = Icons.Outlined.BlurOn,
                        title = "液态玻璃效果",
                        subtitle = "壁纸背景 + 玻璃卡片，需 Android 12+",
                        checked = liquidGlassEnabled,
                        onCheckedChange = {
                            liquidGlassEnabled = it
                            AppPrefs.liquidGlassEnabled = it
                            globalLiquidGlass.value = it
                            if (it) {
                                // 开启液态玻璃时自动开启 G2
                                g2CornersEnabled = true
                                AppPrefs.g2CornersEnabled = true
                                globalG2Corners.value = true
                            }
                        }
                    )

                    // ── 壁纸管理（仅液态玻璃开启时显示） ──
                    if (liquidGlassEnabled) {
                        var isRefreshing by remember { mutableStateOf(false) }
                        var isSaving by remember { mutableStateOf(false) }
                        var wallpaperMessage by remember { mutableStateOf<String?>(null) }
                        var wallpaperAutoRefresh by remember { mutableStateOf(AppPrefs.wallpaperAutoRefresh) }
                        val scope = rememberCoroutineScope()

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsToggleItem(
                            icon = Icons.Outlined.Autorenew,
                            title = "启动时自动刷新壁纸",
                            subtitle = if (wallpaperAutoRefresh) "每次进入首页随机更换"
                                else "仅手动刷新（保持当前壁纸）",
                            checked = wallpaperAutoRefresh,
                            onCheckedChange = {
                                wallpaperAutoRefresh = it
                                AppPrefs.wallpaperAutoRefresh = it
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsItem(
                            icon = Icons.Outlined.Refresh,
                            title = "刷新首页背景图",
                            subtitle = if (isRefreshing) "正在获取新壁纸…"
                                else wallpaperMessage ?: "随机更换一张 Wiki 壁纸",
                            onClick = {
                                if (isRefreshing) return@SettingsItem
                                isRefreshing = true
                                wallpaperMessage = null
                                scope.launch {
                                    val url = withContext(Dispatchers.IO) {
                                        WallpaperApi.fetchRandomWallpaperUrl(forceRefresh = true)
                                    }
                                    wallpaperMessage = if (url != null) "已刷新，返回首页查看"
                                        else "获取失败，请检查网络"
                                    isRefreshing = false
                                }
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsItem(
                            icon = Icons.Outlined.SaveAlt,
                            title = "保存当前背景图",
                            subtitle = if (isSaving) "正在保存…"
                                else "将当前壁纸保存到下载目录",
                            onClick = {
                                val currentUrl = AppPrefs.wallpaperUrl
                                if (currentUrl.isNullOrBlank() || isSaving) return@SettingsItem
                                isSaving = true
                                scope.launch {
                                    val success = withContext(Dispatchers.IO) {
                                        try {
                                            val fileName = currentUrl.substringAfterLast("/")
                                                .substringBefore("?")
                                                .ifBlank { "wallpaper_${System.currentTimeMillis()}.jpg" }
                                            val saveDir = File(AppPrefs.savePath)
                                            if (!saveDir.exists()) saveDir.mkdirs()
                                            val destFile = File(saveDir, fileName)
                                            val request = okhttp3.Request.Builder()
                                                .url(currentUrl).build()
                                            WikiEngine.client.newCall(request).execute().use { resp ->
                                                if (!resp.isSuccessful) return@withContext false
                                                resp.body.byteStream().use { input ->
                                                    destFile.outputStream().use { output ->
                                                        input.copyTo(output)
                                                    }
                                                }
                                            }
                                            true
                                        } catch (_: Exception) {
                                            false
                                        }
                                    }
                                    wallpaperMessage = if (success) "已保存到 ${AppPrefs.savePath}"
                                        else "保存失败"
                                    isSaving = false
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ═══════════════════════════════════
            //  下载
            // ═══════════════════════════════════
            SettingsGroupHeader("下载")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = smoothCornerShape(24.dp),
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

                    // 手动输入路径
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
                                    shape = smoothCornerShape(16.dp)
                                )
                            },
                            shape = smoothCornerShape(28.dp),
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

                    // 最大并发数
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
                                        shape = smoothCornerShape(16.dp)
                                    )
                                }
                            },
                            shape = smoothCornerShape(28.dp),
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
                        subtitle = barStyleName,
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
                                                .clip(smoothCornerShape(12.dp))
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
                            shape = smoothCornerShape(28.dp),
                            confirmButton = {}
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ═══════════════════════════════════
            //  Wiki
            // ═══════════════════════════════════
            SettingsGroupHeader("Wiki")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = smoothCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    // 缓存策略
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

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // 桌面模式
                    SettingsToggleItem(
                        icon = Icons.Outlined.DesktopWindows,
                        title = "桌面模式",
                        subtitle = if (wikiDesktopMode) "使用桌面版 User-Agent" else "使用移动版（默认）",
                        checked = wikiDesktopMode,
                        onCheckedChange = {
                            wikiDesktopMode = it
                            AppPrefs.wikiDesktopMode = it
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // 清除 Cookie
                    var showClearCookieDialog by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Outlined.DeleteSweep,
                        title = "清除登录状态",
                        subtitle = "清除 Wiki Cookie 并登出",
                        onClick = { showClearCookieDialog = true }
                    )
                    if (showClearCookieDialog) {
                        AlertDialog(
                            onDismissRequest = { showClearCookieDialog = false },
                            title = { Text("清除登录状态") },
                            text = { Text("确定要清除 Wiki 登录状态吗？\n清除后需要重新登录才能使用投票等功能。") },
                            shape = smoothCornerShape(28.dp),
                            confirmButton = {
                                FilledTonalButton(onClick = {
                                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                                    android.webkit.CookieManager.getInstance().flush()
                                    showClearCookieDialog = false
                                }) { Text("确定清除") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearCookieDialog = false }) { Text("取消") }
                            }
                        )
                    }

                }
            }

            Spacer(Modifier.height(8.dp))

            // 存储统计
            SettingsGroupHeader("存储")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = smoothCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    StorageStatisticsCard(savePath = savePath)

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    WebViewCacheItem(context = context)
                }
            }

            Spacer(Modifier.height(16.dp))

            // 关于分组
            SettingsGroupHeader("关于")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = smoothCornerShape(24.dp),
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
            .clip(smoothCornerShape(16.dp))
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

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(smoothCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
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
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

// ─────────────────────── 主题色选择器 ───────────────────────

/** 预设主题色 */
private val PRESET_COLORS = listOf(
    0 to "系统默认",                          // 0 = 跟随系统动态取色
    0xFF4285F4.toInt() to "蓝色",             // Google Blue
    0xFF0F9D58.toInt() to "绿色",             // Google Green
    0xFFDB4437.toInt() to "红色",             // Google Red
    0xFFF4B400.toInt() to "琥珀",             // Google Yellow
    0xFF9C27B0.toInt() to "紫色",             // Purple
    0xFF00BCD4.toInt() to "青色",             // Cyan
    0xFFFF5722.toInt() to "橙色",             // Deep Orange
    0xFF607D8B.toInt() to "蓝灰",            // Blue Grey
    0xFFE91E63.toInt() to "粉色",             // Pink
    0xFF3F51B5.toInt() to "靛蓝",             // Indigo
    0xFF009688.toInt() to "蓝绿",             // Teal
    0xFF795548.toInt() to "棕色",             // Brown
)

@Composable
private fun ThemeColorPicker(
    currentSeedColor: Int,
    onColorSelected: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val currentName = PRESET_COLORS.firstOrNull { it.first == currentSeedColor }?.second
        ?: if (currentSeedColor == 0) "系统默认" else "自定义"

    SettingsItem(
        icon = Icons.Outlined.ColorLens,
        title = "主题色",
        subtitle = currentName,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        // 自定义颜色的 HSV 状态
        val initHsv = remember {
            FloatArray(3).also { hsv ->
                if (currentSeedColor != 0) {
                    android.graphics.Color.colorToHSV(currentSeedColor, hsv)
                } else {
                    hsv[0] = 210f; hsv[1] = 0.7f; hsv[2] = 0.8f
                }
            }
        }
        var hue by remember { mutableFloatStateOf(initHsv[0]) }
        var saturation by remember { mutableFloatStateOf(initHsv[1]) }
        var value by remember { mutableFloatStateOf(initHsv[2]) }
        var showCustomPicker by remember { mutableStateOf(
            currentSeedColor != 0 && PRESET_COLORS.none { it.first == currentSeedColor }
        ) }

        val customColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择主题色") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ── 预设色块网格 ──
                    val rows = PRESET_COLORS.chunked(4)
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { (argb, label) ->
                                val isSelected = argb == currentSeedColor && !showCustomPicker
                                val displayColor = if (argb == 0) MaterialTheme.colorScheme.primary
                                else Color(argb)

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(smoothCornerShape(14.dp))
                                        .clickable {
                                            showCustomPicker = false
                                            onColorSelected(argb)
                                            showDialog = false
                                        }
                                        .then(
                                            if (isSelected) Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                                smoothCornerShape(14.dp)
                                            ) else Modifier
                                        )
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(displayColor)
                                            .then(
                                                if (argb == 0) Modifier.border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outline,
                                                    CircleShape
                                                ) else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "已选",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        if (argb == 0 && !isSelected) {
                                            Icon(
                                                Icons.Outlined.Palette,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }

                    // ── 自定义颜色展开按钮 ──
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(smoothCornerShape(12.dp))
                            .clickable { showCustomPicker = !showCustomPicker }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "自定义颜色",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (showCustomPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // ── 自定义颜色选择器 ──
                    if (showCustomPicker) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // 颜色预览
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(smoothCornerShape(16.dp))
                                    .background(customColor),
                                contentAlignment = Alignment.Center
                            ) {
                                val hexStr = String.format("#%06X", customColor.toArgb() and 0xFFFFFF)
                                Text(
                                    hexStr,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // 色相滑块
                            Text("色相", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(
                                value = hue,
                                onValueChange = { hue = it },
                                valueRange = 0f..360f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // 饱和度滑块
                            Text("饱和度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(
                                value = saturation,
                                onValueChange = { saturation = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // 明度滑块
                            Text("明度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(
                                value = value,
                                onValueChange = { value = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            shape = smoothCornerShape(28.dp),
            confirmButton = {
                if (showCustomPicker) {
                    FilledTonalButton(onClick = {
                        onColorSelected(customColor.toArgb())
                        showDialog = false
                    }) {
                        Text("应用自定义颜色")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
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

/** WebView 缓存统计 + 清除按钮 */
@Composable
private fun WebViewCacheItem(context: android.content.Context) {
    var cacheSize by remember { mutableStateOf<Long?>(null) }
    var isCalculating by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        isCalculating = true
        withContext(Dispatchers.IO) {
            cacheSize = calculateWebViewCacheSize(context)
        }
        isCalculating = false
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(smoothCornerShape(16.dp))
            .clickable {
                android.webkit.WebView(context).apply {
                    clearCache(true)
                    clearHistory()
                    destroy()
                }
                refreshKey++
            }
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
                    Icons.Outlined.CleaningServices, null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "清除 WebView 缓存",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = when {
                    isCalculating -> "计算中…"
                    cacheSize != null && cacheSize!! > 0 -> "当前占用 ${formatFileSize(cacheSize!!)}"
                    else -> "无缓存"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (isCalculating) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 计算 WebView 缓存目录大小 */
private fun calculateWebViewCacheSize(context: android.content.Context): Long {
    var total = 0L
    val dataDir = context.dataDir
    // WebView 缓存可能在以下目录
    val cacheDirs = listOf(
        File(dataDir, "app_webview/Cache"),
        File(dataDir, "app_webview/Default/Cache"),
        File(dataDir, "app_webview/Default/Code Cache"),
        File(dataDir, "app_webview/GPUCache"),
        context.cacheDir  // 通用缓存目录
    )
    for (dir in cacheDirs) {
        if (dir.exists() && dir.isDirectory) {
            total += dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }
    return total
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
