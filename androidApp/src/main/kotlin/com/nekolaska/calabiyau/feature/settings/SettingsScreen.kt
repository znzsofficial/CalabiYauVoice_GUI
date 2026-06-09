package com.nekolaska.calabiyau.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import coil3.SingletonImageLoader
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.launcher.LauncherIconTheme
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.core.ui.LocalLiquidGlassEnabled
import com.nekolaska.calabiyau.core.ui.LocalSeedColor
import com.nekolaska.calabiyau.core.ui.LocalThemeMode
import com.nekolaska.calabiyau.core.ui.LocalWallpaperSeedColor
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.feature.tools.formatFileSize
import com.nekolaska.calabiyau.feature.tools.getPathFromUri
import com.nekolaska.calabiyau.feature.wiki.gallery.WallpaperApi
import com.nekolaska.calabiyau.feature.wiki.hub.QuickEntry
import com.nekolaska.calabiyau.feature.wiki.hub.WikiWebViewScreen
import com.nekolaska.calabiyau.feature.wiki.hub.allQuickEntries
import com.nekolaska.calabiyau.feature.wiki.hub.defaultQuickEntryIds
import com.nekolaska.calabiyau.feature.wiki.hub.quickEntryById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

private enum class SettingsPage {
    MAIN,
    ABOUT,
    STORAGE,
    UPDATE_WEB
}

