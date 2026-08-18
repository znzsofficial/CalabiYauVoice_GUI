package com.nekolaska.calabiyau.feature.settings

import android.content.Intent
import android.os.Build
import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.SeekablePredictiveBackHandler
import com.nekolaska.calabiyau.core.ui.rememberPredictiveBackTransition
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.tools.formatFileSize
import com.nekolaska.calabiyau.feature.tools.getWritablePathFromTreeUri
import com.nekolaska.calabiyau.feature.wiki.hub.WikiWebViewScreen
import com.nekolaska.calabiyau.feature.wiki.hub.defaultQuickEntryIds
import com.nekolaska.calabiyau.feature.wiki.hub.quickEntryById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsPage {
    MAIN,
    ABOUT,
    STORAGE,
    UPDATE_WEB
}

private const val SHOW_SETTINGS_DEBUG_ITEM = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onWebViewVisible: (Boolean) -> Unit = {}) {
    var savePath by remember { mutableStateOf(AppPrefs.savePath) }
    var maxConcurrency by remember { mutableStateOf(AppPrefs.maxConcurrency.toString()) }
    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }
    var bottomBarStyle by remember { mutableIntStateOf(AppPrefs.bottomBarStyle) }
    var homeQuickEntryLayout by remember { mutableIntStateOf(AppPrefs.homeQuickEntryLayout) }
    var homeQuickEntryIds by remember {
        mutableStateOf(
            AppPrefs.homeQuickEntryIds.takeIf { it.isNotEmpty() } ?: defaultQuickEntryIds
        )
    }
    var showQuickEntrySheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val showSnack = rememberSnackbarLauncher()
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

    val dirPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = getWritablePathFromTreeUri(it)
            if (path != null) {
                savePath = path
                AppPrefs.savePath = path
                showSnack("已更新保存目录")
            } else {
                showSnack("所选目录不支持直接写入，请选择本机可写目录")
            }
        }
    }

    val canPredictivePopToMain = currentPage != SettingsPage.MAIN && currentPage != SettingsPage.UPDATE_WEB
    val transitionState = rememberPredictiveBackTransition(currentPage)

    SeekablePredictiveBackHandler(
        enabled = canPredictivePopToMain,
        currentState = currentPage,
        targetState = SettingsPage.MAIN,
        transitionState = transitionState,
        onCommit = { currentPage = SettingsPage.MAIN }
    )
    BackHandler(enabled = currentPage == SettingsPage.MAIN) {
        onBack()
    }

    val settingsScrollState = rememberScrollState()

    val animDuration = 300
    val transition = rememberTransition(transitionState, label = "SettingsAboutTransition")
    transition.AnimatedContent(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        transitionSpec = {
            if (targetState != SettingsPage.MAIN) {
                (slideInHorizontally(tween(animDuration)) { it / 4 } + fadeIn(tween(animDuration)))
                    .togetherWith(slideOutHorizontally(tween(animDuration)) { -it / 4 } + fadeOut(tween(animDuration / 2)))
            } else {
                (slideInHorizontally(tween(animDuration)) { -it / 4 } + fadeIn(tween(animDuration)))
                    .togetherWith(slideOutHorizontally(tween(animDuration)) { it / 4 } + fadeOut(tween(animDuration / 2)))
            }
        }
    ) { page ->
        if (page == SettingsPage.ABOUT) {
            AboutScreen(onBack = { currentPage = SettingsPage.MAIN })
            return@AnimatedContent
        }
        if (page == SettingsPage.STORAGE) {
            StorageSettingsScreen(
                snapshot = storageSnapshot,
                isCalculating = isStorageCalculating,
                onRefreshSnapshot = ::refreshStorageSnapshot,
                onBack = { currentPage = SettingsPage.MAIN }
            )
            return@AnimatedContent
        }
        if (page == SettingsPage.UPDATE_WEB) {
            LaunchedEffect(Unit) { onWebViewVisible(true) }
            DisposableEffect(Unit) { onDispose { onWebViewVisible(false) } }
            WikiWebViewScreen(
                onExitWiki = { currentPage = SettingsPage.MAIN },
                initialUrl = updateWebUrl ?: "https://wiki.nekolaska.vip",
                useTopBarMode = true
            )
            return@AnimatedContent
        }

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
                AppearanceSettingsSection()

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
                            title = "快捷入口",
                            subtitle = when (homeQuickEntryLayout) {
                                AppPrefs.HOME_QUICK_LAYOUT_BUTTONS -> "按钮"
                                else -> "网格"
                            },
                            onClick = { showQuickLayoutDialog = true }
                        )
                        if (showQuickLayoutDialog) {
                            AlertDialog(
                                onDismissRequest = { showQuickLayoutDialog = false },
                                title = { Text("快捷入口") },
                                text = {
                                    Column {
                                        listOf(
                                            AppPrefs.HOME_QUICK_LAYOUT_GRID to "网格",
                                            AppPrefs.HOME_QUICK_LAYOUT_BUTTONS to "按钮"
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
                            title = "自定义入口",
                            subtitle = homeQuickEntryIds
                                .mapNotNull(quickEntryById::get)
                                .joinToString("、") { it.label }
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
                        SettingsItem(
                            icon = Icons.Outlined.Folder,
                            title = "保存路径",
                            subtitle = savePath,
                            onClick = { dirPicker.launch(null) }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
                        var showPathDialog by remember { mutableStateOf(false) }
                        SettingsItem(
                            icon = Icons.Outlined.Edit,
                            title = "自定路径",
                            subtitle = "输入保存位置",
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

                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
                        var showConcurrencyDialog by remember { mutableStateOf(false) }
                        SettingsItem(
                            icon = Icons.Outlined.Speed,
                            title = "同时下载",
                            subtitle = maxConcurrency,
                            onClick = { showConcurrencyDialog = true }
                        )
                        if (showConcurrencyDialog) {
                            var tempConcurrency by remember { mutableStateOf(maxConcurrency) }
                            AlertDialog(
                                onDismissRequest = { showConcurrencyDialog = false },
                                title = { Text("同时下载") },
                                text = {
                                    Column {
                                        Text(
                                            "可同时进行的下载数量，范围为 1 到 32。",
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
                        var wikiCacheMode by remember { mutableIntStateOf(AppPrefs.wikiCacheMode) }
                        val cacheName = when (wikiCacheMode) {
                            AppPrefs.WIKI_CACHE_OFFLINE_FIRST -> "优先使用缓存"
                            else -> "自动"
                        }
                        SettingsItem(
                            icon = Icons.Outlined.OfflinePin,
                            title = "缓存",
                            subtitle = cacheName,
                            onClick = {
                                val newMode = if (wikiCacheMode == AppPrefs.WIKI_CACHE_DEFAULT)
                                    AppPrefs.WIKI_CACHE_OFFLINE_FIRST else AppPrefs.WIKI_CACHE_DEFAULT
                                wikiCacheMode = newMode
                                AppPrefs.wikiCacheMode = newMode
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))

                        var wikiDesktopMode by remember { mutableStateOf(AppPrefs.wikiDesktopMode) }
                        SettingsToggleItem(
                            icon = Icons.Outlined.DesktopWindows,
                            title = "桌面版网页",
                            subtitle = if (wikiDesktopMode) "开启" else "关闭",
                            checked = wikiDesktopMode,
                            onCheckedChange = {
                                wikiDesktopMode = it
                                AppPrefs.wikiDesktopMode = it
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))

                        var showClearCookieDialog by remember { mutableStateOf(false) }
                        SettingsItem(
                            icon = Icons.Outlined.DeleteSweep,
                            title = "退出登录",
                            subtitle = "清除 Wiki 登录信息",
                            onClick = { showClearCookieDialog = true }
                        )
                        if (showClearCookieDialog) {
                            AlertDialog(
                                onDismissRequest = { showClearCookieDialog = false },
                                title = { Text("退出登录") },
                                text = { Text("将清除 Wiki 登录信息。之后需要重新登录才能使用投票等功能。") },
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
                        SettingsItem(
                            icon = Icons.Outlined.Info,
                            title = "存储空间",
                            subtitle = storageSnapshot?.let {
                                "已用 ${formatFileSize(it.totalSize)} · 缓存 ${formatFileSize(it.cacheTotalSize)}"
                            } ?: "正在计算…",
                            onClick = { currentPage = SettingsPage.STORAGE }
                        )
                    }
                }

                Spacer(Modifier.height(AppSpacing.sectionGap))

                // 关于分组
                SettingsGroupHeader("关于")

                val updateScope = rememberCoroutineScope()
                var isCheckingUpdate by remember { mutableStateOf(false) }
                var updateSubtitle by remember { mutableStateOf("点击检查新版本") }
                var updateResult by remember { mutableStateOf<UpdateApi.UpdateInfo?>(null) }

                val currentVersion = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.1.5"
                    } catch (_: Exception) {
                        "2.1.5"
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
                            subtitle = "版本与版权",
                            onClick = { currentPage = SettingsPage.ABOUT }
                        )

                        SettingsItem(
                            icon = if (isCheckingUpdate) Icons.Outlined.Sync else Icons.Outlined.SystemUpdate,
                            title = "软件更新",
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
