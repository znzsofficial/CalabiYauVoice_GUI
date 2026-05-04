package com.nekolaska.calabiyau.core.navigation

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.wiki.WikiUserApi
import com.nekolaska.calabiyau.R
import com.nekolaska.calabiyau.feature.settings.SettingsScreen
import com.nekolaska.calabiyau.feature.download.DownloadHistoryScreen
import com.nekolaska.calabiyau.feature.download.DownloaderScreen
import com.nekolaska.calabiyau.core.media.AudioPlayerManager
import com.nekolaska.calabiyau.core.ui.LocalSnackbarHostState
import com.nekolaska.calabiyau.core.ui.LiquidGlassTuning
import com.nekolaska.calabiyau.core.ui.smoothCapsuleShape
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.core.ui.LocalLiquidGlassEnabled
import com.nekolaska.calabiyau.core.ui.liquidGlass
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.feature.tools.FileManagerDirectoryPickerConfig
import com.nekolaska.calabiyau.feature.tools.FileManagerPickerMode
import com.nekolaska.calabiyau.feature.tools.FileManagerScreen
import com.nekolaska.calabiyau.feature.tools.ToolsHomeScreen
import com.nekolaska.calabiyau.feature.wiki.hub.WIKI_HOME_URL
import com.nekolaska.calabiyau.feature.wiki.hub.WikiHubPage
import com.nekolaska.calabiyau.feature.wiki.hub.WikiHubScreen
import com.nekolaska.calabiyau.feature.wiki.hub.WikiWebViewScreen
import com.nekolaska.calabiyau.feature.wiki.hub.hasWikiLoginCookie
import com.nekolaska.calabiyau.feature.download.DownloadViewModel
import com.nekolaska.calabiyau.feature.download.PortraitViewModel
import com.nekolaska.calabiyau.feature.download.SearchViewModel
import data.ApiResult
import kotlinx.coroutines.launch

private data class ToolFileManagerOverlayState(
    val initialPath: String? = null,
    val directoryPickerConfig: FileManagerDirectoryPickerConfig? = null,
    val onDirectoryPicked: ((String) -> Unit)? = null,
    val onFilesPicked: ((List<String>) -> Unit)? = null
)

private sealed interface MainBackTarget {
    data object CloseDrawer : MainBackTarget
    data object CloseToolOverlay : MainBackTarget
    data object ExitWikiToPrevious : MainBackTarget
    data object BackToDownloader : MainBackTarget
    data object GoHome : MainBackTarget
}

/** 侧栏导航目的地 */
enum class DrawerDestination {
    WIKI_HUB,          // 首页（Wiki 主页）
    WIKI_HUB_WEBVIEW,  // 从 Hub 内打开的 WebView（叠加在 Hub 之上，保留 Hub 状态）
    WIKI,              // Wiki 浏览器（从侧栏进入）
    DOWNLOADER,        // 资源下载
    FILE_MANAGER,      // 文件管理
    TOOLS,             // 素材工具
    DOWNLOAD_HISTORY,  // 下载历史（仅从资源下载页打开）
    SETTINGS           // 设置
}

private fun shortcutTargetToDestination(shortcutTarget: String?): DrawerDestination = when (shortcutTarget) {
    "downloader" -> DrawerDestination.DOWNLOADER
    "wiki" -> DrawerDestination.WIKI
    else -> DrawerDestination.WIKI_HUB
}

