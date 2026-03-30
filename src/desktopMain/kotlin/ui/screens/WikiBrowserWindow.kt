package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Button
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.ArrowLeft
import io.github.composefluent.icons.regular.ArrowRight
import io.github.composefluent.icons.regular.ArrowClockwise
import io.github.composefluent.icons.regular.Home
import io.github.composefluent.component.Icon
import io.github.kdroidfilter.webview.web.WebView
import io.github.kdroidfilter.webview.web.rememberWebViewNavigator
import io.github.kdroidfilter.webview.web.rememberWebViewState
import ui.components.StyledWindow

private const val WIKI_HOME_URL = "https://wiki.biligame.com/klbq/%E9%A6%96%E9%A1%B5"
private const val CREATOR_CENTER_URL = "https://creatorcenter.idreamsky.com/creatorCenter"
private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.113 Mobile Safari/537.36"

// ────────────────────────────────────────────
//  Wiki 浏览器窗口
// ────────────────────────────────────────────

/**
 * 内置 Wiki 浏览器窗口。
 */
@OptIn(ExperimentalFluentApi::class)
@Composable
fun WikiBrowserWindow(
    onCloseRequest: () -> Unit
) {
    WebViewBrowserWindow(
        title = "卡拉彼丘 Wiki",
        homeUrl = WIKI_HOME_URL,
        onCloseRequest = onCloseRequest,
        windowWidth = 1100.dp,
        windowHeight = 800.dp
    )
}

// ────────────────────────────────────────────
//  创作者中心窗口（手机 UA）
// ────────────────────────────────────────────

/**
 * 创作者中心窗口，使用手机 UA 模拟移动端访问。
 */
@OptIn(ExperimentalFluentApi::class)
@Composable
fun CreatorCenterWindow(
    onCloseRequest: () -> Unit
) {
    WebViewBrowserWindow(
        title = "创作者中心",
        homeUrl = CREATOR_CENTER_URL,
        onCloseRequest = onCloseRequest,
        customUserAgent = MOBILE_USER_AGENT,
        windowWidth = 480.dp,
        windowHeight = 900.dp
    )
}

// ────────────────────────────────────────────
//  通用 WebView 浏览器窗口
// ────────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun WebViewBrowserWindow(
    title: String,
    homeUrl: String,
    onCloseRequest: () -> Unit,
    customUserAgent: String? = null,
    windowWidth: Dp = 1000.dp,
    windowHeight: Dp = 750.dp
) {
    val windowState = rememberWindowState(
        width = windowWidth,
        height = windowHeight,
        position = WindowPosition(Alignment.Center)
    )

    StyledWindow(
        title = title,
        onCloseRequest = onCloseRequest,
        state = windowState,
        onKeyEvent = { keyEvent ->
            if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                onCloseRequest(); true
            } else false
        },
        useLayer = false
    ) { insetModifier ->
        WebViewBrowserContent(
            modifier = insetModifier,
            homeUrl = homeUrl,
            customUserAgent = customUserAgent
        )
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun WebViewBrowserContent(
    modifier: Modifier = Modifier,
    homeUrl: String,
    customUserAgent: String? = null
) {
    val webViewState = rememberWebViewState(homeUrl) {
        if (customUserAgent != null) {
            customUserAgentString = customUserAgent
        }
    }
    val navigator = rememberWebViewNavigator()

    val isLoading by remember { derivedStateOf { webViewState.isLoading } }
    val pageTitle by remember { derivedStateOf { webViewState.pageTitle ?: "" } }
    val currentUrl by remember { derivedStateOf { webViewState.lastLoadedUrl ?: "" } }

    Column(modifier.fillMaxSize()) {
        // ── 顶部工具栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FluentTheme.colors.background.layer.default)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 返回按钮
            Button(
                onClick = { navigator.navigateBack() },
                modifier = Modifier.size(40.dp),
                disabled = !navigator.canGoBack
            ) {
                Icon(Icons.Regular.ArrowLeft, contentDescription = "返回", modifier = Modifier.size(22.dp))
            }

            // 前进按钮
            Button(
                onClick = { navigator.navigateForward() },
                modifier = Modifier.size(40.dp),
                disabled = !navigator.canGoForward
            ) {
                Icon(Icons.Regular.ArrowRight, contentDescription = "前进", modifier = Modifier.size(22.dp))
            }

            // 刷新按钮
            Button(
                onClick = { navigator.reload() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Regular.ArrowClockwise, contentDescription = "刷新", modifier = Modifier.size(22.dp))
            }

            // 主页按钮
            Button(
                onClick = { navigator.loadUrl(homeUrl) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Regular.Home, contentDescription = "主页", modifier = Modifier.size(22.dp))
            }

            // URL / 标题显示
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(FluentTheme.colors.control.default)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = pageTitle.ifBlank { currentUrl },
                    fontSize = 13.sp,
                    color = FluentTheme.colors.text.text.secondary,
                    maxLines = 1
                )
            }
        }

        // ── 加载进度条 ──
        if (isLoading) {
            ProgressBar(modifier = Modifier.fillMaxWidth())
        }

        // ── WebView 主体 ──
        WebView(
            state = webViewState,
            navigator = navigator,
            modifier = Modifier.fillMaxSize()
        )
    }
}
