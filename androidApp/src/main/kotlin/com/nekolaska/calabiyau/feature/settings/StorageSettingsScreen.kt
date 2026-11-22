package com.nekolaska.calabiyau.feature.settings

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSizes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.tools.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private data class StorageSnapshot(
    val download: DownloadStorageInfo,
    val cacheSizes: Map<CacheCategory, Long>
) {
    val cacheTotalSize: Long get() = cacheSizes.values.sum()
    val knownSize: Long get() = download.totalSize + cacheTotalSize
}

private data class StorageSegment(
    val label: String,
    val size: Long,
    val color: Color
)

enum class CacheCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    OFFLINE("离线数据", "Wiki 页面 JSON 缓存", Icons.Outlined.Cached),
    IMAGE("图片缓存", "Coil 磁盘缓存", Icons.Outlined.Image),
    WEBVIEW("网页缓存", "WebView 页面与脚本缓存", Icons.Outlined.Language),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var snapshot by remember(revision) { mutableStateOf<StorageSnapshot?>(null) }
    val isCalculating = snapshot == null
    LaunchedEffect(revision) { snapshot = computeStorageSnapshot(context) }
    val scope = rememberCoroutineScope()
    val showSnack = rememberSnackbarLauncher()
    var clearingCategory by remember { mutableStateOf<CacheCategory?>(null) }
    var isClearingAll by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var offlineCacheNeverExpire by remember { mutableStateOf(AppPrefs.offlineCacheNeverExpire) }
    val colors = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("存储空间", style = AppTextStyles.topBarTitle) },
                navigationIcon = { BackNavButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background
                )
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = AppSpacing.small, bottom = AppSpacing.xxxLarge)
            ) {
                SettingsGroupHeader("总览", topPadding = AppSpacing.small)
                SettingsCard(highlighted = true, accentTopBar = true) {
                    Column(
                        modifier = Modifier.padding(AppSpacing.screen),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sectionGap)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SettingsFramedIcon(
                                icon = Icons.Outlined.Storage,
                                size = 44.dp,
                                iconSize = 22.dp,
                                containerColor = colors.primaryContainer,
                                borderColor = colors.primary.copy(alpha = 0.3f),
                                iconTint = colors.onPrimaryContainer
                            )
                            Spacer(Modifier.width(SettingsRowIconGap))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)
                            ) {
                                Text(
                                    text = "已用空间",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.4.sp
                                    ),
                                    color = colors.primary
                                )
                                val current = snapshot
                                Text(
                                    text = when {
                                        current == null -> "计算中…"
                                        current.download.coverage == ScanCoverage.Complete -> formatFileSize(current.knownSize)
                                        current.knownSize > 0 -> "已统计 ${formatFileSize(current.knownSize)}"
                                        else -> "用量未知"
                                    },
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = colors.onSurface
                                )
                                Text(
                                    text = when (current?.download?.coverage) {
                                        ScanCoverage.RootUnavailable -> "仅统计应用缓存，下载目录无法读取"
                                        ScanCoverage.Partial -> "仅统计可读取的下载内容与应用缓存"
                                        else -> "下载目录与应用缓存合计"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            if (isCalculating) {
                                CircularProgressIndicator(Modifier.size(AppSizes.iconLarge), strokeWidth = 2.dp)
                            }
                        }

                        val current = snapshot
                        if (current != null && current.download.coverage == ScanCoverage.Complete) {
                            StorageUsageChart(segments = buildStorageSegments(current))
                        }
                    }
                }

                SettingsGroupHeader("下载目录")
                SettingsCard {
                    StorageStatisticsCard(snapshot?.download, onRetry = { revision++ })
                }

                SettingsGroupHeader("缓存")
                SettingsCard {
                    SettingsToggleItem(
                        icon = Icons.Outlined.EventRepeat,
                        title = "离线缓存不自动过期",
                        subtitle = if (offlineCacheNeverExpire) "仅手动清除" else "按有效期自动清理",
                        checked = offlineCacheNeverExpire,
                        onCheckedChange = {
                            offlineCacheNeverExpire = it
                            AppPrefs.offlineCacheNeverExpire = it
                        }
                    )
                    SettingsDivider()

                    CacheCategory.entries.forEach { category ->
                        CacheCategoryRow(
                            category = category,
                            size = snapshot?.cacheSizes?.get(category) ?: 0L,
                            isClearing = clearingCategory == category,
                            enabled = !isCalculating && clearingCategory == null && !isClearingAll,
                            onClear = {
                                scope.launch {
                                    clearingCategory = category
                                    clearCache(context, category)
                                    clearingCategory = null
                                    revision++
                                    showSnack(
                                        if (category == CacheCategory.WEBVIEW) "已清理网页缓存，使用时会重新生成"
                                        else "${category.title}已清除"
                                    )
                                }
                            }
                        )
                        if (category != CacheCategory.entries.last()) {
                            SettingsDivider()
                        }
                    }

                    SettingsDivider(startIndent = SettingsRowHorizontalPadding)
                    Box(Modifier.padding(AppSpacing.screen)) {
                        FilledTonalButton(
                            enabled = (snapshot?.cacheTotalSize ?: 0L) > 0L && !isCalculating && clearingCategory == null && !isClearingAll,
                            onClick = { showClearAllConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = SettingsIconFrameShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = colors.errorContainer,
                                contentColor = colors.onErrorContainer
                            )
                        ) {
                            if (isClearingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(AppSizes.iconXSmall),
                                    strokeWidth = 2.dp,
                                    color = colors.onErrorContainer
                                )
                                Spacer(Modifier.width(AppSpacing.itemGap))
                            }
                            Text("清除所有缓存")
                        }
                    }
                }
            }
        }
    }

    if (showClearAllConfirm) {
        SettingsDialog(
            onDismissRequest = { if (!isClearingAll) showClearAllConfirm = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
            title = { Text("清除所有缓存？") },
            text = { Text("将清除离线数据、图片缓存和网页缓存。已下载到保存目录的文件不会被删除。") },
            shape = AppShapes.dialog,
            confirmButton = {
                FilledTonalButton(
                    enabled = !isClearingAll,
                    onClick = {
                        scope.launch {
                            isClearingAll = true
                            withContext(Dispatchers.IO) {
                                CacheCategory.entries.forEach { clearCache(context, it) }
                            }
                            isClearingAll = false
                            showClearAllConfirm = false
                            revision++
                            showSnack("已清理缓存，使用过程中会重新生成")
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.errorContainer,
                        contentColor = colors.onErrorContainer
                    )
                ) {
                    if (isClearingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AppSizes.iconXSmall),
                            strokeWidth = 2.dp,
                            color = colors.onErrorContainer
                        )
                        Spacer(Modifier.width(AppSpacing.itemGap))
                    }
                    Text("确认清除")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isClearingAll,
                    onClick = { showClearAllConfirm = false }
                ) { Text("取消") }
            }
        )
    }
}

