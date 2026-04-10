package com.nekolaska.calabiyau.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
fun SearchBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    isSearching: Boolean,
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
                "搜索角色名称...",
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
    kind: data.ErrorKind = data.ErrorKind.UNKNOWN
) {
    val icon = when (kind) {
        data.ErrorKind.NETWORK -> Icons.Outlined.WifiOff
        data.ErrorKind.TIMEOUT -> Icons.Outlined.Timer
        data.ErrorKind.CDN_BLOCKED -> Icons.Outlined.Shield
        data.ErrorKind.PARSE -> Icons.Outlined.BrokenImage
        data.ErrorKind.NOT_FOUND -> Icons.Outlined.SearchOff
        data.ErrorKind.UNKNOWN -> Icons.Outlined.ErrorOutline
    }
    val friendlyTitle = when (kind) {
        data.ErrorKind.NETWORK -> "无法连接网络"
        data.ErrorKind.TIMEOUT -> "请求超时"
        data.ErrorKind.CDN_BLOCKED -> "访问被拦截"
        data.ErrorKind.PARSE -> "数据解析失败"
        data.ErrorKind.NOT_FOUND -> "未找到数据"
        data.ErrorKind.UNKNOWN -> "加载失败"
    }
    val friendlyHint = when (kind) {
        data.ErrorKind.NETWORK -> "请检查网络连接后重试"
        data.ErrorKind.TIMEOUT -> "服务器响应缓慢，请稍后重试"
        data.ErrorKind.CDN_BLOCKED -> "请求被 CDN 拦截，请稍后重试"
        data.ErrorKind.PARSE -> "数据格式异常，请稍后重试"
        data.ErrorKind.NOT_FOUND -> "没有可展示的内容"
        data.ErrorKind.UNKNOWN -> "请检查网络后重试"
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
            if (kind == data.ErrorKind.UNKNOWN && message.isNotBlank()) {
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
    shape: androidx.compose.ui.graphics.Shape = smoothCornerShape(8.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .shimmerEffect()
    )
}

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
        val launcher: (String) -> Unit = { msg -> scope.launch { host.showSnackbar(msg) }; Unit }
        launcher
    }
}
