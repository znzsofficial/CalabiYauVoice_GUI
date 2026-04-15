package com.nekolaska.calabiyau.ui.wiki

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Base64
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.nekolaska.calabiyau.data.AppPrefs
import com.nekolaska.calabiyau.ui.shared.smoothCornerShape
import kotlinx.coroutines.launch
import java.io.File

/** Wiki 主页地址 */
const val WIKI_HOME_URL = "https://wiki.biligame.com/klbq/%E9%A6%96%E9%A1%B5"

/** Wiki 域名白名单：在这些域名下的页面保留在 WebView 中 */
private val WIKI_DOMAINS = listOf(
    "wiki.biligame.com",
    "patchwiki.biligame.com",
    "biligame.com",
    "bilibili.com",
    "line.biligame.net",
    "passport.bilibili.com",
    "api.bilibili.com"
)

/** 需要直接拦截（不加载也不打开外部浏览器）的域名 */
private val BLOCKED_HOSTS = listOf(
    "d.bilibili.com"   // B站 APP 下载页
)

/**
 * 带完整浏览器体验的 Wiki WebView 页面。
 *
 * 功能:
 * - 保存登录状态 (CookieManager 持久化)
 * - 支持文件下载 (DownloadManager)
 * - 支持文件上传 (<input type="file"> / camera)
 * - 前进/后退/刷新
 * - 加载进度条
 * - 溢出菜单 (外部浏览器打开、分享)
 */
/**
 * 快速从 Cookie 检测是否登录（不发起网络请求）。
 * 若 Cookie 中有 DedeUserID 则认为已登录。
 */
fun hasWikiLoginCookie(): Boolean {
    return try {
        val cookies = CookieManager.getInstance().getCookie("https://wiki.biligame.com") ?: return false
        cookies.split(";").any { it.trim().startsWith("DedeUserID=") }
    } catch (_: Exception) {
        false
    }
}