@Composable
private fun StorageUsageChart(segments: List<StorageSegment>) {
    val colors = MaterialTheme.colorScheme
    val visibleSegments = segments.filter { it.size > 0L }
    if (visibleSegments.isEmpty()) {
        Text(
            "暂无可展示的数据",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        return
    }
    val total = visibleSegments.sumOf { it.size }.coerceAtLeast(1L)

    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
            shape = SettingsIconFrameShape,
            color = colors.surfaceContainerHigh,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                visibleSegments.forEach { segment ->
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            // Do not inflate small categories: the legend keeps them discoverable.
                            .weight(segment.size.toFloat() / total)
                            .background(segment.color)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            visibleSegments.forEach { segment ->
                val percent = segment.size * 100f / total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(smoothCornerShape(3.dp))
                            .background(segment.color)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = segment.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(AppSpacing.medium))
                    Text(
                        text = formatFileSize(segment.size),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontFeatureSettings = "tnum"
                        ),
                        color = colors.onSurface,
                        textAlign = TextAlign.End
                    )
                    Spacer(Modifier.width(AppSpacing.medium))
                    Text(
                        text = "${String.format(Locale.ROOT, "%.1f", percent)}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(min = 42.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun buildStorageSegments(snapshot: StorageSnapshot): List<StorageSegment> {
    return listOf(
        StorageSegment("下载目录", snapshot.download.totalSize, MaterialTheme.colorScheme.primary),
        StorageSegment(CacheCategory.OFFLINE.title, snapshot.cacheSizes[CacheCategory.OFFLINE] ?: 0L, MaterialTheme.colorScheme.secondary),
        StorageSegment(CacheCategory.IMAGE.title, snapshot.cacheSizes[CacheCategory.IMAGE] ?: 0L, MaterialTheme.colorScheme.tertiary),
        StorageSegment(CacheCategory.WEBVIEW.title, snapshot.cacheSizes[CacheCategory.WEBVIEW] ?: 0L, MaterialTheme.colorScheme.outline)
    )
}

@Composable
private fun StorageStatisticsCard(download: DownloadStorageInfo?, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpacing.screen)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingsFramedIcon(icon = Icons.Outlined.Folder)
            Spacer(Modifier.width(SettingsRowIconGap))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)
            ) {
                Text(
                    text = "已用空间",
                    style = AppTextStyles.settingsItemTitle,
                    color = colors.onSurface
                )
                Text(
                    text = when (download?.coverage) {
                        null -> "计算中…"
                        ScanCoverage.RootUnavailable -> "无法读取下载目录，请检查保存位置或访问权限"
                        ScanCoverage.Partial -> if (download.fileCount == 0) "部分内容无法读取，用量未知"
                            else "部分内容无法读取，已统计 ${formatFileSize(download.totalSize)} · ${download.fileCount} 个文件"
                        ScanCoverage.Complete -> "${formatFileSize(download.totalSize)} · ${download.fileCount} 个文件"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = colors.onSurfaceVariant
                )
            }
            if (download == null) {
                CircularProgressIndicator(Modifier.size(AppSizes.iconMedium), strokeWidth = 2.dp)
            } else if (download.coverage != ScanCoverage.Complete) {
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }

        val subDirSizes = download?.subDirSizes.orEmpty()
        if (subDirSizes.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(thickness = 1.dp, color = settingsDividerColor())
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                subDirSizes.forEach { dirInfo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(AppSizes.iconXSmall),
                            tint = colors.primary.copy(alpha = 0.72f)
                        )
                        Spacer(Modifier.width(AppSpacing.itemGap))
                        Text(
                            text = dirInfo.name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.onSurface
                        )
                        Spacer(Modifier.width(AppSpacing.medium))
                        Text(
                            text = when {
                                !dirInfo.incomplete -> formatFileSize(dirInfo.size)
                                dirInfo.size > 0 -> "已统计 ${formatFileSize(dirInfo.size)}"
                                else -> "用量未知"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontFeatureSettings = "tnum"
                            ),
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CacheCategoryRow(
    category: CacheCategory,
    size: Long,
    isClearing: Boolean,
    enabled: Boolean,
    onClear: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(
                start = SettingsRowHorizontalPadding,
                end = AppSpacing.medium,
                top = 10.dp,
                bottom = 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsFramedIcon(icon = category.icon)
        Spacer(Modifier.width(SettingsRowIconGap))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)
        ) {
            Text(
                text = category.title,
                style = AppTextStyles.settingsItemTitle,
                color = colors.onSurface
            )
            Text(
                text = category.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(AppSpacing.medium))
        Text(
            text = formatFileSize(size),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum"
            ),
            color = if (size > 0) colors.onSurface else colors.onSurfaceVariant,
            textAlign = TextAlign.End
        )
        Spacer(Modifier.width(AppSpacing.xSmall))
        if (isClearing) {
            Box(Modifier.size(AppSizes.iconButton), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(AppSizes.iconSmall), strokeWidth = 2.dp)
            }
        } else {
            IconButton(
                onClick = onClear,
                enabled = enabled && size > 0
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "清除${category.title}",
                    modifier = Modifier.size(AppSizes.iconSmall),
                    tint = colors.error.copy(
                        alpha = if (enabled && size > 0) 1f else 0.3f
                    )
                )
            }
        }
    }
}

