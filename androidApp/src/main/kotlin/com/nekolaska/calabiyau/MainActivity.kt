package com.nekolaska.calabiyau

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nekolaska.calabiyau.core.usage.UsageStats
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.launcher.LauncherIconTheme
import com.nekolaska.calabiyau.core.cache.AppCacheBootstrap
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.feature.settings.UpdateApi
import com.nekolaska.calabiyau.feature.settings.UpdateApkCleanup
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.feature.wiki.gallery.WallpaperApi
import com.nekolaska.calabiyau.core.ui.AppTheme
import com.nekolaska.calabiyau.core.navigation.MainScreen
import com.nekolaska.calabiyau.feature.download.DownloadViewModel
import com.nekolaska.calabiyau.feature.download.PortraitViewModel
import com.nekolaska.calabiyau.feature.download.SearchViewModel
import com.nekolaska.calabiyau.feature.settings.UpdateAvailableDialog
import com.nekolaska.calabiyau.feature.settings.LocalUpdateCheckSession
import com.nekolaska.calabiyau.feature.settings.UpdateCheckSession
import com.nekolaska.calabiyau.feature.settings.openExternalUri
import com.nekolaska.calabiyau.core.webkit.WebViewWarmup
import data.PortraitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val shortcutTargetState = mutableStateOf<String?>(null)
    private val shortcutRequestKeyState = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        var keepSplashOnScreen = true
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }
        enableEdgeToEdge()
        if (!CrashReportActivity.shouldSkipPendingCrash(intent) && CrashHandler.hasPendingCrashLog(this)) {
            startActivity(
                Intent(this, CrashReportActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
            finish()
            return
        }

        NotificationHelper.createChannel(this)
        AppPrefs.init(this)
        UsageStats.attach(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) { UpdateApkCleanup.cleanupAfterLaunch(applicationContext) }
        LauncherIconTheme.syncWithPreference(this, AppPrefs.launcherIconTheme)
        OfflineCache.init(this)
        AppCacheBootstrap.ensureRegistered()
        WebViewWarmup.start(this)
        // 仅在冷启动时异步清理过期磁盘缓存，避免配置变更时重复执行。
        if (savedInstanceState == null) {
            lifecycleScope.launch(Dispatchers.IO) {
                OfflineCache.pruneExpired()
                WallpaperApi.refresh()
            }
        }

        PortraitRepository.init(
            fetchFilesInCategory = { cat, audio -> WikiEngine.fetchFilesInCategory(cat, audio) },
            searchFilesFn = { kw, audio -> WikiEngine.searchFiles(kw, audio) },
            getAllCharacterNames = { WikiEngine.getAllCharacterNames() }
        )

        // 解析首次进入时的快捷方式目标。
        shortcutTargetState.value = intent?.getStringExtra("shortcut_target")
        shortcutRequestKeyState.intValue = savedInstanceState?.getInt("shortcut_request_key", 1) ?: 1

        setContent {
            AppTheme {
                var showSplashCover by remember { mutableStateOf(true) }
                val searchVM: SearchViewModel = viewModel()
                val downloadVM: DownloadViewModel = viewModel()
                val portraitVM: PortraitViewModel = viewModel()

                LaunchedEffect(Unit) {
                    // 先让 Compose 中的同色覆盖层接住系统 Splash，再移除系统 Splash。
                    withFrameNanos { }
                    keepSplashOnScreen = false
                    withFrameNanos { }
                    // 主界面和系统栏都完成至少一帧后，再由 Compose 覆盖层退场。
                    withFrameNanos { }
                    showSplashCover = false
                    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        while (true) {
                            UsageStats.foreground()
                            delay(UsageStats.untilTomorrow())
                        }
                    }
                }

                // 启动时静默检查更新，每天最多一次。
                val context = LocalContext.current
                val updates: UpdateCheckSession = viewModel { UpdateCheckSession() }
                LaunchedEffect(Unit) {
                    val now = System.currentTimeMillis()
                    val lastCheck = AppPrefs.lastUpdateCheck
                    val oneDayMs = 24 * 60 * 60 * 1000L
                    if (now - lastCheck < oneDayMs) return@LaunchedEffect
                    val packageInfo = try {
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    } catch (_: Exception) { return@LaunchedEffect }
                    val currentVersion = packageInfo.versionName ?: return@LaunchedEffect
                    @Suppress("DEPRECATION")
                    val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        packageInfo.versionCode.toLong()
                    }
                    updates.checkAtStartup({ UpdateApi.checkUpdate(currentVersion, currentVersionCode) }) { result ->
                        when (result) {
                            is UpdateApi.Result.NewVersion -> {
                                updates.available = result.info
                                AppPrefs.lastUpdateCheck = now
                            }
                            UpdateApi.Result.AlreadyLatest -> AppPrefs.lastUpdateCheck = now
                            is UpdateApi.Result.Error -> { /* 静默失败 */ }
                        }
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalUpdateCheckSession provides updates) {
                        MainScreen(
                            searchVM,
                            downloadVM,
                            portraitVM,
                            shortcutTarget = shortcutTargetState.value,
                            shortcutRequestKey = shortcutRequestKeyState.intValue
                        )
                    }

                    AnimatedVisibility(
                        visible = showSplashCover,
                        exit = fadeOut(animationSpec = tween(220)) + scaleOut(targetScale = 0.96f, animationSpec = tween(220))
                    ) {
                        SplashCover()
                    }
                }

                // 启动和手动检查共用同一个弹窗；已经开始的下载不随设置页面重组。
                updates.available?.let { info ->
                    val curVer = remember {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
                        } catch (_: Exception) { "?" }
                    }
                    UpdateAvailableDialog(
                        info = info,
                        install = updates.install,
                        currentVersion = curVer,
                        onDismiss = { updates.available = null },
                        onOpenBrowser = {
                            openExternalUri(context, UpdateApi.resolveApkUrl(info).toUri(), onError = {
                                Toast.makeText(context, "未找到可用的浏览器", Toast.LENGTH_SHORT).show()
                            })
                        }
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("shortcut_request_key", shortcutRequestKeyState.intValue)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shortcutTargetState.value = intent.getStringExtra("shortcut_target")
        shortcutRequestKeyState.intValue++
    }
}
@Composable
private fun SplashCover() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.splash_screen_background)),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.splash_logo),
            contentDescription = null,
            tint = colorResource(R.color.splash_logo_color),
            modifier = Modifier.height(96.dp)
        )
    }
}