@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiWebViewScreen(
    onExitWiki: (() -> Unit)? = null,
    initialUrl: String = WIKI_HOME_URL,
    onInitialUrlConsumed: (() -> Unit)? = null,
    useTopBarMode: Boolean = false
) {
    val context = LocalContext.current

    // 状态
    var webView by remember { mutableStateOf<WebView?>(null) }

    // WebView 生命周期管理：组件销毁时释放 WebView 资源
    DisposableEffect(Unit) {
        onDispose {
            webView?.let { wv ->
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.webViewClient = WebViewClient()  // 清除回调
                wv.webChromeClient = null
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.removeAllViews()
                wv.destroy()
            }
            webView = null
        }
    }

    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("Wiki") }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var textZoomLevel by remember { mutableIntStateOf(100) }

    // 网络错误状态
    var hasNetworkError by remember { mutableStateOf(false) }
    var networkErrorUrl by remember { mutableStateOf<String?>(null) }

    // 登录提示 Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val showSnack: (String) -> Unit = remember(snackbarScope, snackbarHostState) {
        { msg -> snackbarScope.launch { snackbarHostState.showSnackbar(msg) }; Unit }
    }
    var hasShownLoginHint by remember { mutableStateOf(false) }

    // 长按图片保存
    var longPressImageUrl by remember { mutableStateOf<String?>(null) }

    // 文件上传回调
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        fileChooserCallback?.onReceiveValue(if (uris.isNotEmpty()) uris.toTypedArray() else null)
        fileChooserCallback = null
    }

    // 返回键拦截 —— 在 WebView 中可后退时拦截返回键
    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }
    // 顶栏模式：无法后退时按返回键退出 WebView
    BackHandler(enabled = useTopBarMode && !canGoBack && onExitWiki != null) {
        onExitWiki?.invoke()
    }

    // 侧栏模式 = 有退出回调且非顶栏模式（底部工具栏）
    // 顶栏模式 = useTopBarMode（仅顶栏，无底部工具栏）
    val isSidebarMode = onExitWiki != null && !useTopBarMode

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = if (isSidebarMode) 10.dp else 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onExitWiki != null) {
                            // 有退出回调：显示退出按钮（侧栏模式 & 顶栏模式共用）
                            Surface(
                                onClick = { onExitWiki.invoke() },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.ExitToApp,
                                        contentDescription = "退出 Wiki",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                        } else {
                            // 无退出回调：后退按钮
                            IconButton(
                                onClick = { if (canGoBack) webView?.goBack() },
                                enabled = canGoBack,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "后退", modifier = Modifier.size(20.dp))
                            }
                        }

                        // 标题 + URL
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(smoothCornerShape(10.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", currentUrl))
                                    showSnack("已复制链接")
                                }
                                .padding(vertical = 1.dp)
                        ) {
                            Text(
                                text = pageTitle,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = currentUrl.removePrefix("https://").removePrefix("http://"),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!isSidebarMode) {
                            // 主页模式：刷新/停止 + 溢出菜单
                            IconButton(
                                onClick = { if (isLoading) webView?.stopLoading() else webView?.reload() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                                    if (isLoading) "停止" else "刷新",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.MoreVert, "更多", modifier = Modifier.size(20.dp))
                                }
                                WikiOverflowMenu(
                                    showMenu = showMenu,
                                    onDismiss = { showMenu = false },
                                    textZoomLevel = textZoomLevel,
                                    onZoomIn = {
                                        textZoomLevel = (textZoomLevel + 15).coerceAtMost(200)
                                        webView?.settings?.textZoom = textZoomLevel
                                    },
                                    onZoomOut = {
                                        textZoomLevel = (textZoomLevel - 15).coerceAtLeast(50)
                                        webView?.settings?.textZoom = textZoomLevel
                                    },
                                    onResetZoom = {
                                        textZoomLevel = 100
                                        webView?.settings?.textZoom = 100
                                    },
                                    onOpenInBrowser = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, currentUrl.toUri()))
                                    },
                                    onShare = {
                                        val shareIntent = Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, currentUrl)
                                                putExtra(Intent.EXTRA_TITLE, pageTitle)
                                            },
                                            null
                                        )
                                        context.startActivity(shareIntent)
                                    },
                                    // 顶栏模式：前进后退放溢出菜单，不放 Wiki 首页
                                    onGoBack = if (useTopBarMode && canGoBack) {{ webView?.goBack() }} else null,
                                    onGoHome = if (!useTopBarMode) {{ webView?.loadUrl(WIKI_HOME_URL) }} else null,
                                    onGoForward = if (canGoForward) {{ webView?.goForward() }} else null
                                )
                            }
                        } else {
                            // 侧栏模式：仅显示停止按钮
                            if (isLoading) {
                                IconButton(
                                    onClick = { webView?.stopLoading() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, "停止", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // 加载进度条
                    AnimatedVisibility(
                        visible = isLoading,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        LinearProgressIndicator(
                            progress = { loadingProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                }
            }
        },
        bottomBar = if (isSidebarMode) { {
            WikiBottomToolbar(
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                isLoading = isLoading,
                textZoomLevel = textZoomLevel,
                showMenu = showMenu,
                onShowMenuChange = { showMenu = it },
                onGoBack = { webView?.goBack() },
                onGoForward = { webView?.goForward() },
                onRefresh = { if (isLoading) webView?.stopLoading() else webView?.reload() },
                onGoHome = { webView?.loadUrl(WIKI_HOME_URL) },
                onZoomIn = {
                    textZoomLevel = (textZoomLevel + 15).coerceAtMost(200)
                    webView?.settings?.textZoom = textZoomLevel
                },
                onZoomOut = {
                    textZoomLevel = (textZoomLevel - 15).coerceAtLeast(50)
                    webView?.settings?.textZoom = textZoomLevel
                },
                onResetZoom = {
                    textZoomLevel = 100
                    webView?.settings?.textZoom = 100
                },
                onOpenInBrowser = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, currentUrl.toUri()))
                },
                onShare = {
                    val shareIntent = Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                            putExtra(Intent.EXTRA_TITLE, pageTitle)
                        },
                        null
                    )
                    context.startActivity(shareIntent)
                },
            )
        } } else { {} },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = smoothCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                createWikiWebView(
                    context = ctx,
                    onPageStarted = { url ->
                        currentUrl = url
                        isLoading = true
                        // about:blank 是网络错误时主动加载的空白页，不应重置错误状态
                        if (url != "about:blank") {
                            hasNetworkError = false
                        }
                    },
                    onPageFinished = { url ->
                        currentUrl = url
                        isLoading = false
                    },
                    onPassportDetected = {
                        hasShownLoginHint = true
                    },
                    onNetworkError = { url ->
                        hasNetworkError = true
                        networkErrorUrl = url
                        isLoading = false
                    },
                    onTitleChanged = { title ->
                        if (title.isNotBlank() && !title.startsWith("http")) {
                            pageTitle = title
                        }
                    },
                    onProgressChanged = { progress ->
                        loadingProgress = progress
                    },
                    onNavigationChanged = { back, forward ->
                        canGoBack = back
                        canGoForward = forward
                    },
                    onFileChooser = { callback ->
                        fileChooserCallback?.onReceiveValue(null) // 取消之前的回调
                        fileChooserCallback = callback
                        try {
                            fileChooserLauncher.launch("*/*")
                        } catch (_: Exception) {
                            callback.onReceiveValue(null)
                            fileChooserCallback = null
                        }
                        true
                    },
                    showSnack = showSnack
                ).also { wv ->
                    webView = wv
                    // 长按图片检测
                    wv.setOnLongClickListener {
                        val result = wv.hitTestResult
                        when (result.type) {
                            WebView.HitTestResult.IMAGE_TYPE,
                            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                result.extra?.let { url ->
                                    longPressImageUrl = url
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    wv.loadUrl(initialUrl)
                    onInitialUrlConsumed?.invoke()
                }
            },
            update = { /* WebView 状态已通过回调管理 */ }
        )

        // 网络错误覆盖层
        AnimatedVisibility(
            visible = hasNetworkError,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(96.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.WifiOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "网络连接失败",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "无法加载页面，请检查网络连接后重试",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    FilledTonalButton(
                        onClick = {
                            hasNetworkError = false
                            val urlToLoad = networkErrorUrl ?: WIKI_HOME_URL
                            webView?.loadUrl(urlToLoad)
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("重新加载")
                    }
                }
            }
        }
        } // content Box
        } // outer Box
    }

    // ── 登录后提示回到首页 ──
    LaunchedEffect(hasShownLoginHint) {
        if (hasShownLoginHint) {
            val result = snackbarHostState.showSnackbar(
                message = "登录完成后，请点击返回 Wiki 首页",
                actionLabel = "回到首页",
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) {
                webView?.loadUrl(WIKI_HOME_URL)
            }
            hasShownLoginHint = false
        }
    }

    // ── 长按图片保存对话框 ──
    if (longPressImageUrl != null) {
        val imageUrl = longPressImageUrl!!
        AlertDialog(
            onDismissRequest = { longPressImageUrl = null },
            title = { Text("保存图片") },
            text = {
                Text(
                    imageUrl.substringAfterLast('/').take(60),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            shape = smoothCornerShape(28.dp),
            confirmButton = {
                FilledTonalButton(onClick = {
                    val url = imageUrl
                    longPressImageUrl = null
                    try {
                        val fileName = URLUtil.guessFileName(url, null, null)
                        val savePath = AppPrefs.savePath
                        val dir = File(savePath)
                        dir.mkdirs()
                        val request = DownloadManager.Request(url.toUri()).apply {
                            addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                            addRequestHeader("User-Agent", webView?.settings?.userAgentString ?: "")
                            setTitle(fileName)
                            setDescription("正在保存图片...")
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationUri(Uri.fromFile(File(dir, fileName)))
                        }
                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(request)
                        showSnack("已保存: $fileName")
                    } catch (e: Exception) {
                        showSnack("保存失败: ${e.message}")
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { longPressImageUrl = null }) { Text("取消") }
            }
        )
    }
}

// ─────────────────────── 底部工具栏 ───────────────────────

/**
 * Wiki 底部工具栏 —— 导航 + 溢出菜单。
 * 采用 BottomAppBar 风格，按钮宽松分布，易于单手操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WikiBottomToolbar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    textZoomLevel: Int,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onRefresh: () -> Unit,
    onGoHome: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onShare: () -> Unit
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        // 等距分布 5 个按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ← 后退
            IconButton(
                onClick = onGoBack,
                enabled = canGoBack,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "后退", modifier = Modifier.size(18.dp))
            }

            // → 前进
            IconButton(
                onClick = onGoForward,
                enabled = canGoForward,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前进", modifier = Modifier.size(18.dp))
            }

            // 🏠 首页
            Surface(
                onClick = onGoHome,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Home, contentDescription = "首页", modifier = Modifier.size(18.dp))
                }
            }

            // 🔄 刷新 / ✕ 停止
            IconButton(onClick = onRefresh, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                    contentDescription = if (isLoading) "停止" else "刷新",
                    modifier = Modifier.size(18.dp)
                )
            }

            // ⋮ 更多菜单
            Box {
                IconButton(onClick = { onShowMenuChange(true) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { onShowMenuChange(false) },
                    shape = smoothCornerShape(16.dp)
                ) {
                    // ── 页面操作 ──
                    DropdownMenuItem(
                        text = { Text("在浏览器中打开") },
                        onClick = {
                            onOpenInBrowser()
                            onShowMenuChange(false)
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.OpenInBrowser, null, modifier = Modifier.size(20.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("分享") },
                        onClick = {
                            onShare()
                            onShowMenuChange(false)
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Share, null, modifier = Modifier.size(20.dp))
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // ── 文本缩放 ──
                    DropdownMenuItem(
                        text = { Text("放大文字") },
                        onClick = {
                            onZoomIn()
                            onShowMenuChange(false)
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.ZoomIn, null, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            Text(
                                "${textZoomLevel}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("缩小文字") },
                        onClick = {
                            onZoomOut()
                            onShowMenuChange(false)
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.ZoomOut, null, modifier = Modifier.size(20.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("重置缩放") },
                        onClick = {
                            onResetZoom()
                            onShowMenuChange(false)
                        },
                        enabled = textZoomLevel != 100,
                        leadingIcon = {
                            Icon(Icons.Outlined.RestartAlt, null, modifier = Modifier.size(20.dp))
                        }
                    )

                }
            }
        }
    }
}

// ─────────────────────── 溢出菜单（共用） ───────────────────────

@Composable
private fun WikiOverflowMenu(
    showMenu: Boolean,
    onDismiss: () -> Unit,
    textZoomLevel: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onShare: () -> Unit,
    onGoBack: (() -> Unit)? = null,
    onGoHome: (() -> Unit)? = null,
    onGoForward: (() -> Unit)? = null
) {
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = onDismiss,
        shape = smoothCornerShape(16.dp)
    ) {
        // 额外导航项
        if (onGoBack != null) {
            DropdownMenuItem(
                text = { Text("后退") },
                onClick = { onGoBack(); onDismiss() },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(20.dp)) }
            )
        }
        if (onGoForward != null) {
            DropdownMenuItem(
                text = { Text("前进") },
                onClick = { onGoForward(); onDismiss() },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(20.dp)) }
            )
        }
        if (onGoHome != null) {
            DropdownMenuItem(
                text = { Text("Wiki 首页") },
                onClick = { onGoHome(); onDismiss() },
                leadingIcon = { Icon(Icons.Default.Home, null, modifier = Modifier.size(20.dp)) }
            )
        }
        if (onGoBack != null || onGoForward != null || onGoHome != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }

        DropdownMenuItem(
            text = { Text("在浏览器中打开") },
            onClick = { onOpenInBrowser(); onDismiss() },
            leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, null, modifier = Modifier.size(20.dp)) }
        )
        DropdownMenuItem(
            text = { Text("分享") },
            onClick = { onShare(); onDismiss() },
            leadingIcon = { Icon(Icons.Outlined.Share, null, modifier = Modifier.size(20.dp)) }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        DropdownMenuItem(
            text = { Text("放大文字") },
            onClick = { onZoomIn(); onDismiss() },
            leadingIcon = { Icon(Icons.Outlined.ZoomIn, null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                Text("${textZoomLevel}%", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        )
        DropdownMenuItem(
            text = { Text("缩小文字") },
            onClick = { onZoomOut(); onDismiss() },
            leadingIcon = { Icon(Icons.Outlined.ZoomOut, null, modifier = Modifier.size(20.dp)) }
        )
        DropdownMenuItem(
            text = { Text("重置缩放") },
            onClick = { onResetZoom(); onDismiss() },
            enabled = textZoomLevel != 100,
            leadingIcon = { Icon(Icons.Outlined.RestartAlt, null, modifier = Modifier.size(20.dp)) }
        )
    }
}

// ─────────────────────── WebView 工厂 ───────────────────────

@SuppressLint("SetJavaScriptEnabled")
private fun createWikiWebView(
    context: Context,
    onPageStarted: (String) -> Unit,
    onPageFinished: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onNavigationChanged: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onNetworkError: (String) -> Unit = {},
    onPassportDetected: () -> Unit = {},
    onFileChooser: (ValueCallback<Array<Uri>>) -> Boolean,
    showSnack: (String) -> Unit
): WebView {
    // 确保 Cookie 持久化
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(WebView(context), true)

    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // ── Settings ──
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true

            // 缓存策略：根据用户设置
            cacheMode = if (AppPrefs.wikiCacheMode == AppPrefs.WIKI_CACHE_OFFLINE_FIRST)
                WebSettings.LOAD_CACHE_ELSE_NETWORK
            else
                WebSettings.LOAD_DEFAULT

            // 允许混合内容（http 资源在 https 页面中加载）
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            // 文件访问
            allowFileAccess = true
            allowContentAccess = true

            // 视口和缩放
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // User-Agent
            userAgentString = if (AppPrefs.wikiDesktopMode) {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 CalabiYauVoice/1.3"
            } else {
                "$userAgentString CalabiYauVoice/1.3"
            }

            // 支持多窗口（处理 target=_blank）
            setSupportMultipleWindows(true)

            // 地理定位关闭
            setGeolocationEnabled(false)

            // 文本缩放（跟随系统字体大小）
            textZoom = 100

            // 媒体自动播放
            mediaPlaybackRequiresUserGesture = false
        }

        // ── Blob 下载桥接 ──
        addJavascriptInterface(object {
            @JavascriptInterface
            fun onBlobData(base64Data: String, mimeType: String, fileName: String) {
                try {
                    val data = Base64.decode(base64Data, Base64.DEFAULT)
                    val dir = File(AppPrefs.savePath)
                    dir.mkdirs()
                    val file = File(dir, fileName)
                    file.writeBytes(data)
                    Handler(Looper.getMainLooper()).post {
                        showSnack("已保存: $fileName")
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        showSnack("保存失败: ${e.message}")
                    }
                }
            }
        }, "_blobDownloader")

        // ── 下载监听 ──
        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            // blob: URL 通过 JS 读取内容后传回 native 保存
            if (url.startsWith("blob:")) {
                val guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    .let { if (it == "downloadfile.bin" || it.isBlank()) "download_${System.currentTimeMillis()}" else it }
                val safeMime = mimeType ?: "application/octet-stream"
                evaluateJavascript("""
                    (function() {
                        var xhr = new XMLHttpRequest();
                        xhr.open('GET', '$url', true);
                        xhr.responseType = 'blob';
                        xhr.onload = function() {
                            var reader = new FileReader();
                            reader.onloadend = function() {
                                var base64 = reader.result.split(',')[1] || '';
                                _blobDownloader.onBlobData(base64, '$safeMime', '$guessedName');
                            };
                            reader.readAsDataURL(xhr.response);
                        };
                        xhr.onerror = function() {
                            _blobDownloader.onBlobData('', '$safeMime', '$guessedName');
                        };
                        xhr.send();
                    })();
                """.trimIndent(), null)
                return@setDownloadListener
            }

            // data: URL 直接解码保存
            if (url.startsWith("data:")) {
                try {
                    val guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    val commaIdx = url.indexOf(',')
                    if (commaIdx > 0) {
                        val data = Base64.decode(url.substring(commaIdx + 1), Base64.DEFAULT)
                        val dir = File(AppPrefs.savePath)
                        dir.mkdirs()
                        File(dir, guessedName).writeBytes(data)
                        showSnack("已保存: $guessedName")
                    }
                } catch (e: Exception) {
                    showSnack("保存失败: ${e.message}")
                }
                return@setDownloadListener
            }

            try {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(url.toUri()).apply {
                    setMimeType(mimeType ?: "application/octet-stream")
                    addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                    addRequestHeader("User-Agent", userAgent ?: "")
                    setDescription("正在下载文件...")
                    setTitle(fileName)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationUri(Uri.fromFile(File(AppPrefs.savePath, fileName)))
                }
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                showSnack("已加入下载队列: $fileName")
            } catch (_: SecurityException) {
                showSnack("缺少存储权限，无法下载")
            } catch (_: IllegalArgumentException) {
                showSnack("无法下载该文件")
            } catch (e: Exception) {
                showSnack("下载失败: ${e.message}")
            }
        }

        // ── WebViewClient ──
        webViewClient = object : WebViewClient() {
            private var passportHintShown = false

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let {
                    onPageStarted(it)
                    view?.let { v -> onNavigationChanged(v.canGoBack(), v.canGoForward()) }
                }
                // 检测进入登录页，弹出提示
                if (url != null && url.toUri().host.equals("passport.bilibili.com", ignoreCase = true)
                    && !passportHintShown) {
                    passportHintShown = true
                    onPassportDetected()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    onPageFinished(it)
                    view?.let { v -> onNavigationChanged(v.canGoBack(), v.canGoForward()) }
                }
                // 刷新 Cookie
                CookieManager.getInstance().flush()

                // 桌面模式：强制 viewport 宽度为 1280px，欺骗 CSS @media 查询
                if (AppPrefs.wikiDesktopMode) {
                    view?.evaluateJavascript(
                        """
                        (function() {
                            var meta = document.querySelector('meta[name="viewport"]');
                            if (!meta) { meta = document.createElement('meta'); meta.name = 'viewport'; document.head.appendChild(meta); }
                            meta.content = 'width=1280';
                        })();
                        """.trimIndent(), null
                    )
                }

                // 注入 CSS 优化体验（隐藏不必要的广告/侧栏等）
                view?.evaluateJavascript(
                    """
                    (function() {
                        var style = document.createElement('style');
                        style.textContent = `
                            .wiki-nav-ad, .wiki-side-ad, .game-ad-box,
                            .mw-footer, .footer-wiki { display: none !important; }
                        `;
                        document.head.appendChild(style);
                    })();
                    """.trimIndent(),
                    null
                )
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val host = url.toUri().host ?: ""

                // 拦截 B站 APP 下载页等，直接丢弃不加载
                if (BLOCKED_HOSTS.any { host.equals(it, ignoreCase = true) }) {
                    return true
                }

                // Wiki 域名白名单内的链接在 WebView 中打开
                if (WIKI_DOMAINS.any { host.endsWith(it) }) {
                    return false // 交给 WebView 处理
                }

                // 其他链接用外部浏览器
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                } catch (_: Exception) { }
                return true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // 仅处理主框架的错误（非子资源）
                if (request?.isForMainFrame == true) {
                    val errorCode = error?.errorCode ?: ERROR_UNKNOWN
                    // 网络相关错误码
                    if (errorCode == ERROR_HOST_LOOKUP ||
                        errorCode == ERROR_CONNECT ||
                        errorCode == ERROR_TIMEOUT ||
                        errorCode == ERROR_IO ||
                        errorCode == ERROR_UNKNOWN
                    ) {
                        val failedUrl = request.url?.toString() ?: WIKI_HOME_URL
                        onNetworkError(failedUrl)
                        // 阻止 WebView 显示默认错误页
                        view?.stopLoading()
                        view?.loadUrl("about:blank")
                    }
                }
            }
        }

        // ── WebChromeClient ──
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgressChanged(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                title?.let { onTitleChanged(it) }
            }

            // 文件上传支持
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                filePathCallback?.let { return onFileChooser(it) }
                return false
            }

            // 处理 target=_blank 链接：提取 URL 在当前 WebView 中打开
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                // 创建临时 WebView 来拦截 URL，避免父 WebView 承载自身弹窗
                val tempWebView = WebView(view!!.context)
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        view.loadUrl(url)
                        tempWebView.destroy()
                        return true
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()
                return true
            }
        }

        // 确保第三方 Cookie 可用（登录需要）
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    }
}


