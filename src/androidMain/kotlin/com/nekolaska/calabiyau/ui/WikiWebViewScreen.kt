package com.nekolaska.calabiyau.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.util.Base64
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import com.nekolaska.calabiyau.data.AppPrefs
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

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
 * 从 CookieManager 中检测 bilibili Wiki 登录状态。
 * 若已登录返回用户名/UID，否则返回 null。
 */
fun getWikiLoginInfo(): String? {
    return try {
        val cookies = CookieManager.getInstance().getCookie("https://wiki.biligame.com") ?: return null
        val cookieMap = cookies.split(";").associate {
            val parts = it.trim().split("=", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to ""
        }
        // DedeUserID 存在说明已登录；尝试获取用户名
        val uid = cookieMap["DedeUserID"]
        val name = cookieMap["DedeUserID__ckMd5"] // 无直接用户名cookie，显示UID
        if (!uid.isNullOrBlank()) "UID: $uid" else null
    } catch (_: Exception) {
        null
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiWebViewScreen(
    onExitWiki: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // 状态
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(WIKI_HOME_URL) }
    var pageTitle by remember { mutableStateOf("Wiki") }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var textZoomLevel by remember { mutableIntStateOf(100) }

    // 长按图片保存
    var longPressImageUrl by remember { mutableStateOf<String?>(null) }

    // 文件上传回调
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        fileChooserCallback?.onReceiveValue(if (uris.isNotEmpty()) uris.toTypedArray() else null)
        fileChooserCallback = null
    }

    // 返回键拦截 —— 在 WebView 中可后退时拦截返回键
    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 返回/前进
                        IconButton(
                            onClick = { webView?.goBack() },
                            enabled = canGoBack
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "后退")
                        }
                        IconButton(
                            onClick = { webView?.goForward() },
                            enabled = canGoForward
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "前进")
                        }

                        // 标题 + URL
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
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
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 刷新/停止
                        IconButton(onClick = {
                            if (isLoading) webView?.stopLoading() else webView?.reload()
                        }) {
                            Icon(
                                if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                                contentDescription = if (isLoading) "停止" else "刷新"
                            )
                        }

                        // 溢出菜单
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, "更多")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("回到首页") },
                                    onClick = {
                                        webView?.loadUrl(WIKI_HOME_URL)
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("在浏览器中打开") },
                                    onClick = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                        )
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.OpenInBrowser, null, modifier = Modifier.size(20.dp))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("分享") },
                                    onClick = {
                                        val shareIntent = Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, currentUrl)
                                                putExtra(Intent.EXTRA_TITLE, pageTitle)
                                            },
                                            null
                                        )
                                        context.startActivity(shareIntent)
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Share, null, modifier = Modifier.size(20.dp))
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                // 文本缩放
                                DropdownMenuItem(
                                    text = { Text("放大文字") },
                                    onClick = {
                                        textZoomLevel = (textZoomLevel + 15).coerceAtMost(200)
                                        webView?.settings?.textZoom = textZoomLevel
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.ZoomIn, null, modifier = Modifier.size(20.dp))
                                    },
                                    trailingIcon = {
                                        Text("${textZoomLevel}%", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("缩小文字") },
                                    onClick = {
                                        textZoomLevel = (textZoomLevel - 15).coerceAtLeast(50)
                                        webView?.settings?.textZoom = textZoomLevel
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.ZoomOut, null, modifier = Modifier.size(20.dp))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("重置缩放") },
                                    onClick = {
                                        textZoomLevel = 100
                                        webView?.settings?.textZoom = 100
                                        showMenu = false
                                    },
                                    enabled = textZoomLevel != 100,
                                    leadingIcon = {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp))
                                    }
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                DropdownMenuItem(
                                    text = { Text("清除登录状态") },
                                    onClick = {
                                        CookieManager.getInstance().removeAllCookies(null)
                                        CookieManager.getInstance().flush()
                                        webView?.reload()
                                        Toast.makeText(context, "已清除登录状态", Toast.LENGTH_SHORT).show()
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Close, null, modifier = Modifier.size(20.dp))
                                    }
                                )

                                // 退出 Wiki
                                if (onExitWiki != null) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    DropdownMenuItem(
                                        text = { Text("退出 Wiki") },
                                        onClick = {
                                            showMenu = false
                                            onExitWiki()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(20.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 加载进度条
                    AnimatedVisibility(
                        visible = isLoading,
                        enter = fadeIn(),
                        exit = fadeOut()
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
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            factory = { ctx ->
                createWikiWebView(
                    context = ctx,
                    onPageStarted = { url ->
                        currentUrl = url
                        isLoading = true
                    },
                    onPageFinished = { url ->
                        currentUrl = url
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
                    }
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
                    wv.loadUrl(WIKI_HOME_URL)
                }
            },
            update = { /* WebView 状态已通过回调管理 */ }
        )
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
            shape = RoundedCornerShape(28.dp),
            confirmButton = {
                FilledTonalButton(onClick = {
                    val url = imageUrl
                    longPressImageUrl = null
                    try {
                        val fileName = URLUtil.guessFileName(url, null, null)
                        val savePath = AppPrefs.savePath
                        val dir = java.io.File(savePath)
                        dir.mkdirs()
                        val request = DownloadManager.Request(Uri.parse(url)).apply {
                            addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                            addRequestHeader("User-Agent", webView?.settings?.userAgentString ?: "")
                            setTitle(fileName)
                            setDescription("正在保存图片...")
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationUri(Uri.fromFile(java.io.File(dir, fileName)))
                        }
                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(request)
                        Toast.makeText(context, "已保存: $fileName", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { longPressImageUrl = null }) { Text("取消") }
            }
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
    onFileChooser: (ValueCallback<Array<Uri>>) -> Boolean
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

            // User-Agent: 追加标识以便后续 Wiki API 调用
            userAgentString = "$userAgentString CalabiYauVoice/1.3"

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
            @android.webkit.JavascriptInterface
            fun onBlobData(base64Data: String, mimeType: String, fileName: String) {
                try {
                    val data = Base64.decode(base64Data, Base64.DEFAULT)
                    val dir = java.io.File(AppPrefs.savePath)
                    dir.mkdirs()
                    val file = java.io.File(dir, fileName)
                    file.writeBytes(data)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "已保存: $fileName", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
                        val dir = java.io.File(AppPrefs.savePath)
                        dir.mkdirs()
                        java.io.File(dir, guessedName).writeBytes(data)
                        Toast.makeText(context, "已保存: $guessedName", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                return@setDownloadListener
            }

            try {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType ?: "application/octet-stream")
                    addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                    addRequestHeader("User-Agent", userAgent ?: "")
                    setDescription("正在下载文件...")
                    setTitle(fileName)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationUri(Uri.fromFile(java.io.File(AppPrefs.savePath, fileName)))
                }
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(context, "已加入下载队列: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                Toast.makeText(context, "缺少存储权限，无法下载", Toast.LENGTH_LONG).show()
            } catch (e: IllegalArgumentException) {
                Toast.makeText(context, "无法下载该文件", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // ── WebViewClient ──
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let {
                    onPageStarted(it)
                    view?.let { v -> onNavigationChanged(v.canGoBack(), v.canGoForward()) }
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

                // 注入 CSS 优化移动端体验（隐藏不必要的广告/侧栏等）
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
                val host = Uri.parse(url).host ?: ""

                // Wiki 域名白名单内的链接在 WebView 中打开
                if (WIKI_DOMAINS.any { host.endsWith(it) }) {
                    return false // 交给 WebView 处理
                }

                // 其他链接用外部浏览器
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) { }
                return true
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
                tempWebView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView?,
                        request: android.webkit.WebResourceRequest?
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


