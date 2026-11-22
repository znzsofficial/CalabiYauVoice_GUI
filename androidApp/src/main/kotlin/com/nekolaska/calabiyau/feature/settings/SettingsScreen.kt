package com.nekolaska.calabiyau.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.LocalThemeMode
import com.nekolaska.calabiyau.core.ui.SeekablePredictiveBackHandler
import com.nekolaska.calabiyau.core.ui.rememberPredictiveBackTransition
import com.nekolaska.calabiyau.core.usage.UsageStats
import com.nekolaska.calabiyau.core.wiki.WikiAuthHelper

private val settingsNavigationSaver = listSaver<SettingsNavigation, String>(
    save = { it.save() }, restore = { SettingsNavigation.restore(it) }
)

@Composable
fun SettingsScreen(isActive: () -> Boolean = { true }, onBack: () -> Unit) {
    var stack by rememberSaveable(stateSaver = settingsNavigationSaver) { mutableStateOf(SettingsNavigation()) }
    val pages = rememberSaveableStateHolder()
    var editorVisit by rememberSaveable { mutableIntStateOf(0) }
    val current = stack.current
    val previous = stack.previous
    val transitionState = rememberPredictiveBackTransition(current)
    var concurrency by remember { mutableIntStateOf(AppPrefs.maxConcurrency) }
    var desktopMode by remember { mutableStateOf(AppPrefs.wikiDesktopMode) }
    var wallpaperAutoRefresh by remember { mutableStateOf(AppPrefs.wallpaperAutoRefresh) }

    fun back(from: SettingsPage) {
        if (!isActive() || stack.current != from) return
        if (from == SettingsPage.HOME) onBack() else stack = stack.pop(from)
    }

    // The editor alone owns cancellation/discard (including the system back gesture).
    SeekablePredictiveBackHandler(
        enabled = isActive() && previous != null && current != SettingsPage.QUICK_ENTRIES,
        currentState = current,
        targetState = previous ?: current,
        transitionState = transitionState,
        resolveTarget = { stack.previous.takeIf { stack.current == current } },
        onCommit = { back(current) }
    )
    BackHandler(enabled = isActive() && current == SettingsPage.HOME) { back(SettingsPage.HOME) }

    val transition = rememberTransition(transitionState.state, label = "SettingsNavigation")
    transition.AnimatedContent(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        transitionSpec = {
            val direction = if (targetState == initialState.parent) -1 else 1
            (slideInHorizontally(tween(220)) { direction * it / 8 } + fadeIn(tween(220)))
                .togetherWith(slideOutHorizontally(tween(150)) { -direction * it / 8 } + fadeOut(tween(150)))
        }
    ) { page ->
        val open: (SettingsPage) -> Unit = open@{ next ->
            if (!isActive()) return@open
            val updated = stack.open(page, next)
            if (updated !== stack && next == SettingsPage.QUICK_ENTRIES) editorVisit++
            stack = updated
        }
        val pop = { back(page) }
        pages.SaveableStateProvider(page.name) {
            DisposableEffect(page) {
                // Preserve ancestors during drill-down or a cancelled back preview, not popped drafts.
                onDispose { if (page !in stack) pages.removeState(page.name) }
            }
            when (page) {
                SettingsPage.QUICK_ENTRIES -> key(editorVisit) {
                    val visit = editorVisit
                    QuickEntryEditorScreen(isActive = { page == stack.current && visit == editorVisit && isActive() }, onBack = pop)
                }
                SettingsPage.ABOUT -> AboutScreen(onBack = pop)
                SettingsPage.STORAGE -> StorageSettingsScreen(onBack = pop)
                else -> SettingsPageLayout(page.title, pop) {
                    when (page) {
                        SettingsPage.HOME -> SettingsHome(concurrency, desktopMode, open)
                        SettingsPage.APPEARANCE -> AppearanceSettingsSection(
                            wallpaperAutoRefresh = wallpaperAutoRefresh,
                            onOpenColors = { open(SettingsPage.COLORS) },
                            onOpenWallpaper = { open(SettingsPage.WALLPAPER) }
                        )
                        SettingsPage.COLORS -> ColorSettingsSection()
                        SettingsPage.WALLPAPER -> WallpaperSettingsSection(wallpaperAutoRefresh) {
                            wallpaperAutoRefresh = it
                            AppPrefs.wallpaperAutoRefresh = it
                        }
                        SettingsPage.DOWNLOADS -> DownloadSettingsSection(
                            concurrency = concurrency,
                            onConcurrencyChange = { concurrency = it; AppPrefs.maxConcurrency = it },
                            onOpenStorage = { open(SettingsPage.STORAGE) }
                        )
                        SettingsPage.WIKI -> WikiAccountSettings(desktopMode) {
                            desktopMode = it
                            AppPrefs.wikiDesktopMode = it
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPageLayout(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = AppTextStyles.topBarTitle) },
                navigationIcon = { BackNavButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)
                .verticalScroll(rememberScrollState())
                .padding(top = AppSpacing.small, bottom = AppSpacing.xxxLarge)
        ) { content() }
    }
}

@Composable
private fun SettingsHome(concurrency: Int, desktopMode: Boolean, open: (SettingsPage) -> Unit) {
    val layout by AppPrefs.homeQuickEntryLayoutState.collectAsState()
    val themeMode = LocalThemeMode.current.intValue
    val version = rememberAppVersion()
    SettingsCard {
        SettingsItem(Icons.Outlined.GridView, SettingsPage.QUICK_ENTRIES.title,
            if (layout == AppPrefs.HOME_QUICK_LAYOUT_BUTTONS) "按钮布局" else "网格布局") { open(SettingsPage.QUICK_ENTRIES) }
        SettingsDivider()
        SettingsItem(Icons.Outlined.Palette, SettingsPage.APPEARANCE.title, themeModeLabel(themeMode)) { open(SettingsPage.APPEARANCE) }
        SettingsDivider()
        SettingsItem(Icons.Outlined.Folder, SettingsPage.DOWNLOADS.title, "同时下载 $concurrency 项") { open(SettingsPage.DOWNLOADS) }
        SettingsDivider()
        SettingsItem(Icons.Outlined.Language, SettingsPage.WIKI.title, if (desktopMode) "桌面版网页" else "移动版网页") { open(SettingsPage.WIKI) }
        SettingsDivider()
        SettingsItem(Icons.Outlined.Info, SettingsPage.ABOUT.title, "当前版本 ${version.name}") { open(SettingsPage.ABOUT) }
    }

    Spacer(Modifier.height(AppSpacing.itemGap))
    SettingsGroupHeader("隐私")
    var statsEnabled by remember { mutableStateOf(AppPrefs.usageStatsEnabled) }
    SettingsCard {
        SettingsToggleItem(Icons.Outlined.Info, "匿名使用统计", "上报每日使用次数，不含账号", statsEnabled) {
            statsEnabled = it
            UsageStats.setEnabled(it)
        }
    }
}

@Composable
private fun WikiAccountSettings(desktopMode: Boolean, onDesktopModeChange: (Boolean) -> Unit) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    SettingsCard {
        SettingsToggleItem(Icons.Outlined.DesktopWindows, "桌面版网页", if (desktopMode) "开启" else "关闭", desktopMode, onDesktopModeChange)
        SettingsDivider()
        SettingsItem(Icons.Outlined.DeleteSweep, "退出登录", "清除 Wiki 登录信息") { confirmLogout = true }
    }
    if (confirmLogout) {
        SettingsDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录") },
            text = { Text("将清除 Wiki 登录信息。之后需要重新登录才能使用投票等功能。") },
            confirmButton = {
                FilledTonalButton(onClick = {
                    WikiAuthHelper.clearLoginCookies()
                    confirmLogout = false
                }) { Text("确定清除") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("取消") } }
        )
    }
}