private fun shortcutTargetToHubPage(shortcutTarget: String?): WikiHubPage = when (shortcutTarget) {
    "characters" -> WikiHubPage.CHARACTERS
    else -> WikiHubPage.HOME
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainScreen(
    searchVM: SearchViewModel,
    downloadVM: DownloadViewModel,
    portraitVM: PortraitViewModel,
    shortcutTarget: String? = null,
    shortcutRequestKey: Int = 0
) {
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // 根据 Shortcut 目标决定初始页面
    val initialDestination = remember {
        when (shortcutTarget) {
            "characters" -> DrawerDestination.WIKI_HUB  // Hub 内再导航到角色列表
            "downloader" -> DrawerDestination.DOWNLOADER
            "wiki" -> DrawerDestination.WIKI
            else -> DrawerDestination.WIKI_HUB
        }
    }
    var currentDestination by rememberSaveable {
        mutableStateOf(initialDestination)
    }
    var hubStartPage by rememberSaveable {
        mutableStateOf(shortcutTargetToHubPage(shortcutTarget))
    }
    var hubResetToken by rememberSaveable { mutableIntStateOf(0) }
    var lastHandledShortcutRequestKey by rememberSaveable { mutableIntStateOf(shortcutRequestKey) }

    // ── 自适应布局：平板/折叠屏使用常驻侧栏 ──
    val activity = LocalActivity.current
    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val useExpandedLayout = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded
    var toolFileManagerOverlay by remember { mutableStateOf<ToolFileManagerOverlayState?>(null) }
    // 记录 Wiki 浏览器是从哪里打开的（Hub 或侧栏）
    var wikiEnteredFromHub by rememberSaveable { mutableStateOf(false) }
    // 记录进入侧栏 Wiki 前的页面，退出时回到该页面
    var previousDestination by rememberSaveable { mutableStateOf(DrawerDestination.WIKI_HUB) }

    fun normalizePreviousDestination(destination: DrawerDestination): DrawerDestination =
        if (destination == DrawerDestination.WIKI_HUB_WEBVIEW) DrawerDestination.WIKI_HUB else destination

    fun openWikiHub(page: WikiHubPage = WikiHubPage.HOME, resetStack: Boolean = true) {
        wikiEnteredFromHub = false
        hubStartPage = page
        if (resetStack) hubResetToken++
        currentDestination = DrawerDestination.WIKI_HUB
    }

    LaunchedEffect(shortcutTarget, shortcutRequestKey) {
        val target = shortcutTarget ?: return@LaunchedEffect
        if (shortcutRequestKey == lastHandledShortcutRequestKey) return@LaunchedEffect

        toolFileManagerOverlay = null
        when (target) {
            "characters" -> openWikiHub(page = WikiHubPage.CHARACTERS)
            "downloader" -> currentDestination = DrawerDestination.DOWNLOADER
            "wiki" -> {
                wikiEnteredFromHub = false
                previousDestination = normalizePreviousDestination(currentDestination)
                currentDestination = DrawerDestination.WIKI
            }
            else -> openWikiHub()
        }
        lastHandledShortcutRequestKey = shortcutRequestKey
    }

    val backTarget = when {
        drawerState.isOpen -> MainBackTarget.CloseDrawer
        toolFileManagerOverlay != null -> MainBackTarget.CloseToolOverlay
        currentDestination == DrawerDestination.WIKI && !drawerState.isOpen -> MainBackTarget.ExitWikiToPrevious
        currentDestination == DrawerDestination.DOWNLOAD_HISTORY && !drawerState.isOpen -> MainBackTarget.BackToDownloader
        currentDestination != DrawerDestination.WIKI_HUB
                && currentDestination != DrawerDestination.WIKI
                && currentDestination != DrawerDestination.WIKI_HUB_WEBVIEW
                && !drawerState.isOpen -> MainBackTarget.GoHome
        else -> null
    }
    BackHandler(enabled = backTarget != null) {
        when (backTarget) {
            MainBackTarget.CloseDrawer -> coroutineScope.launch { drawerState.close() }
            MainBackTarget.CloseToolOverlay -> toolFileManagerOverlay = null
            MainBackTarget.ExitWikiToPrevious -> currentDestination = previousDestination
            MainBackTarget.BackToDownloader -> currentDestination = DrawerDestination.DOWNLOADER
            MainBackTarget.GoHome -> openWikiHub()
            null -> Unit
        }
    }

    // 切换页面时停止音频播放
    LaunchedEffect(currentDestination) {
        AudioPlayerManager.stop()
    }

    val openDrawer: () -> Unit = {
        if (!useExpandedLayout) coroutineScope.launch { drawerState.open() }
    }

    // ── 页面内容（Drawer 和 PermanentDrawer 共用） ──
    // 使用 movableContentOf 保持组合树身份，避免 Drawer 类型切换时子树重建
    val pageContent = remember { movableContentOf {
        // Hub 和从 Hub 打开的 WebView 共享同一个组合树，保留 Hub 状态
        var hubWebViewUrl by remember { mutableStateOf<String?>(null) }

        // 将 WIKI_HUB 和 WIKI_HUB_WEBVIEW 视为同一动画状态（WebView 叠加在 Hub 上，不需要转场）
        val animKey = if (currentDestination == DrawerDestination.WIKI_HUB_WEBVIEW)
            DrawerDestination.WIKI_HUB else currentDestination

        AnimatedContent(
            targetState = animKey,
            transitionSpec = {
                val enterTween = tween<Float>(300, easing = FastOutSlowInEasing)
                val exitTween = tween<Float>(250, easing = FastOutLinearInEasing)
                (fadeIn(enterTween) + scaleIn(enterTween, initialScale = 0.95f))
                    .togetherWith(fadeOut(exitTween) + scaleOut(exitTween, targetScale = 0.98f))
            },
            label = "MainPageTransition"
        ) { destination ->
        when (destination) {
            DrawerDestination.DOWNLOADER -> {
                DownloaderScreen(
                    searchVM = searchVM,
                    downloadVM = downloadVM,
                    portraitVM = portraitVM,
                    onOpenDrawer = openDrawer,
                    onOpenFileManager = { currentDestination = DrawerDestination.FILE_MANAGER },
                    onOpenDownloadHistory = { currentDestination = DrawerDestination.DOWNLOAD_HISTORY }
                )
            }

            DrawerDestination.WIKI -> {
                WikiWebViewScreen(
                    onExitWiki = if (wikiEnteredFromHub) null
                        else {{ currentDestination = previousDestination }},
                    initialUrl = WIKI_HOME_URL
                )
            }
            DrawerDestination.WIKI_HUB -> {
                // WIKI_HUB_WEBVIEW 也映射到此分支（animKey 合并）
                // Shortcut "characters" → 直接进入角色列表
                Box {
                    WikiHubScreen(
                        onOpenDrawer = openDrawer,
                        isOverlaid = currentDestination == DrawerDestination.WIKI_HUB_WEBVIEW,
                        initialPage = hubStartPage,
                        resetKey = hubResetToken,
                        onOpenWikiUrl = { url ->
                            hubWebViewUrl = url
                            wikiEnteredFromHub = true
                            currentDestination = DrawerDestination.WIKI_HUB_WEBVIEW
                        }
                    )
                    if (currentDestination == DrawerDestination.WIKI_HUB_WEBVIEW && hubWebViewUrl != null) {
                        WikiWebViewScreen(
                            onExitWiki = {
                                currentDestination = DrawerDestination.WIKI_HUB
                            },
                            initialUrl = hubWebViewUrl!!,
                            useTopBarMode = true
                        )
                    }
                }
            }
            DrawerDestination.FILE_MANAGER -> {
                FileManagerScreen(
                    rootPath = AppPrefs.savePath,
                    onBack = { openWikiHub() }
                )
            }
            DrawerDestination.TOOLS -> {
                Box(Modifier.fillMaxSize()) {
                    ToolsHomeScreen(
                        onBack = { openWikiHub() },
                        backEnabled = toolFileManagerOverlay == null,
                        onOpenFileManager = { path ->
                            toolFileManagerOverlay = ToolFileManagerOverlayState(initialPath = path)
                        },
                        onPickDirectoryFromFileManager = { initialPath, title, description, onPicked ->
                            toolFileManagerOverlay = ToolFileManagerOverlayState(
                                initialPath = initialPath,
                                directoryPickerConfig = FileManagerDirectoryPickerConfig(
                                    title = title,
                                    description = description
                                ),
                                onDirectoryPicked = onPicked
                            )
                        },
                        onPickFilesFromFileManager = { initialPath, title, description, allowMultiSelect, onOpenSystemPicker, onPicked ->
                            toolFileManagerOverlay = ToolFileManagerOverlayState(
                                initialPath = initialPath,
                                directoryPickerConfig = FileManagerDirectoryPickerConfig(
                                    title = title,
                                    description = description,
                                    actionLabel = if (allowMultiSelect) "确认所选文件" else "选择当前文件",
                                    pickMode = FileManagerPickerMode.FILES,
                                    allowMultiSelect = allowMultiSelect,
                                    systemPickerLabel = "打开系统选择器",
                                    onOpenSystemPicker = {
                                        toolFileManagerOverlay = null
                                        onOpenSystemPicker()
                                    }
                                ),
                                onFilesPicked = onPicked
                            )
                        }
                    )

                    AnimatedVisibility(
                        visible = toolFileManagerOverlay != null,
                        enter = fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 6 },
                        exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 6 }
                    ) {
                        val overlay = toolFileManagerOverlay
                        if (overlay != null) {
                            val overlayRootPath = if (overlay.directoryPickerConfig == null && !overlay.initialPath.isNullOrBlank()) {
                                overlay.initialPath
                            } else {
                                AppPrefs.savePath
                            }
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                FileManagerScreen(
                                    rootPath = overlayRootPath,
                                    initialPath = overlay.initialPath,
                                    onBack = { toolFileManagerOverlay = null },
                                    directoryPickerConfig = overlay.directoryPickerConfig,
                                    onDirectoryPicked = { file ->
                                        overlay.onDirectoryPicked?.invoke(file.absolutePath)
                                        toolFileManagerOverlay = null
                                    },
                                    onFilesPicked = { files ->
                                        overlay.onFilesPicked?.invoke(files.map { it.absolutePath })
                                        toolFileManagerOverlay = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
            DrawerDestination.DOWNLOAD_HISTORY -> {
                DownloadHistoryScreen(
                    downloadVM = downloadVM,
                    onBack = { currentDestination = DrawerDestination.DOWNLOADER }
                )
            }

            DrawerDestination.SETTINGS -> {
                SettingsScreen(onBack = { openWikiHub() })
            }

            else -> {} // WIKI_HUB_WEBVIEW 已合并到 WIKI_HUB 处理
        }
        }
    } }

    // ── 全局 Snackbar 宿主，替代 Toast + 接收 SearchViewModel.errorEvent ──
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(searchVM) {
        searchVM.errorEvent.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val liquidGlassEnabled = LocalLiquidGlassEnabled.current.value
    val configuration = LocalConfiguration.current
    val useLiquidGlassDrawer = liquidGlassEnabled && configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val mainLayerBackdrop = rememberLayerBackdrop()
    val emptyBackdrop = remember { emptyBackdrop() }
    val drawerBackdrop = if (useLiquidGlassDrawer) mainLayerBackdrop else emptyBackdrop

    val drawerContent: @Composable () -> Unit = {
        AppDrawerContent(
            currentDestination = currentDestination,
            onDestinationSelected = { dest ->
                if (dest == DrawerDestination.WIKI) {
                    wikiEnteredFromHub = false
                    previousDestination = normalizePreviousDestination(currentDestination)
                }
                if (dest == DrawerDestination.WIKI_HUB) {
                    openWikiHub()
                } else {
                    currentDestination = dest
                }
                if (!useExpandedLayout) coroutineScope.launch { drawerState.close() }
            },
            backdrop = drawerBackdrop,
            liquidGlassEnabled = useLiquidGlassDrawer
        )
    }

    // ── 根据屏幕宽度选择布局 ──
    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                if (useExpandedLayout) {
                    PermanentNavigationDrawer(
                        drawerContent = drawerContent,
                        content = {
                            Box(modifier = Modifier.fillMaxSize().then(if (liquidGlassEnabled) Modifier.layerBackdrop(mainLayerBackdrop) else Modifier)) {
                                pageContent()
                            }
                        }
                    )
                } else {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = currentDestination != DrawerDestination.WIKI
                                && currentDestination != DrawerDestination.WIKI_HUB_WEBVIEW,
                        drawerContent = drawerContent,
                        content = {
                            Box(modifier = Modifier.fillMaxSize().then(if (liquidGlassEnabled) Modifier.layerBackdrop(mainLayerBackdrop) else Modifier)) {
                                pageContent()
                            }
                        }
                    )
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }
    }
}

// ─────────────────────── 侧栏内容 ───────────────────────

@Composable
private fun AppDrawerContent(
    currentDestination: DrawerDestination,
    onDestinationSelected: (DrawerDestination) -> Unit,
    backdrop: Backdrop = emptyBackdrop(),
    liquidGlassEnabled: Boolean = LocalLiquidGlassEnabled.current.value
) {
    // ── Wiki 用户信息状态（提升到 ModalDrawerSheet 外，底部弹窗也能访问） ──
    val hasLoginCookie = remember { mutableStateOf(hasWikiLoginCookie()) }
    var wikiUserInfo by remember { mutableStateOf<WikiUserApi.UserInfo?>(null) }
    var isLoadingUserInfo by remember { mutableStateOf(false) }
    var showUserInfoSheet by remember { mutableStateOf(false) }
    val wikiCoroutineScope = rememberCoroutineScope()
    val drawerContentShape = smoothCornerShape(28.dp)

    ModalDrawerSheet(
        drawerShape = drawerContentShape,
        drawerContainerColor = if (liquidGlassEnabled) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .width(300.dp)
            .liquidGlass(
                backdrop = backdrop,
                shape = { drawerContentShape },
                blurRadius = 8.dp,
                tuning = LiquidGlassTuning(
                    brightness = -0.15f,
                    contrast = 1.2f
                ),
                enabled = liquidGlassEnabled
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
        Spacer(Modifier.height(16.dp))

        // 标题 + 图标
        Row(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = smoothCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(6.dp)
                )
            }
            Text(
                text = "卡拉彼丘",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 每次侧栏显示时刷新登录状态
        LaunchedEffect(currentDestination) {
            hasLoginCookie.value = hasWikiLoginCookie()
            if (hasLoginCookie.value && wikiUserInfo == null && !isLoadingUserInfo) {
                isLoadingUserInfo = true
                wikiCoroutineScope.launch {
                    when (val result = WikiUserApi.fetchCurrentUserInfo()) {
                        is ApiResult.Success -> {
                            val info = result.value
                            if (info != null && info.isLoggedIn) {
                                wikiUserInfo = info
                            }
                        }
                        is ApiResult.Error -> { /* 忽略错误 */ }
                    }
                    isLoadingUserInfo = false
                }
            }
            if (!hasLoginCookie.value) {
                wikiUserInfo = null
                showUserInfoSheet = false
            }
        }

        if (wikiUserInfo != null) {
            WikiUserInfoCard(
                userInfo = wikiUserInfo!!,
                onClick = { showUserInfoSheet = true },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            )
        } else if (isLoadingUserInfo) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 28.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "正在获取用户信息…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp))

        Spacer(Modifier.height(8.dp))

        Text(
            text = "探索",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
        )

        // 1. 首页
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
            label = { Text("首页") },
            selected = currentDestination == DrawerDestination.WIKI_HUB,
            onClick = { onDestinationSelected(DrawerDestination.WIKI_HUB) },
            modifier = Modifier.padding(horizontal = 12.dp),
            shape = smoothCornerShape(28.dp)
        )

        Spacer(Modifier.height(4.dp))

        // 2. Wiki 浏览器
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
            label = { Text("Wiki") },
            badge = {
                if (hasLoginCookie.value) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = smoothCornerShape(8.dp)
                    ) {
                        Text(
                            text = "已登录",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = "未登录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            selected = currentDestination == DrawerDestination.WIKI,
            onClick = { onDestinationSelected(DrawerDestination.WIKI) },
            modifier = Modifier.padding(horizontal = 12.dp),
            shape = smoothCornerShape(28.dp)
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp))
        
        Text(
            text = "工具",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
        )

        // 3. 资源下载
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Download, contentDescription = null) },
            label = { Text("资源下载") },
            selected = currentDestination == DrawerDestination.DOWNLOADER,
            onClick = { onDestinationSelected(DrawerDestination.DOWNLOADER) },
            modifier = Modifier.padding(horizontal = 12.dp),
            shape = smoothCornerShape(28.dp)
        )

        Spacer(Modifier.height(4.dp))

        // 4. 文件管理
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
            label = { Text("文件管理") },
            selected = currentDestination == DrawerDestination.FILE_MANAGER,
            onClick = { onDestinationSelected(DrawerDestination.FILE_MANAGER) },
            modifier = Modifier.padding(horizontal = 12.dp),
            shape = smoothCornerShape(28.dp)
        )

        Spacer(Modifier.height(4.dp))

        // 5. 设置 
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.BuildCircle, contentDescription = null) },
            label = { Text("素材工具") },
            selected = currentDestination == DrawerDestination.TOOLS,
            onClick = { onDestinationSelected(DrawerDestination.TOOLS) },
            modifier = Modifier.padding(horizontal = 12.dp),
            shape = smoothCornerShape(28.dp)
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp))
        
        Text(
            text = "系统",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
        )

        // 6. 设置
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            label = { Text("设置") },
            selected = currentDestination == DrawerDestination.SETTINGS,
            onClick = { onDestinationSelected(DrawerDestination.SETTINGS) },
            modifier = Modifier.padding(horizontal = 12.dp),
            shape = smoothCornerShape(28.dp)
        )

        Spacer(Modifier.height(16.dp))
        } // Column
    }

    // ── 用户信息底部弹窗 ──
    if (showUserInfoSheet && wikiUserInfo != null) {
        WikiUserInfoBottomSheet(
            userInfo = wikiUserInfo!!,
            onDismiss = { showUserInfoSheet = false }
        )
    }
}

