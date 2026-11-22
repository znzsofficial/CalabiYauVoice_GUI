package com.nekolaska.calabiyau.feature.settings

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppTheme
import com.nekolaska.calabiyau.core.ui.LocalSnackbarHostState
import com.nekolaska.calabiyau.feature.download.DownloadHistoryScreen
import com.nekolaska.calabiyau.feature.download.DownloadViewModel
import data.DownloadRecord

@Preview(name = "手机", widthDp = 393, heightDp = 820, locale = "zh-rCN")
@Preview(name = "窄屏 · 大字", widthDp = 320, heightDp = 760, fontScale = 1.3f, locale = "zh-rCN")
private annotation class SettingsSizes

@SettingsSizes
@Composable
internal fun SettingsLandingPreview() = PreviewEnvironment {
    SettingsScreen(onBack = {})
}

@SettingsSizes
@Composable
internal fun AppearanceSettingsPreview() = RestoredSettingsPreview(SettingsPage.APPEARANCE)

@SettingsSizes
@Composable
internal fun WallpaperSettingsPreview() = RestoredSettingsPreview(SettingsPage.WALLPAPER)

@SettingsSizes
@Composable
internal fun DownloadSettingsPreview() = RestoredSettingsPreview(SettingsPage.DOWNLOADS)

// SettingsScreen's first rememberSaveable is its navigation stack. Restore only that entry,
// using the production saved form, so previews include the real scaffold and insets.
@Composable
private fun RestoredSettingsPreview(page: SettingsPage) = PreviewEnvironment {
    val registry = remember(page) {
        val registry = SaveableStateRegistry(null) { true }
        object : SaveableStateRegistry by registry {
            private var initial: Any? = mutableStateOf(SettingsNavigation().open(SettingsPage.HOME, page).save())
            override fun consumeRestored(key: String): Any? = initial?.also { initial = null } ?: registry.consumeRestored(key)
        }
    }
    CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { SettingsScreen(onBack = {}) }
}

@SettingsSizes
@Composable
internal fun DownloadHistoryPreview() = PreviewEnvironment {
    // History reads the preference snapshot on construction. It does not subscribe to the
    // network flow, start searches, enqueue downloads or use the application's services.
    DownloadHistoryScreen(remember { DownloadViewModel(Application()) }, onBack = {})
}

@Composable
private fun PreviewEnvironment(content: @Composable () -> Unit) {
    check(LocalInspectionMode.current)
    val context = LocalContext.current
    val ready = remember(context) { mutableStateOf(false) }
    LaunchedEffect(context) {
        val defaults = context.getSharedPreferences("preview-only", Context.MODE_PRIVATE)
        val history = DownloadRecord.encodeToJson(listOf(
            DownloadRecord("文件搜索 (6个)", 6, 1795303800000L),
            DownloadRecord("下载失败", 0, 1795303200000L, status = "error"),
            DownloadRecord("星绘 / 默认", 3, 1795302600000L),
            DownloadRecord("明", 18, 1795217400000L),
            DownloadRecord("文件搜索 (12个)", 12, 1795213800000L)
        ))
        val snapshot = object : SharedPreferences by defaults {
            override fun getString(key: String?, defValue: String?): String? = when (key) {
                "downloadHistoryJson" -> history
                "savePath" -> "/storage/emulated/0/Download/CalabiYauVoice"
                else -> defValue
            }
        }
        AppPrefs.init(object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = snapshot
        })
        ready.value = true
    }
    if (!ready.value) return
    CompositionLocalProvider(
        LocalUpdateCheckSession provides remember { UpdateCheckSession() },
        LocalSnackbarHostState provides remember { SnackbarHostState() }
    ) {
        AppTheme { Surface(Modifier.fillMaxSize(), content = content) }
    }
}