// ─────────────────────── 存储计算工具 ───────────────────────

internal suspend fun computeAllCacheSizes(
    context: Context
): Map<CacheCategory, Long> = withContext(Dispatchers.IO) {
    val loader = SingletonImageLoader.get(context)
    mapOf(
        CacheCategory.OFFLINE to OfflineCache.totalSize(),
        CacheCategory.IMAGE to (loader.diskCache?.size ?: 0L),
        CacheCategory.WEBVIEW to calculateWebViewCacheSize(context),
    )
}

private suspend fun computeStorageSnapshot(context: Context): StorageSnapshot = withContext(Dispatchers.IO) {
    val downloadInfo = scanDownloadStorage(File(AppPrefs.savePath)) { file ->
        try {
            val stat = Os.lstat(file.absolutePath)
            when {
                OsConstants.S_ISDIR(stat.st_mode) -> StorageEntry.Directory
                OsConstants.S_ISREG(stat.st_mode) -> StorageEntry.RegularFile(stat.st_size)
                else -> StorageEntry.Skipped
            }
        } catch (_: ErrnoException) { StorageEntry.Unreadable }
    }
    StorageSnapshot(
        download = downloadInfo,
        cacheSizes = computeAllCacheSizes(context)
    )
}

private suspend fun clearCache(
    context: Context,
    category: CacheCategory
) = withContext(Dispatchers.IO) {
    when (category) {
        CacheCategory.OFFLINE -> {
            OfflineCache.clearAll()
        }

        CacheCategory.IMAGE -> {
            val loader = SingletonImageLoader.get(context)
            loader.diskCache?.clear()
            loader.memoryCache?.clear()
        }

        CacheCategory.WEBVIEW -> {
            withContext(Dispatchers.Main) {
                WebView(context).apply {
                    clearCache(true)
                    clearHistory()
                    destroy()
                }
            }
        }
    }
}

private suspend fun calculateWebViewCacheSize(context: Context): Long {
    val scanContext = currentCoroutineContext()
    var total = 0L
    for (dir in webViewCacheDirs(context)) {
        if (dir.exists() && dir.isDirectory) {
            total += dir.walkTopDown().sumOf { file ->
                scanContext.ensureActive()
                if (file.isFile) file.length() else 0L
            }
        }
    }
    return total
}

/** Known WebView cache locations, used only for estimating disk usage. */
private fun webViewCacheDirs(context: Context): List<File> {
    val dataDir = context.dataDir
    return listOf(
        File(context.cacheDir, "WebView"),
        File(context.codeCacheDir, "WebView"),
        File(dataDir, "app_webview/Cache"),
        File(dataDir, "app_webview/Code Cache"),
        File(dataDir, "app_webview/Default/Cache"),
        File(dataDir, "app_webview/Default/HTTP Cache"),
        File(dataDir, "app_webview/Default/Code Cache"),
        File(dataDir, "app_webview/GPUCache"),
        File(dataDir, "app_webview/Default/GPUCache")
    ).mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
        .distinctBy { it.path }
}