// ─────────────────────── Wiki 用户信息卡片（侧栏摘要） ───────────────────────

@Composable
private fun WikiUserInfoCard(
    userInfo: WikiUserApi.UserInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = smoothCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 头像圆圈（首字母）
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = userInfo.name.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userInfo.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "编辑 ${userInfo.editCount} 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "查看详情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─────────────────────── Wiki 用户信息底部弹窗 ───────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WikiUserInfoBottomSheet(
    userInfo: WikiUserApi.UserInfo,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("个人信息", "我的编辑", "监视列表")

    // 编辑贡献状态
    var contributions by remember { mutableStateOf<List<WikiUserApi.UserContrib>>(emptyList()) }
    var isLoadingContribs by remember { mutableStateOf(false) }
    var contribError by remember { mutableStateOf<String?>(null) }

    // 监视列表状态
    var watchlist by remember { mutableStateOf<List<WikiUserApi.WatchlistItem>>(emptyList()) }
    var isLoadingWatchlist by remember { mutableStateOf(false) }
    var watchlistError by remember { mutableStateOf<String?>(null) }

    val activityScope = rememberCoroutineScope()

    // 切到编辑贡献 Tab 时自动加载
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && contributions.isEmpty() && !isLoadingContribs) {
            isLoadingContribs = true
            contribError = null
            activityScope.launch {
                when (val result = WikiUserApi.fetchContributions(userInfo.name, limit = 30)) {
                    is ApiResult.Success -> contributions = result.value
                    is ApiResult.Error -> contribError = result.message
                }
                isLoadingContribs = false
            }
        }
        if (selectedTab == 2 && watchlist.isEmpty() && !isLoadingWatchlist) {
            isLoadingWatchlist = true
            watchlistError = null
            activityScope.launch {
                when (val result = WikiUserApi.fetchWatchlist(limit = 30)) {
                    is ApiResult.Success -> watchlist = result.value
                    is ApiResult.Error -> watchlistError = result.message
                }
                isLoadingWatchlist = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = smoothCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // 头像 + 用户名（始终可见）
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = userInfo.name.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Column {
                    Text(
                        text = userInfo.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Wiki ID: ${userInfo.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Tab 栏
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, maxLines = 1) }
                    )
                }
            }

            // Tab 内容
            when (selectedTab) {
                0 -> UserInfoTabContent(userInfo = userInfo)
                1 -> ActivityListContent(
                    items = contributions.mapIndexed { idx, contrib ->
                        ActivityItem(
                            id = contrib.revId * 10000 + idx,
                            title = contrib.title,
                            summary = contrib.comment.ifBlank { null },
                            timestamp = contrib.timestamp,
                            badge = buildString {
                                if (contrib.isNewPage) append("新建 ")
                                if (contrib.isMinor) append("小编辑 ")
                                val diff = contrib.sizeDiff
                                if (diff > 0) append("+$diff") else if (diff < 0) append("$diff")
                            }.trim().ifBlank { null },
                            badgePositive = contrib.sizeDiff >= 0
                        )
                    },
                    isLoading = isLoadingContribs,
                    error = contribError,
                    emptyIcon = Icons.Outlined.Edit,
                    emptyText = "暂无编辑记录",
                    loadingText = "正在加载编辑记录…",
                    onRetry = {
                        isLoadingContribs = true
                        contribError = null
                        activityScope.launch {
                            when (val result = WikiUserApi.fetchContributions(userInfo.name, limit = 30)) {
                                is ApiResult.Success -> contributions = result.value
                                is ApiResult.Error -> contribError = result.message
                            }
                            isLoadingContribs = false
                        }
                    }
                )
                2 -> ActivityListContent(
                    items = watchlist.mapIndexed { idx, wl ->
                        ActivityItem(
                            id = wl.revId * 10000 + idx,
                            title = wl.title,
                            summary = wl.comment.ifBlank { null },
                            timestamp = wl.timestamp,
                            badge = WikiUserApi.watchTypeLabel(wl.type),
                            badgePositive = true,
                            subtitle = wl.user.ifBlank { null }
                        )
                    },
                    isLoading = isLoadingWatchlist,
                    error = watchlistError,
                    emptyIcon = Icons.Outlined.Visibility,
                    emptyText = "监视列表为空",
                    loadingText = "正在加载监视列表…",
                    onRetry = {
                        isLoadingWatchlist = true
                        watchlistError = null
                        activityScope.launch {
                            when (val result = WikiUserApi.fetchWatchlist(limit = 30)) {
                                is ApiResult.Success -> watchlist = result.value
                                is ApiResult.Error -> watchlistError = result.message
                            }
                            isLoadingWatchlist = false
                        }
                    }
                )
            }
        }
    }
}

