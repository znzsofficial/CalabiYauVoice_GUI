package com.nekolaska.calabiyau.ui.shared

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun SearchBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    isSearching: Boolean,
    placeholder: String = "搜索角色名称...",
    modifier: Modifier = Modifier
) {
    TextField(
        value = keyword,
        onValueChange = onKeywordChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = smoothCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        placeholder = {
            Text(
                placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            if (isSearching) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.Search, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingIcon = {
            if (keyword.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, "清空")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

@Composable
fun DownloadStatusBar(progress: Float, statusText: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "下载中",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = smoothCornerShape(12.dp)
                ) {
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(smoothCornerShape(4.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LogsDialog(logs: List<String>, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = smoothCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "运行日志",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    FilledTonalIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (logs.isEmpty()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无日志",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(logs.reversed()) { log ->
                            val isError = log.startsWith("[错误]")
                            Surface(
                                color = if (isError)
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                else
                                    Color.Transparent,
                                shape = smoothCornerShape(8.dp)
                            ) {
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 18.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = if (isError) MaterialTheme.colorScheme.errorContainer
                       else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isError) Icons.Outlined.WifiOff else icon,
                        null,
                        modifier = Modifier.size(36.dp),
                        tint = if (isError) MaterialTheme.colorScheme.onErrorContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                errorMessage ?: message,
                color = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
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
 * 统一的请求失败/错误状态组件。
 *
 * 根据 [kind] 选择图标和友好提示语。
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
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
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon, null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                friendlyTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                friendlyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            // 仅在未识别的通用错误时显示原始信息作为补充
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
                FilledTonalButton(
                    onClick = onRetry,
                    shape = smoothCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("重试")
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  通用区块标题
// ────────────────────────────────────────────

/**
 * 卡片内的区块标题（图标 + 标题文字）。
 *
 * 用于 CharacterDetail、WeaponDetail、MapDetail 等详情页的各区块。
 */
@Composable
fun SectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

// ────────────────────────────────────────────
//  信息标签 Chip
// ────────────────────────────────────────────

/**
 * 带图标的信息标签，用于角色/武器的属性标签行。
 */
@Composable
fun InfoChip(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = smoothCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

// ────────────────────────────────────────────
//  加载状态
// ────────────────────────────────────────────

/**
 * 统一的加载中状态组件，与 [EmptyState] / [ErrorState] 风格一致。
 */
@Composable
fun LoadingState(
    message: String = "正在加载…",
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ────────────────────────────────────────────
//  Shimmer / 骨架屏基础
// ────────────────────────────────────────────

/**
 * 为组件添加 shimmer 光泽扫过动画。
 * 颜色自动适配 MaterialTheme，无需外部库。
 */
@Composable
fun Modifier.shimmerEffect(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 300f, 0f)
    )
    return this.background(brush)
}

/**
 * 骨架屏占位块。
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = smoothCornerShape(8.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .shimmerEffect()
    )
}

// ────────────────────────────────────────────
//  骨架屏通用片段组件
// ────────────────────────────────────────────

/** 骨架屏卡片容器 —— 统一详情页骨架屏的卡片圆角和间距。 */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = smoothCornerShape(24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

/** 骨架屏标题行（宽度约 100dp，高度 18dp）。 */
@Composable
fun SkeletonSectionTitle(modifier: Modifier = Modifier) {
    ShimmerBox(modifier.width(100.dp).height(18.dp))
}

/** 骨架屏属性网格行（2 列，每列 label + value）。 */
@Composable
fun SkeletonStatRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        SkeletonStatCell()
        SkeletonStatCell()
    }
}

/** 骨架屏标签行（模拟 InfoChip / 标签组）。 */
@Composable
fun SkeletonChipRow(
    count: Int = 3,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            ShimmerBox(
                Modifier
                    .width(skeletonChipWidth(index))
                    .height(28.dp),
                shape = smoothCapsuleShape()
            )
        }
    }
}

/** 骨架屏文本行（模拟一行文字，可指定宽度比例）。 */
@Composable
fun SkeletonTextLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.85f,
    height: Dp = 14.dp
) {
    ShimmerBox(modifier.fillMaxWidth(widthFraction).height(height))
}

@Composable
private fun RowScope.SkeletonStatCell() {
    Column(Modifier.weight(1f)) {
        ShimmerBox(Modifier.width(40.dp).height(10.dp))
        Spacer(Modifier.height(4.dp))
        ShimmerBox(Modifier.width(60.dp).height(14.dp))
    }
}

private fun skeletonChipWidth(index: Int) = if (index == 0) 72.dp else (64 - index * 4).dp

// ────────────────────────────────────────────
//  离线提示横条
// ────────────────────────────────────────────

/**
 * 展示"离线数据 · N 小时前"的横条。
 * 由 [ApiResult.Success.isOffline] 与 [ApiResult.Success.cacheAgeMs] 驱动。
 */
@Composable
fun OfflineBanner(
    ageMs: Long,
    modifier: Modifier = Modifier
) {
    val label = remember(ageMs) {
        buildString {
            append("离线数据")
            if (ageMs > 0) {
                val minutes = ageMs / 60_000
                val hours = minutes / 60
                val days = hours / 24
                append(" · ")
                append(
                    when {
                        days >= 1 -> "$days 天前"
                        hours >= 1 -> "$hours 小时前"
                        minutes >= 1 -> "$minutes 分钟前"
                        else -> "刚刚"
                    }
                )
            }
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

// ────────────────────────────────────────────
//  全局 Snackbar 宿主
// ────────────────────────────────────────────

/**
 * 由 [MainScreen] 提供的全局 [SnackbarHostState]。
 * 任意子组件通过 [rememberSnackbarLauncher] 获取发送器。
 */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided. Wrap content in MainScreen's CompositionLocalProvider.")
}

/**
 * 返回一个简单的 `(String) -> Unit` 发送器，便于替代 Toast。
 *
 * 用法：
 * ```
 * val showSnack = rememberSnackbarLauncher()
 * // …
 * showSnack("已保存: $fileName")
 * ```
 */
@Composable
fun rememberSnackbarLauncher(): (String) -> Unit {
    val host = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    return remember(host, scope) {
        val launcher: (String) -> Unit = { msg ->
            scope.launch { host.showSnackbar(msg) }
        }
        launcher
    }
}

// ════════════════════════════════════════════════════════
//  LoadState<T> —— 列表/详情屏幕统一的加载状态容器
// ════════════════════════════════════════════════════════

/**
 * 承载列表/详情屏幕的 "loading / data / error / offline" 四态的容器，
 * 把每个屏幕重复的 `var isLoading by remember / var errorResult by remember / ... /
 * fun loadData(force) { scope.launch { ... when(result) { ... } } }` 样板收敛到一处。
 *
 * 用法：
 * ```kotlin
 * val state = rememberLoadState(emptyList<FactionData>()) { force ->
 *     CharacterListApi.fetchAllFactions(force)
 * }
 * // state.data / state.isLoading / state.error / state.isOffline / state.cacheAgeMs
 * // state.reload(forceRefresh = true)           // 手动重载
 * ```
 *
 * 搭配 [ApiResourceContent] 可以进一步收敛 UI 层的 `when { loading -> ...; error -> ...; else -> PullToRefresh }` 样板。
 */
@Stable
class LoadState<T> internal constructor(
    initial: T,
    private val scope: CoroutineScope,
    private val fetchRef: State<suspend (forceRefresh: Boolean) -> ApiResult<T>>
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

    /** 触发一次加载；`forceRefresh = true` 时绕过内存 + 磁盘缓存。 */
    fun reload(forceRefresh: Boolean = false) {
        val currentVersion = ++requestVersion
        activeRequestJob?.cancel()
        activeRequestJob = scope.launch {
            isLoading = true
            error = null
            when (val result = fetchRef.value(forceRefresh)) {
                is ApiResult.Success -> {
                    if (currentVersion != requestVersion) return@launch
                    data = result.value
                    isOffline = result.isOffline
                    cacheAgeMs = result.cacheAgeMs
                }
                is ApiResult.Error -> {
                    if (currentVersion != requestVersion) return@launch
                    error = result
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
 * 创建一个 [LoadState] 并在首次组合时自动加载数据。
 *
 * @param initial 数据的初始值（通常是 `emptyList()`），同时决定 `T` 的类型推断。
 * @param key 变化时重新触发加载——适用于 pageName 等参数化屏幕；默认 `Unit` 只加载一次。
 * @param fetch 抓取数据的挂起函数。接收 `forceRefresh` 参数以区分首次加载与重试。
 */
@Composable
fun <T> rememberLoadState(
    initial: T,
    key: Any? = Unit,
    fetch: suspend (forceRefresh: Boolean) -> ApiResult<T>
): LoadState<T> {
    val scope = rememberCoroutineScope()
    // 捕获最新的 fetch lambda，避免 remember 固化旧闭包
    val fetchRef = rememberUpdatedState(fetch)
    val state = remember(scope) { LoadState(initial, scope, fetchRef) }
    LaunchedEffect(key) { state.reload() }
    return state
}

// ════════════════════════════════════════════════════════
//  ApiResourceContent —— 加载 / 错误 / 下拉刷新 / 离线横幅 四合一包装
// ════════════════════════════════════════════════════════

/**
 * 列表屏幕常用的四态 UI 包装：
 * - 数据为空 + 正在加载 → 骨架屏 / LoadingState
 * - 数据为空 + 有错误 → 错误占位（带 retry）
 * - 其他 → PullToRefreshBox 包裹的内容，首行按需显示离线横幅
 *
 * 不符合这个模板的屏幕（例如 BalanceDataScreen 这种多数据源）可以直接
 * 使用 [LoadState] 自己写 when 分支。
 *
 * @param loading 自定义加载占位；默认用通用 [LoadingState]
 * @param isDataEmpty 判断数据是否为空的谓词；默认对 Collection / Map / null 生效
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