private const val SHOW_SETTINGS_DEBUG_ITEM = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var savePath by remember { mutableStateOf(AppPrefs.savePath) }
    var maxConcurrency by remember { mutableStateOf(AppPrefs.maxConcurrency.toString()) }
    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }
    // 全局状态（单一数据源：CompositionLocal）
    val globalThemeMode = LocalThemeMode.current
    val globalSeedColor = LocalSeedColor.current
    val globalLiquidGlass = LocalLiquidGlassEnabled.current

    // 直接从全局状态派生，避免三重同步
    var themeMode by globalThemeMode
    var seedColorInt by globalSeedColor
    var liquidGlassEnabled by globalLiquidGlass
    var highReadabilityDrawer by remember { mutableStateOf(AppPrefs.highReadabilityDrawer) }
    var launcherIconTheme by remember { mutableIntStateOf(AppPrefs.launcherIconTheme) }

    // 这些没有对应的 CompositionLocal，保留本地 remember
    var wikiCacheMode by remember { mutableIntStateOf(AppPrefs.wikiCacheMode) }
    var wikiDesktopMode by remember { mutableStateOf(AppPrefs.wikiDesktopMode) }
    var bottomBarStyle by remember { mutableIntStateOf(AppPrefs.bottomBarStyle) }
    var homeQuickEntryLayout by remember { mutableIntStateOf(AppPrefs.homeQuickEntryLayout) }
    var homeQuickEntryIds by remember {
        mutableStateOf(
            AppPrefs.homeQuickEntryIds.takeIf { it.isNotEmpty() } ?: defaultQuickEntryIds
        )
    }
    var showQuickEntrySheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var storageSnapshot by remember { mutableStateOf<StorageSnapshot?>(null) }
    var isStorageCalculating by remember { mutableStateOf(true) }
    var storageRefreshKey by remember { mutableIntStateOf(0) }
    var updateWebUrl by remember { mutableStateOf<String?>(null) }

    fun refreshStorageSnapshot() {
        storageRefreshKey++
    }

    LaunchedEffect(savePath, storageRefreshKey) {
        isStorageCalculating = true
        storageSnapshot = withContext(Dispatchers.IO) { computeStorageSnapshot(context, savePath) }
        isStorageCalculating = false
    }

    // SAF 目录选择器
    val dirPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // 获取实际路径或使用 URI 的 path 部分
            val path = getPathFromUri(it)
            if (path != null) {
                savePath = path
                AppPrefs.savePath = path
            }
        }
    }

    // 拦截返回键：二级页 > 设置 > 主界面
    BackHandler(enabled = currentPage != SettingsPage.MAIN) {
        currentPage = SettingsPage.MAIN
    }
    BackHandler(enabled = currentPage == SettingsPage.MAIN) {
        onBack()
    }

    val settingsScrollState = rememberScrollState()

    val animDuration = 300
    AnimatedContent(
        targetState = currentPage,
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        transitionSpec = {
            if (targetState != SettingsPage.MAIN) {
                // 进入二级页：从右滑入 + 淡入
                (slideInHorizontally(tween(animDuration)) { it / 4 } + fadeIn(tween(animDuration)))
                    .togetherWith(slideOutHorizontally(tween(animDuration)) { -it / 4 } + fadeOut(tween(animDuration / 2)))
            } else {
                // 返回设置：从左滑入 + 淡入
                (slideInHorizontally(tween(animDuration)) { -it / 4 } + fadeIn(tween(animDuration)))
                    .togetherWith(slideOutHorizontally(tween(animDuration)) { it / 4 } + fadeOut(tween(animDuration / 2)))
            }
        },
        label = "SettingsAboutTransition"
    ) { page ->
        if (page == SettingsPage.ABOUT) {
            AboutScreen(onBack = { currentPage = SettingsPage.MAIN })
            return@AnimatedContent
        }
        if (page == SettingsPage.STORAGE) {
            StorageSettingsScreen(
                savePath = savePath,
                snapshot = storageSnapshot,
                isCalculating = isStorageCalculating,
                onRefreshSnapshot = ::refreshStorageSnapshot,
                onBack = { currentPage = SettingsPage.MAIN }
            )
            return@AnimatedContent
        }
        if (page == SettingsPage.UPDATE_WEB) {
            WikiWebViewScreen(
                onExitWiki = { currentPage = SettingsPage.MAIN },
                initialUrl = updateWebUrl ?: "https://wiki.nekolaska.vip",
                useTopBarMode = true
            )
            return@AnimatedContent
        }

        val showSnack = rememberSnackbarLauncher()

        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = AppSpacing.screen, vertical = AppSpacing.medium),
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
                    Spacer(Modifier.width(AppSpacing.iconGap))
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
                    .verticalScroll(settingsScrollState)
            ) {
                Spacer(Modifier.height(AppSpacing.xSmall))

                // ═══════════════════════════════════
                //  外观
                // ═══════════════════════════════════
                SettingsGroupHeader("外观")

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.screen),
                    shape = AppShapes.card,
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
                                                        showThemeDialog = false
                                                    }
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                            }
                                        }
                                    }
                                },
                                shape = AppShapes.dialog,
                                confirmButton = {}
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
                        var showLauncherIconDialog by remember { mutableStateOf(false) }
                        val launcherIconName = when (launcherIconTheme) {
                            AppPrefs.LAUNCHER_ICON_SYSTEM -> "系统强调色"
                            else -> "品牌深色"
                        }
                        SettingsItem(
                            icon = Icons.Outlined.Apps,
                            title = "应用图标",
                            subtitle = launcherIconName,
                            onClick = { showLauncherIconDialog = true }
                        )
                        if (showLauncherIconDialog) {
                            AlertDialog(
                                onDismissRequest = { showLauncherIconDialog = false },
                                title = { Text("应用图标") },
                                text = {
                                    Column {
                                        Text(
                                            "切换后桌面图标可能需要几秒刷新，部分启动器会缓存旧图标。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(AppSpacing.medium))
                                        listOf(
                                            AppPrefs.LAUNCHER_ICON_BRAND to "品牌深色",
                                            AppPrefs.LAUNCHER_ICON_SYSTEM to "系统强调色"
                                        ).forEach { (theme, label) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(smoothCornerShape(12.dp))
                                                    .clickable {
                                                        launcherIconTheme = theme
                                                        AppPrefs.launcherIconTheme = theme
                                                        LauncherIconTheme.apply(context, theme)
                                                        showLauncherIconDialog = false
                                                        showSnack("已切换应用图标")
                                                    }
                                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = launcherIconTheme == theme,
                                                    onClick = {
                                                        launcherIconTheme = theme
                                                        AppPrefs.launcherIconTheme = theme
                                                        LauncherIconTheme.apply(context, theme)
                                                        showLauncherIconDialog = false
                                                        showSnack("已切换应用图标")
                                                    }
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                            }
                                        }
                                    }
                                },
                                shape = AppShapes.dialog,
                                confirmButton = {}
                            )
                        }

                        // 主题色
                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
                        ThemeColorPicker(
                            currentSeedColor = seedColorInt,
                            onColorSelected = { argb ->
                                seedColorInt = argb
                                AppPrefs.customSeedColor = argb
                            }
                        )

                        // 液态玻璃效果
                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
                        SettingsToggleItem(
                            icon = Icons.Outlined.BlurOn,
                            title = "液态玻璃效果",
                            subtitle = "需 Android 12+",
                            checked = liquidGlassEnabled,
                            onCheckedChange = {
                                liquidGlassEnabled = it
                                AppPrefs.liquidGlassEnabled = it
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
                        SettingsToggleItem(
                            icon = Icons.Outlined.Visibility,
                            title = "高可读性侧栏",
                            subtitle = "增强液态玻璃侧栏文字和选中项对比",
                            checked = highReadabilityDrawer,
                            onCheckedChange = {
                                highReadabilityDrawer = it
                                AppPrefs.highReadabilityDrawer = it
                            }
                        )

                        // ── 壁纸管理 ──
                        var isRefreshing by remember { mutableStateOf(false) }
                        var isSaving by remember { mutableStateOf(false) }
                        var wallpaperMessage by remember { mutableStateOf<String?>(null) }
                        var wallpaperAutoRefresh by remember { mutableStateOf(AppPrefs.wallpaperAutoRefresh) }
                        val scope = rememberCoroutineScope()

                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
                        SettingsToggleItem(
                            icon = Icons.Outlined.Autorenew,
                            title = "自动刷新壁纸",
                            subtitle = "启动时随机更换首页背景图",
                            checked = wallpaperAutoRefresh,
                            onCheckedChange = {
                                wallpaperAutoRefresh = it
                                AppPrefs.wallpaperAutoRefresh = it
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
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

                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
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
                                            val request = Request.Builder()
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

                Spacer(Modifier.height(AppSpacing.itemGap))

                // ═══════════════════════════════════
                //  首页快捷入口
                // ═══════════════════════════════════
                SettingsGroupHeader("首页")

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.screen),
                    shape = AppShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column {
                        var showQuickLayoutDialog by remember { mutableStateOf(false) }
                        SettingsItem(
                            icon = Icons.Outlined.GridView,
                            title = "快捷入口样式",
                            subtitle = when (homeQuickEntryLayout) {
                                AppPrefs.HOME_QUICK_LAYOUT_BUTTONS -> "六按钮"
                                else -> "网格大卡（默认）"
                            },
                            onClick = { showQuickLayoutDialog = true }
                        )
                        if (showQuickLayoutDialog) {
                            AlertDialog(
                                onDismissRequest = { showQuickLayoutDialog = false },
                                title = { Text("快捷入口样式") },
                                text = {
                                    Column {
                                        listOf(
                                            AppPrefs.HOME_QUICK_LAYOUT_GRID to "网格大卡（默认）",
                                            AppPrefs.HOME_QUICK_LAYOUT_BUTTONS to "六按钮"
                                        ).forEach { (layout, label) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(smoothCornerShape(12.dp))
                                                    .clickable {
                                                        homeQuickEntryLayout = layout
                                                        AppPrefs.homeQuickEntryLayout = layout
                                                        showQuickLayoutDialog = false
                                                    }
                                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = homeQuickEntryLayout == layout,
                                                    onClick = {
                                                        homeQuickEntryLayout = layout
                                                        AppPrefs.homeQuickEntryLayout = layout
                                                        showQuickLayoutDialog = false
                                                    }
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                            }
                                        }
                                    }
                                },
                                shape = AppShapes.dialog,
                                confirmButton = {}
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
                        SettingsItem(
                            icon = Icons.Outlined.SpaceDashboard,
                            title = "顶部六入口",
                            subtitle = homeQuickEntryIds
                                .mapNotNull(quickEntryById::get)
                                .joinToString(" · ") { it.label }
                                .ifBlank { "默认" },
                            onClick = { showQuickEntrySheet = true }
                        )
                    }
                }

                Spacer(Modifier.height(AppSpacing.itemGap))

                // ═══════════════════════════════════
                //  下载
                // ═══════════════════════════════════
                SettingsGroupHeader("下载")

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.screen),
                    shape = AppShapes.card,
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
                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
                        var showPathDialog by remember { mutableStateOf(false) }
                        SettingsItem(
                            icon = Icons.Outlined.Edit,
                            title = "手动填写",
                            subtitle = "直接输入自定义保存路径",
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
                                shape = AppShapes.dialog,
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
                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
                        var showConcurrencyDialog by remember { mutableStateOf(false) }
                        SettingsItem(
                            icon = Icons.Outlined.Speed,
                            title = "最大并发下载数",
                            subtitle = "当前 $maxConcurrency 个连接并发",
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
                                        Spacer(Modifier.height(AppSpacing.large))
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
                                shape = AppShapes.dialog,
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
                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
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
                                                    .clip(smoothCornerShape(16.dp))
                                                    .clickable {
                                                        bottomBarStyle = style
                                                        AppPrefs.bottomBarStyle = style
                                                        showBarStyleDialog = false
                                                    }
                                                    .padding(vertical = 12.dp, horizontal = 12.dp),
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
                                shape = AppShapes.dialog,
                                confirmButton = {}
                            )
                        }
                    }
                }

                Spacer(Modifier.height(AppSpacing.itemGap))

                // ═══════════════════════════════════
                //  Wiki
                // ═══════════════════════════════════
                SettingsGroupHeader("Wiki")

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.screen),
                    shape = AppShapes.card,
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

                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))

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

                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))

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
                                shape = AppShapes.dialog,
                                confirmButton = {
                                    FilledTonalButton(onClick = {
                                        CookieManager.getInstance().removeAllCookies(null)
                                        CookieManager.getInstance().flush()
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

                Spacer(Modifier.height(AppSpacing.itemGap))

                // 存储统计
                SettingsGroupHeader("存储")

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.screen),
                    shape = AppShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column {
                        StorageSummaryItem(
                            snapshot = storageSnapshot,
                            onClick = { currentPage = SettingsPage.STORAGE }
                        )
                    }
                }

                Spacer(Modifier.height(AppSpacing.sectionGap))

                // 关于分组
                SettingsGroupHeader("关于")

                // ── 检查更新状态 ──
                val updateScope = rememberCoroutineScope()
                var isCheckingUpdate by remember { mutableStateOf(false) }
                var updateSubtitle by remember { mutableStateOf("点击检查新版本") }
                var updateResult by remember { mutableStateOf<UpdateApi.UpdateInfo?>(null) }

                val currentVersion = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.0.8"
                    } catch (_: Exception) {
                        "2.0.8"
                    }
                }
                val currentVersionCode = remember {
                    try {
                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        @Suppress("DEPRECATION")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            packageInfo.longVersionCode
                        } else {
                            packageInfo.versionCode.toLong()
                        }
                    } catch (_: Exception) {
                        0L
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.screen),
                    shape = AppShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column {
                        SettingsItem(
                            icon = Icons.Outlined.Info,
                            title = "关于",
                            subtitle = "版本信息与版权声明",
                            onClick = { currentPage = SettingsPage.ABOUT }
                        )

                        SettingsItem(
                            icon = if (isCheckingUpdate) Icons.Outlined.Sync else Icons.Outlined.SystemUpdate,
                            title = "检查更新",
                            subtitle = if (isCheckingUpdate) "正在检查…" else updateSubtitle,
                            onClick = {
                                if (isCheckingUpdate) return@SettingsItem
                                isCheckingUpdate = true
                                updateSubtitle = "正在检查…"
                                updateScope.launch {
                                    when (val result = UpdateApi.checkUpdate(currentVersion, currentVersionCode)) {
                                        is UpdateApi.Result.NewVersion -> {
                                            updateSubtitle = "发现新版本: ${result.info.versionName}"
                                            updateResult = result.info
                                            AppPrefs.lastUpdateCheck = System.currentTimeMillis()
                                        }

                                        is UpdateApi.Result.AlreadyLatest -> {
                                            updateSubtitle = "已是最新版本 ($currentVersion)"
                                            AppPrefs.lastUpdateCheck = System.currentTimeMillis()
                                        }

                                        is UpdateApi.Result.Error -> {
                                            updateSubtitle = result.message
                                        }
                                    }
                                    isCheckingUpdate = false
                                }
                            }
                        )

                        if (SHOW_SETTINGS_DEBUG_ITEM) {
                            SettingsDebugItem(
                                currentVersion = currentVersion,
                                context = context,
                                showSnack = showSnack
                            )
                        }
                    }
                }

                // ── 新版本对话框 ──
                updateResult?.let { info ->
                    UpdateAvailableDialog(
                        info = info,
                        currentVersion = currentVersion,
                        onDismiss = { updateResult = null },
                        onOpenBrowser = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, info.htmlUrl.toUri()))
                        },
                        onOpenInApp = {
                            updateWebUrl = info.htmlUrl
                            updateResult = null
                            currentPage = SettingsPage.UPDATE_WEB
                        }
                    )
                }

                Spacer(Modifier.height(AppSpacing.xxxLarge))
            }
        }
    } // AnimatedContent

    if (showQuickEntrySheet) {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
        ModalBottomSheet(
            onDismissRequest = { showQuickEntrySheet = false },
            sheetState = sheetState
        ) {
            QuickEntryCustomizeSheet(
                selectedIds = homeQuickEntryIds,
                onSelectedIdsChange = {
                    homeQuickEntryIds = it
                    AppPrefs.homeQuickEntryIds = it
                },
                onClose = { showQuickEntrySheet = false }
            )
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
        modifier = Modifier.padding(start = AppSpacing.xxxLarge, top = AppSpacing.cardContent, bottom = 10.dp)
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
            .padding(horizontal = AppSpacing.cardContent, vertical = AppSpacing.sectionGap),
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
        Spacer(Modifier.width(AppSpacing.sectionGap))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTextStyles.settingsItemTitle
            )
            Text(
                text = subtitle,
                style = AppTextStyles.settingsItemSubtitle,
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
private fun SettingsDebugItem(
    currentVersion: String,
    context: Context,
    showSnack: (String) -> Unit
) {
    var showDebugMenu by remember { mutableStateOf(false) }
    SettingsItem(
        icon = Icons.Outlined.BugReport,
        title = "调试信息",
        subtitle = "版本 $currentVersion · API ${Build.VERSION.SDK_INT}",
        onClick = { showDebugMenu = true }
    )

    if (!showDebugMenu) return

    var showCrashConfirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { showDebugMenu = false },
        icon = { Icon(Icons.Outlined.BugReport, null) },
        title = { Text("调试菜单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                Surface(
                    shape = smoothCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        buildString {
                            appendLine("版本: $currentVersion (${Build.VERSION.SDK_INT})")
                            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
                            appendLine("系统: Android ${Build.VERSION.RELEASE}")
                            append("保存路径: ${AppPrefs.savePath}")
                        },
                        style = AppTextStyles.codeBlock,
                        modifier = Modifier.padding(AppSpacing.large)
                    )
                }

                OutlinedButton(
                    onClick = {
                        val info = buildString {
                            appendLine("版本: $currentVersion")
                            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
                            appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                            append("保存路径: ${AppPrefs.savePath}")
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("设备信息", info))
                        showSnack("已复制")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(AppSpacing.itemGap))
                    Text("复制设备信息")
                }

                FilledTonalButton(
                    onClick = { showCrashConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Outlined.Warning, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(AppSpacing.itemGap))
                    Text("触发测试崩溃")
                }
            }
        },
        shape = AppShapes.dialog,
        confirmButton = {
            TextButton(onClick = { showDebugMenu = false }) { Text("关闭") }
        }
    )

    if (showCrashConfirm) {
        AlertDialog(
            onDismissRequest = { showCrashConfirm = false },
            icon = {
                Icon(
                    Icons.Outlined.Warning,
                    null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("确认触发崩溃？") },
            text = { Text("应用会立即关闭并显示崩溃日志页面。") },
            shape = AppShapes.dialog,
            confirmButton = {
                FilledTonalButton(
                    onClick = { throw RuntimeException("手动触发的测试崩溃 - CrashHandler 功能验证") },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showCrashConfirm = false }) { Text("取消") }
            }
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

@Composable
private fun QuickEntryCustomizeSheet(
    selectedIds: List<String>,
    onSelectedIdsChange: (List<String>) -> Unit,
    onClose: () -> Unit
) {
    val selectedEntries = remember(selectedIds) { selectedIds.mapNotNull(quickEntryById::get) }
    val remainingEntries = remember(selectedIds) {
        allQuickEntries.filterNot { candidate -> selectedIds.contains(candidate.id) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.cardContent)
            .padding(bottom = 24.dp)
    ) {
        Text(
            "顶部六按钮自定义",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(AppSpacing.xSmall))
        Text(
            "已选中的按钮会显示在 Wiki 首页顶部。可移除、补充，并调整顺序。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(AppSpacing.sectionGap))
        Text(
            "当前顺序",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))

        selectedEntries.forEachIndexed { index, entry ->
            QuickEntryEditorRow(
                entry = entry,
                index = index,
                canMoveUp = index > 0,
                canMoveDown = index < selectedEntries.lastIndex,
                onMoveUp = { onSelectedIdsChange(selectedIds.moveQuickEntry(index, index - 1)) },
                onMoveDown = { onSelectedIdsChange(selectedIds.moveQuickEntry(index, index + 1)) },
                onRemove = {
                    if (selectedIds.size <= 1) return@QuickEntryEditorRow
                    onSelectedIdsChange(selectedIds.filterNot { it == entry.id })
                }
            )
            if (index < selectedEntries.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = AppSpacing.medium),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.sectionGap))
        Text(
            "可添加项",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)) {
            items(remainingEntries, key = { it.id }) { entry ->
                AssistChip(
                    onClick = {
                        if (selectedIds.size >= 6) return@AssistChip
                        onSelectedIdsChange(selectedIds + entry.id)
                    },
                    label = { Text(entry.label) },
                    leadingIcon = {
                        Icon(entry.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.sectionGap))
        TextButton(onClick = { onSelectedIdsChange(defaultQuickEntryIds) }) {
            Text("恢复默认六按钮")
        }

        Spacer(Modifier.height(AppSpacing.xSmall))
        TextButton(onClick = onClose) {
            Text("完成")
        }
    }
}

private fun List<String>.moveQuickEntry(fromIndex: Int, toIndex: Int): List<String> {
    val mutable = toMutableList()
    mutable[fromIndex] = mutable[toIndex].also { mutable[toIndex] = mutable[fromIndex] }
    return mutable
}

@Composable
private fun QuickEntryEditorRow(
    entry: QuickEntry,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(entry.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(AppSpacing.iconGap))
        Column(Modifier.weight(1f)) {
            Text(entry.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "位置 ${index + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Default.ExpandLess, contentDescription = "上移")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Default.ExpandMore, contentDescription = "下移")
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Close, contentDescription = "移除")
        }
    }
}

// ─────────────────────── 主题色选择器 ───────────────────────

/** 预设主题色 */
private val PRESET_COLORS = listOf(
    AppPrefs.SEED_WALLPAPER to "跟随背景图",  // -1 = 从壁纸提取主题色
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
    val wallpaperSeedArgb = LocalWallpaperSeedColor.current.intValue
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
                if (currentSeedColor > 0) {
                    android.graphics.Color.colorToHSV(currentSeedColor, hsv)
                } else {
                    hsv[0] = 210f; hsv[1] = 0.7f; hsv[2] = 0.8f
                }
            }
        }
        var hue by remember { mutableFloatStateOf(initHsv[0]) }
        var saturation by remember { mutableFloatStateOf(initHsv[1]) }
        var value by remember { mutableFloatStateOf(initHsv[2]) }
        var showCustomPicker by remember {
            mutableStateOf(
                currentSeedColor > 0 && PRESET_COLORS.none { it.first == currentSeedColor }
            )
        }

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
                                val displayColor = when (argb) {
                                    AppPrefs.SEED_WALLPAPER -> if (wallpaperSeedArgb != 0) Color(wallpaperSeedArgb)
                                    else MaterialTheme.colorScheme.primary

                                    0 -> MaterialTheme.colorScheme.primary
                                    else -> Color(argb)
                                }

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
                            Text(
                                "色相",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = hue,
                                onValueChange = { hue = it },
                                valueRange = 0f..360f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // 饱和度滑块
                            Text(
                                "饱和度",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = saturation,
                                onValueChange = { saturation = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // 明度滑块
                            Text(
                                "明度",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
            shape = AppShapes.dialog,
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

private data class StorageSnapshot(
    val downloadTotalSize: Long = 0L,
    val downloadFileCount: Int = 0,
    val subDirSizes: List<DirSizeInfo> = emptyList(),
    val cacheSizes: Map<CacheCategory, Long> = emptyMap()
) {
    val cacheTotalSize: Long get() = cacheSizes.values.sum()
    val totalSize: Long get() = downloadTotalSize + cacheTotalSize
}

private data class StorageSegment(
    val label: String,
    val size: Long,
    val color: Color
)

@Composable
private fun StorageSummaryItem(
    snapshot: StorageSnapshot?,
    onClick: () -> Unit
) {
    SettingsItem(
        icon = Icons.Outlined.Storage,
        title = "存储空间",
        subtitle = snapshot?.let {
            "已用 ${formatFileSize(it.totalSize)} · 缓存 ${formatFileSize(it.cacheTotalSize)}"
        } ?: "正在计算…",
        onClick = onClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorageSettingsScreen(
    savePath: String,
    snapshot: StorageSnapshot?,
    isCalculating: Boolean,
    onRefreshSnapshot: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showSnack = rememberSnackbarLauncher()
    var clearingCategory by remember { mutableStateOf<CacheCategory?>(null) }
    var isClearingAll by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var offlineCacheNeverExpire by remember { mutableStateOf(AppPrefs.offlineCacheNeverExpire) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = AppSpacing.screen, vertical = AppSpacing.medium),
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
                Spacer(Modifier.width(AppSpacing.iconGap))
                Text(
                    "存储空间",
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
                .padding(bottom = AppSpacing.xxxLarge)
        ) {
            SettingsGroupHeader("总览")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.screen),
                shape = AppShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(Modifier.padding(AppSpacing.cardContent), verticalArrangement = Arrangement.spacedBy(AppSpacing.sectionGap)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("已用空间", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(
                                if (isCalculating) "计算中…" else formatFileSize(snapshot?.totalSize ?: 0L),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "下载目录与应用缓存合计",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isCalculating) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }

                    val current = snapshot
                    if (current != null && !isCalculating) {
                        StorageUsageChart(
                            segments = buildStorageSegments(current)
                        )
                    }
                }
            }

            SettingsGroupHeader("下载目录")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.screen),
                shape = AppShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                StorageStatisticsCard(snapshot = snapshot, isCalculating = isCalculating)
            }

            SettingsGroupHeader("缓存")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.screen),
                shape = AppShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    SettingsToggleItem(
                        icon = Icons.Outlined.EventRepeat,
                        title = "离线缓存不自动过期",
                        subtitle = if (offlineCacheNeverExpire) "仅手动清除" else "按有效期自动清理",
                        checked = offlineCacheNeverExpire,
                        onCheckedChange = {
                            offlineCacheNeverExpire = it
                            AppPrefs.offlineCacheNeverExpire = it
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))

                    CacheCategory.entries.forEach { category ->
                        CacheCategoryRow(
                            category = category,
                            size = snapshot?.cacheSizes?.get(category) ?: 0L,
                            isClearing = clearingCategory == category,
                            enabled = !isCalculating && clearingCategory == null && !isClearingAll,
                            modifier = Modifier.padding(horizontal = AppSpacing.cardContent),
                            onClear = {
                                scope.launch {
                                    clearingCategory = category
                                    withContext(Dispatchers.IO) { clearCache(context, category) }
                                    clearingCategory = null
                                    onRefreshSnapshot()
                                    showSnack("${category.title}已清除")
                                }
                            }
                        )
                        if (category != CacheCategory.entries.last()) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
                    Box(Modifier.padding(AppSpacing.screen)) {
                        FilledTonalButton(
                            enabled = (snapshot?.cacheTotalSize ?: 0L) > 0L && !isCalculating && clearingCategory == null && !isClearingAll,
                            onClick = { showClearAllConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            if (isClearingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.width(AppSpacing.itemGap))
                            }
                            Text("清除所有缓存")
                        }
                    }
                }
            }
        }
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isClearingAll) showClearAllConfirm = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
            title = { Text("清除所有缓存？") },
            text = { Text("将清除离线数据、图片缓存和网页缓存。已下载到保存目录的文件不会被删除。") },
            shape = AppShapes.dialog,
            confirmButton = {
                FilledTonalButton(
                    enabled = !isClearingAll,
                    onClick = {
                        scope.launch {
                            isClearingAll = true
                            withContext(Dispatchers.IO) {
                                CacheCategory.entries.forEach { clearCache(context, it) }
                            }
                            isClearingAll = false
                            showClearAllConfirm = false
                            onRefreshSnapshot()
                            showSnack("所有缓存已清除")
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    if (isClearingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(AppSpacing.itemGap))
                    }
                    Text("确认清除")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isClearingAll,
                    onClick = { showClearAllConfirm = false }
                ) { Text("取消") }
            }
        )
    }
}