// ─────────────────────── 个人信息 Tab ───────────────────────

@Composable
private fun UserInfoTabContent(userInfo: WikiUserApi.UserInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 用户组标签
        if (userInfo.displayGroups.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                userInfo.displayGroups.forEach { group ->
                    Surface(
                        shape = smoothCapsuleShape(),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = WikiUserApi.groupLabel(group),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 详细信息
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UserDetailRow(label = "编辑次数", value = "${userInfo.editCount}")

            if (userInfo.registrationDate.isNotBlank()) {
                UserDetailRow(
                    label = "注册时间",
                    value = WikiUserApi.formatTimestamp(userInfo.registrationDate)
                )
            }

            if (userInfo.email.isNotBlank()) {
                UserDetailRow(label = "邮箱", value = userInfo.email)
            }

            if (userInfo.realName.isNotBlank()) {
                UserDetailRow(label = "真实姓名", value = userInfo.realName)
            }
        }
    }
}

// ─────────────────────── 通用活动列表数据 ───────────────────────

private data class ActivityItem(
    val id: Long,
    val title: String,
    val summary: String? = null,
    val timestamp: String = "",
    val badge: String? = null,
    val badgePositive: Boolean = true,
    val subtitle: String? = null         // 操作者（监视列表用）
)

// ─────────────────────── 通用活动列表 Tab ───────────────────────

@Composable
private fun ActivityListContent(
    items: List<ActivityItem>,
    isLoading: Boolean,
    error: String?,
    emptyIcon: ImageVector,
    emptyText: String,
    loadingText: String,
    onRetry: () -> Unit
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    Text(
                        text = loadingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        error != null -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onRetry) {
                        Text("重试")
                    }
                }
            }
        }
        items.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = emptyIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    ActivityItemCard(item)
                }
            }
        }
    }
}

// ─────────────────────── 单条活动卡片 ───────────────────────

@Composable
private fun ActivityItemCard(item: ActivityItem) {
    val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
    val time = item.timestamp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(smoothCornerShape(12.dp))
            .background(bgColor)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // 页面标题 + 变更标签
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!item.badge.isNullOrBlank()) {
                    Surface(
                        shape = smoothCapsuleShape(),
                        color = if (item.badgePositive)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = item.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.badgePositive)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 摘要/评论
            if (!item.summary.isNullOrBlank()) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 底部：操作者 + 时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!item.subtitle.isNullOrBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (time.isNotBlank()) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun UserDetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
