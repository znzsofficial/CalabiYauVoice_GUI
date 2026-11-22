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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.SeekablePredictiveBackHandler
import com.nekolaska.calabiyau.core.ui.rememberPredictiveBackTransition
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
                        SettingsPage.HOME -> SettingsHome(concurrency, desktopMode, wallpaperAutoRefresh,
                            isActive = { stack.current == SettingsPage.HOME && isActive() }, open = open)
                        SettingsPage.APPEARANCE -> AppearanceSettingsSection(onOpenColors = { open(SettingsPage.COLORS) })
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
        Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(top = AppSpacing.small, bottom = AppSpacing.xxxLarge)) { content() }
        }
    }
}

@Composable
private fun WikiAccountSettings(desktopMode: Boolean, onDesktopModeChange: (Boolean) -> Unit) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    SettingsGroupHeader("浏览与账号", topPadding = AppSpacing.small)
    SettingsCard {
        SettingsToggleItem(
            icon = Icons.Outlined.DesktopWindows,
            title = "桌面版网页",
            subtitle = if (desktopMode) "默认请求桌面版页面" else "默认请求移动版页面",
            checked = desktopMode,
            onCheckedChange = onDesktopModeChange
        )
        SettingsDivider()
        SettingsItem(
            icon = Icons.Outlined.DeleteSweep,
            title = "退出登录",
            subtitle = "清除 Wiki 登录信息",
            iconTint = MaterialTheme.colorScheme.error
        ) { confirmLogout = true }
    }
    if (confirmLogout) {
        SettingsDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录") },
            text = { Text("将清除 Wiki 登录信息。之后需要重新登录才能使用投票等功能。") },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        WikiAuthHelper.clearLoginCookies()
                        confirmLogout = false
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) { Text("确定清除") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("取消") } }
        )
    }
}