@Composable
private fun StorageUsageChart(segments: List<StorageSegment>) {
    val visibleSegments = segments.filter { it.size > 0L }
    if (visibleSegments.isEmpty()) {
        Text(
            "暂无可展示的数据",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val total = visibleSegments.sumOf { it.size }.coerceAtLeast(1L)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(smoothCornerShape(9.dp))
        ) {
            visibleSegments.forEach { segment ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight((segment.size.toFloat() / total).coerceAtLeast(0.02f))
                        .background(segment.color)
                )
            }
        }

        visibleSegments.forEach { segment ->
            val percent = segment.size * 100f / total
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(segment.color)
                )
                Spacer(Modifier.width(AppSpacing.itemGap))
                Text(
                    segment.label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatFileSize(segment.size)} · ${String.format("%.1f", percent)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun buildStorageSegments(snapshot: StorageSnapshot): List<StorageSegment> {
    return listOf(
        StorageSegment("下载目录", snapshot.downloadTotalSize, MaterialTheme.colorScheme.primary),
        StorageSegment(CacheCategory.OFFLINE.title, snapshot.cacheSizes[CacheCategory.OFFLINE] ?: 0L, Color(CacheCategory.OFFLINE.color)),
        StorageSegment(CacheCategory.IMAGE.title, snapshot.cacheSizes[CacheCategory.IMAGE] ?: 0L, Color(CacheCategory.IMAGE.color)),
        StorageSegment(CacheCategory.WEBVIEW.title, snapshot.cacheSizes[CacheCategory.WEBVIEW] ?: 0L, Color(CacheCategory.WEBVIEW.color))
    )
}

@Composable
private fun StorageStatisticsCard(snapshot: StorageSnapshot?, isCalculating: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpacing.cardContent)
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
            Spacer(Modifier.width(AppSpacing.sectionGap))
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
                        "${formatFileSize(snapshot?.downloadTotalSize ?: 0)}  ·  ${snapshot?.downloadFileCount ?: 0} 个文件",
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
        val subDirSizes = snapshot?.subDirSizes.orEmpty()
        if (!isCalculating && subDirSizes.isNotEmpty()) {
            Spacer(Modifier.height(AppSpacing.sectionGap))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(AppSpacing.large))

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
                    Spacer(Modifier.width(AppSpacing.itemGap))
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

/** 缓存类别 */
private enum class CacheCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Long
) {
    OFFLINE("离线数据", "Wiki 页面 JSON 缓存", Icons.Outlined.Cached, 0xFF4F8CFF),
    IMAGE("图片缓存", "Coil 磁盘缓存", Icons.Outlined.Image, 0xFF22C55E),
    WEBVIEW("网页缓存", "WebView 页面与脚本缓存", Icons.Outlined.Language, 0xFFF59E0B),
}

@Composable
private fun CacheCategoryRow(
    category: CacheCategory,
    size: Long,
    isClearing: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClear: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            category.icon,
            null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(AppSpacing.iconGap))
        Column(Modifier.weight(1f)) {
            Text(
                category.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (size > 0) formatFileSize(size) else category.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (isClearing) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            IconButton(
                onClick = onClear,
                enabled = enabled && size > 0,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "清除${category.title}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error.copy(
                        alpha = if (enabled && size > 0) 1f else 0.3f
                    )
                )
            }
        }
    }
}

