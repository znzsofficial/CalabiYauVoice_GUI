package com.nekolaska.calabiyau.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════
//  Empty / error / loading states
// ════════════════════════════════════════════════════════

/**
 * Centered empty placeholder. When [errorMessage] is supplied it switches to an error treatment and
 * can show a retry button.
 */
@Composable
fun EmptyState(
    icon: ImageVector = Icons.Outlined.SearchOff,
    message: String,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            val isError = errorMessage != null
            androidx.compose.material3.Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isError) Icons.Outlined.WifiOff else icon,
                        null,
                        modifier = Modifier.size(36.dp),
                        tint = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                errorMessage ?: message,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            if (isError && onRetry != null) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("重试")
                }
            }
        }
    }
}

/**
 * Friendly error page mapped from [ErrorKind].
 */
@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    message: String,
    onRetry: (() -> Unit)? = null,
    kind: ErrorKind = ErrorKind.UNKNOWN
) {
    val icon = when (kind) {
        ErrorKind.NETWORK -> Icons.Outlined.WifiOff
        ErrorKind.TIMEOUT -> Icons.Outlined.Timer
        ErrorKind.CDN_BLOCKED -> Icons.Outlined.Shield
        ErrorKind.PARSE -> Icons.Outlined.BrokenImage
        ErrorKind.NOT_FOUND -> Icons.Outlined.SearchOff
        ErrorKind.UNKNOWN -> Icons.Outlined.ErrorOutline
    }
    val friendlyTitle = when (kind) {
        ErrorKind.NETWORK -> "无法连接网络"
        ErrorKind.TIMEOUT -> "请求超时"
        ErrorKind.CDN_BLOCKED -> "访问被拦截"
        ErrorKind.PARSE -> "数据解析失败"
        ErrorKind.NOT_FOUND -> "未找到数据"
        ErrorKind.UNKNOWN -> "加载失败"
    }
    val friendlyHint = when (kind) {
        ErrorKind.NETWORK -> "请检查网络连接后重试"
        ErrorKind.TIMEOUT -> "服务器响应缓慢，请稍后重试"
        ErrorKind.CDN_BLOCKED -> "请求被 CDN 拦截，请稍后重试"
        ErrorKind.PARSE -> "数据格式异常，请稍后重试"
        ErrorKind.NOT_FOUND -> "没有可展示的内容"
        ErrorKind.UNKNOWN -> "请检查网络后重试"
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(friendlyTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(friendlyHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (kind == ErrorKind.UNKNOWN && message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onRetry != null) {
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(onClick = onRetry, shape = smoothCornerShape(24.dp)) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("重试")
                }
            }
        }
    }
}

/**
 * Centered spinner used while a page has no renderable data yet.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String = "正在加载…"
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ════════════════════════════════════════════════════════
//  API resource loading state
// ════════════════════════════════════════════════════════

/**
 * Small Compose-aware holder for API resources.
 *
 * It prevents stale concurrent requests from overwriting newer data, can show cached data before a
 * network refresh finishes, and keeps offline/cache metadata close to the data it describes.
 */
