package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import dev.nucleusframework.webview.web.WebView
import dev.nucleusframework.webview.web.rememberWebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewState
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Button
import io.github.composefluent.component.Icon
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.ArrowClockwise
import io.github.composefluent.icons.regular.ArrowLeft
import io.github.composefluent.icons.regular.ArrowRight
import io.github.composefluent.icons.regular.Home
import ui.components.StyledWindow

private const val WIKI_HOME_URL = "https://wiki.biligame.com/klbq/%E9%A6%96%E9%A1%B5"
private const val CREATOR_CENTER_URL = "https://creatorcenter.idreamsky.com/creatorCenter"
private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.113 Mobile Safari/537.36"

@OptIn(ExperimentalFluentApi::class)
@Composable
fun WikiBrowserWindow(onCloseRequest: () -> Unit) {
    WebViewBrowserWindow(
        title = "卡拉彼丘 Wiki",
        homeUrl = WIKI_HOME_URL,
        onCloseRequest = onCloseRequest,
        windowWidth = 1100.dp,
        windowHeight = 800.dp,
    )
}

@OptIn(ExperimentalFluentApi::class)
@Composable
fun CreatorCenterWindow(onCloseRequest: () -> Unit) {
    WebViewBrowserWindow(
        title = "创作者中心",
        homeUrl = CREATOR_CENTER_URL,
        onCloseRequest = onCloseRequest,
        customUserAgent = MOBILE_USER_AGENT,
        windowWidth = 480.dp,
        windowHeight = 900.dp,
    )
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun WebViewBrowserWindow(
    title: String,
    homeUrl: String,
    onCloseRequest: () -> Unit,
    customUserAgent: String? = null,
    windowWidth: Dp = 1000.dp,
    windowHeight: Dp = 750.dp,
) {
    val windowState = rememberWindowState(
        width = windowWidth,
        height = windowHeight,
        position = WindowPosition(Alignment.Center),
    )

    StyledWindow(
        title = title,
        onCloseRequest = onCloseRequest,
        state = windowState,
        onKeyEvent = { keyEvent ->
            if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                onCloseRequest()
                true
            } else {
                false
            }
        },
        useLayer = false,
    ) { insetModifier ->
        WebViewBrowserContent(
            modifier = insetModifier,
            homeUrl = homeUrl,
            customUserAgent = customUserAgent,
        )
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun WebViewBrowserContent(
    modifier: Modifier,
    homeUrl: String,
    customUserAgent: String?,
) {
    val webViewState = rememberWebViewState(homeUrl) {
        customUserAgentString = customUserAgent
    }
    val navigator = rememberWebViewNavigator()
    val isLoading by remember { derivedStateOf { webViewState.isLoading } }
    val pageTitle by remember { derivedStateOf { webViewState.pageTitle.orEmpty() } }
    val currentUrl by remember { derivedStateOf { webViewState.lastLoadedUrl.orEmpty() } }

    Column(modifier.fillMaxSize()) {
        BrowserToolbar(
            title = pageTitle.ifBlank { currentUrl.ifBlank { homeUrl } },
            isLoading = isLoading,
            canGoBack = navigator.canGoBack,
            canGoForward = navigator.canGoForward,
            onBack = navigator::navigateBack,
            onForward = navigator::navigateForward,
            onReload = navigator::reload,
            onHome = { navigator.loadUrl(homeUrl) },
        )
        if (isLoading) {
            ProgressBar(modifier = Modifier.fillMaxWidth())
        }
        WebView(
            state = webViewState,
            navigator = navigator,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun BrowserToolbar(
    title: String,
    isLoading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FluentTheme.colors.background.layer.default)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Button(onClick = onBack, modifier = Modifier.size(40.dp), disabled = !canGoBack) {
            Icon(Icons.Regular.ArrowLeft, contentDescription = "返回", modifier = Modifier.size(22.dp))
        }
        Button(onClick = onForward, modifier = Modifier.size(40.dp), disabled = !canGoForward) {
            Icon(Icons.Regular.ArrowRight, contentDescription = "前进", modifier = Modifier.size(22.dp))
        }
        Button(onClick = onReload, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Regular.ArrowClockwise,
                contentDescription = if (isLoading) "重新加载" else "刷新",
                modifier = Modifier.size(22.dp),
            )
        }
        Button(onClick = onHome, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Regular.Home, contentDescription = "主页", modifier = Modifier.size(22.dp))
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(FluentTheme.colors.control.default)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                color = FluentTheme.colors.text.text.secondary,
                maxLines = 1,
            )
        }
    }
}