private suspend fun computeAllCacheSizes(
    context: Context
): Map<CacheCategory, Long> = withContext(Dispatchers.IO) {
    val loader = SingletonImageLoader.get(context)
    mapOf(
        CacheCategory.OFFLINE to OfflineCache.totalSize(),
        CacheCategory.IMAGE to (loader.diskCache?.size ?: 0L),
        CacheCategory.WEBVIEW to calculateWebViewCacheSize(context),
    )
}

private suspend fun computeStorageSnapshot(
    context: Context,
    savePath: String
): StorageSnapshot = withContext(Dispatchers.IO) {
    val downloadInfo = computeDownloadStorageInfo(savePath)
    StorageSnapshot(
        downloadTotalSize = downloadInfo.totalSize,
        downloadFileCount = downloadInfo.fileCount,
        subDirSizes = downloadInfo.subDirSizes,
        cacheSizes = computeAllCacheSizes(context)
    )
}

private data class DownloadStorageInfo(
    val totalSize: Long,
    val fileCount: Int,
    val subDirSizes: List<DirSizeInfo>
)

private fun computeDownloadStorageInfo(savePath: String): DownloadStorageInfo {
    return try {
        val root = File(savePath)
        if (!root.exists() || !root.isDirectory) {
            return DownloadStorageInfo(0L, 0, emptyList())
        }
        val totalSize = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val fileCount = root.walkTopDown().count { it.isFile }
        val subDirSizes = root.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.map { dir ->
                DirSizeInfo(
                    name = dir.name,
                    size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                )
            }
            ?.filter { it.size > 0 }
            ?.sortedByDescending { it.size }
            ?.take(8)
            ?.toList()
            ?: emptyList()
        DownloadStorageInfo(totalSize, fileCount, subDirSizes)
    } catch (_: Exception) {
        DownloadStorageInfo(0L, 0, emptyList())
    }
}

private suspend fun clearCache(
    context: Context,
    category: CacheCategory
) = withContext(Dispatchers.IO) {
    when (category) {
        CacheCategory.OFFLINE -> {
            OfflineCache.clearAll()
            OfflineCache.clearMemoryCaches()
        }

        CacheCategory.IMAGE -> {
            val loader = SingletonImageLoader.get(context)
            loader.diskCache?.clear()
            loader.memoryCache?.clear()
        }

        CacheCategory.WEBVIEW -> {
            // WebView 必须在主线程调用
            withContext(Dispatchers.Main) {
                WebView(context).apply {
                    clearCache(true)
                    clearHistory()
                    destroy()
                }
            }
        }
    }
}

/** 计算 WebView 缓存目录大小 */
private fun calculateWebViewCacheSize(context: Context): Long {
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