@Stable
class LoadState<T> internal constructor(
    initial: T,
    private val scope: CoroutineScope,
    private val fetchRef: State<suspend (forceRefresh: Boolean) -> ApiResult<T>>,
    private val cachedFetchRef: State<(suspend () -> ApiResult<T>)?> = mutableStateOf(null)
) {
    private var activeRequestJob: Job? = null
    private var requestVersion: Long = 0L
    var data: T by mutableStateOf(initial)
        private set
    var isLoading: Boolean by mutableStateOf(true)
        private set
    var error: ApiResult.Error? by mutableStateOf(null)
        private set
    var isOffline: Boolean by mutableStateOf(false)
        private set
    var cacheAgeMs: Long by mutableLongStateOf(0L)
        private set

    /**
     * Starts a new load and cancels the previous one. [forceRefresh] bypasses the cached prefetch.
     */
    fun reload(forceRefresh: Boolean = false) {
        val currentVersion = ++requestVersion
        activeRequestJob?.cancel()
        activeRequestJob = scope.launch {
            isLoading = true
            error = null

            val cachedFetch = cachedFetchRef.value
            if (!forceRefresh && cachedFetch != null) {
                when (val cached = cachedFetch()) {
                    is ApiResult.Success -> {
                        if (currentVersion != requestVersion) return@launch
                        data = cached.value
                        isOffline = true
                        cacheAgeMs = cached.cacheAgeMs
                        isLoading = false
                    }
                    is ApiResult.Error -> {}
                }
            }

            when (val result = fetchRef.value(forceRefresh)) {
                is ApiResult.Success -> {
                    if (currentVersion != requestVersion) return@launch
                    data = result.value
                    isOffline = result.isOffline
                    cacheAgeMs = result.cacheAgeMs
                }
                is ApiResult.Error -> {
                    if (currentVersion != requestVersion) return@launch
                    if (isLoading) error = result
                }
            }
            if (currentVersion == requestVersion) {
                isLoading = false
                activeRequestJob = null
            }
        }
    }
}

/**
 * Remembers a [LoadState] and reloads whenever [key] changes.
 */
@Composable
fun <T> rememberLoadState(
    initial: T,
    key: Any? = Unit,
    fetch: suspend (forceRefresh: Boolean) -> ApiResult<T>
): LoadState<T> {
    val scope = rememberCoroutineScope()
    val fetchRef = rememberUpdatedState(fetch)
    val state = remember(scope) { LoadState(initial, scope, fetchRef) }
    LaunchedEffect(key) { state.reload() }
    return state
}

/**
 * Remembers a [LoadState] that first attempts [cachedFetch] before the network fetch.
 */
@Composable
fun <T> rememberLoadState(
    initial: T,
    key: Any? = Unit,
    cachedFetch: suspend () -> ApiResult<T>,
    fetch: suspend (forceRefresh: Boolean) -> ApiResult<T>
): LoadState<T> {
    val scope = rememberCoroutineScope()
    val fetchRef = rememberUpdatedState(fetch)
    val cachedFetchRef = rememberUpdatedState<(suspend () -> ApiResult<T>)?>(cachedFetch)
    val state = remember(scope) { LoadState(initial, scope, fetchRef, cachedFetchRef) }
    LaunchedEffect(key) { state.reload() }
    return state
}

/**
 * Standard wrapper for API-backed screens.
 *
 * It handles initial loading, empty error, offline cache banner, and optional pull-to-refresh while
 * leaving the actual success content to the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ApiResourceContent(
    state: LoadState<T>,
    modifier: Modifier = Modifier,
    isDataEmpty: (T) -> Boolean = ::defaultIsDataEmpty,
    enablePullToRefresh: Boolean = true,
    loading: @Composable (Modifier) -> Unit = { mod -> LoadingState(modifier = mod) },
    content: @Composable (T) -> Unit
) {
    val dataEmpty = isDataEmpty(state.data)
    when {
        state.isLoading && dataEmpty -> loading(modifier)
        state.error != null && dataEmpty -> {
            val err = state.error!!
            ErrorState(
                message = err.message,
                kind = err.kind,
                onRetry = { state.reload(forceRefresh = true) },
                modifier = modifier
            )
        }
        else -> {
            val body: @Composable () -> Unit = {
                Column(Modifier.fillMaxSize()) {
                    if (state.isOffline) OfflineBanner(ageMs = state.cacheAgeMs)
                    content(state.data)
                }
            }

            if (enablePullToRefresh) {
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { state.reload(forceRefresh = true) },
                    state = rememberPullToRefreshState(),
                    modifier = modifier.fillMaxSize()
                ) {
                    body()
                }
            } else {
                Box(modifier = modifier.fillMaxSize()) {
                    body()
                }
            }
        }
    }
}

private fun defaultIsDataEmpty(data: Any?): Boolean = when (data) {
    null -> true
    is Collection<*> -> data.isEmpty()
    is Map<*, *> -> data.isEmpty()
    else -> false
}
