package com.nekolaska.calabiyau.feature.settings

import android.content.Context
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.tools.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class DirSizeInfo(
    val name: String,
    val size: Long
)

data class StorageSnapshot(
    val downloadTotalSize: Long = 0L,
    val downloadFileCount: Int = 0,
    val subDirSizes: List<DirSizeInfo> = emptyList(),
    val cacheSizes: Map<CacheCategory, Long> = emptyMap()
) {
    val cacheTotalSize: Long get() = cacheSizes.values.sum()
    val totalSize: Long get() = downloadTotalSize + cacheTotalSize
}

private data class StorageSegment(
    val label: String,
    val size: Long,
    val color: Color
)

enum class CacheCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Long
) {
    OFFLINE("离线数据", "Wiki 页面 JSON 缓存", Icons.Outlined.Cached, 0xFF4F8CFF),
    IMAGE("图片缓存", "Coil 磁盘缓存", Icons.Outlined.Image, 0xFF22C55E),
    WEBVIEW("网页缓存", "WebView 页面与脚本缓存", Icons.Outlined.Language, 0xFFF59E0B),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageSettingsScreen(
    snapshot: StorageSnapshot?,
    isCalculating: Boolean,
    onRefreshSnapshot: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showSnack = rememberSnackbarLauncher()
    var clearingCategory by remember { mutableStateOf<CacheCategory?>(null) }
    var isClearingAll by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var offlineCacheNeverExpire by remember { mutableStateOf(AppPrefs.offlineCacheNeverExpire) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = AppSpacing.screen, vertical = AppSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(AppSpacing.iconGap))
                Text(
                    "存储空间",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = AppSpacing.xxxLarge)
        ) {
            SettingsGroupHeader("总览")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.screen),
                shape = AppShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(Modifier.padding(AppSpacing.cardContent), verticalArrangement = Arrangement.spacedBy(AppSpacing.sectionGap)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("已用空间", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(
                                if (isCalculating) "计算中…" else formatFileSize(snapshot?.totalSize ?: 0L),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "下载目录与应用缓存合计",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isCalculating) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }

                    val current = snapshot
                    if (current != null && !isCalculating) {
                        StorageUsageChart(
                            segments = buildStorageSegments(current)
                        )
                    }
                }
            }

            SettingsGroupHeader("下载目录")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.screen),
                shape = AppShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                StorageStatisticsCard(snapshot = snapshot, isCalculating = isCalculating)
            }

            SettingsGroupHeader("缓存")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.screen),
                shape = AppShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
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
                    HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))

                    CacheCategory.entries.forEach { category ->
                        CacheCategoryRow(
                            category = category,
                            size = snapshot?.cacheSizes?.get(category) ?: 0L,
                            isClearing = clearingCategory == category,
                            enabled = !isCalculating && clearingCategory == null && !isClearingAll,
                            modifier = Modifier.padding(horizontal = AppSpacing.cardContent),
                            onClear = {
                                scope.launch {
                                    clearingCategory = category
                                    withContext(Dispatchers.IO) { clearCache(context, category) }
                                    clearingCategory = null
                                    onRefreshSnapshot()
                                    showSnack("${category.title}已清除")
                                }
                            }
                        )
                        if (category != CacheCategory.entries.last()) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
                    Box(Modifier.padding(AppSpacing.screen)) {
                        FilledTonalButton(
                            enabled = (snapshot?.cacheTotalSize ?: 0L) > 0L && !isCalculating && clearingCategory == null && !isClearingAll,
                            onClick = { showClearAllConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            if (isClearingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
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
        AlertDialog(
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
                            onRefreshSnapshot()
                            showSnack("所有缓存已清除")
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    if (isClearingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onErrorContainer
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
    val visibleSegments = segments.filter { it.size > 0L }
    if (visibleSegments.isEmpty()) {
        Text(
            "暂无可展示的数据",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val total = visibleSegments.sumOf { it.size }.coerceAtLeast(1L)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(smoothCornerShape(9.dp))
        ) {
            visibleSegments.forEach { segment ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight((segment.size.toFloat() / total).coerceAtLeast(0.02f))
                        .background(segment.color)
                )
            }
        }

        visibleSegments.forEach { segment ->
            val percent = segment.size * 100f / total
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(segment.color)
                )
                Spacer(Modifier.width(AppSpacing.itemGap))
                Text(
                    segment.label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatFileSize(segment.size)} · ${String.format(Locale.ROOT, "%.1f", percent)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun buildStorageSegments(snapshot: StorageSnapshot): List<StorageSegment> {
    return listOf(
        StorageSegment("下载目录", snapshot.downloadTotalSize, MaterialTheme.colorScheme.primary),
        StorageSegment(CacheCategory.OFFLINE.title, snapshot.cacheSizes[CacheCategory.OFFLINE] ?: 0L, Color(CacheCategory.OFFLINE.color)),
        StorageSegment(CacheCategory.IMAGE.title, snapshot.cacheSizes[CacheCategory.IMAGE] ?: 0L, Color(CacheCategory.IMAGE.color)),
        StorageSegment(CacheCategory.WEBVIEW.title, snapshot.cacheSizes[CacheCategory.WEBVIEW] ?: 0L, Color(CacheCategory.WEBVIEW.color))
    )
}

@Composable
private fun StorageStatisticsCard(snapshot: StorageSnapshot?, isCalculating: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpacing.cardContent)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Storage, null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(AppSpacing.sectionGap))
            Column(Modifier.weight(1f)) {
                Text(
                    "已用空间",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (isCalculating) {
                    Text(
                        "计算中...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "${formatFileSize(snapshot?.downloadTotalSize ?: 0)}  ·  ${snapshot?.downloadFileCount ?: 0} 个文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isCalculating) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        val subDirSizes = snapshot?.subDirSizes.orEmpty()
        if (!isCalculating && subDirSizes.isNotEmpty()) {
            Spacer(Modifier.height(AppSpacing.sectionGap))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(AppSpacing.large))

            subDirSizes.forEach { dirInfo ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Folder,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.width(AppSpacing.itemGap))
                    Text(
                        text = dirInfo.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatFileSize(dirInfo.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
    modifier: Modifier = Modifier,
    onClear: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            category.icon,
            null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(AppSpacing.iconGap))
        Column(Modifier.weight(1f)) {
            Text(
                category.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (size > 0) formatFileSize(size) else category.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (isClearing) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            IconButton(
                onClick = onClear,
                enabled = enabled && size > 0,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "清除${category.title}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error.copy(
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

internal suspend fun computeStorageSnapshot(
    context: Context,
    savePath: String
): StorageSnapshot = withContext(Dispatchers.IO) {
    val downloadInfo = computeDownloadStorageInfo(savePath)
    StorageSnapshot(
        downloadTotalSize = downloadInfo.totalSize,
        downloadFileCount = downloadInfo.fileCount,
        subDirSizes = downloadInfo.subDirSizes,
        cacheSizes = computeAllCacheSizes(context)
    )
}

private data class DownloadStorageInfo(
    val totalSize: Long,
    val fileCount: Int,
    val subDirSizes: List<DirSizeInfo>
)

private fun computeDownloadStorageInfo(savePath: String): DownloadStorageInfo {
    return try {
        val root = File(savePath)
        if (!root.exists() || !root.isDirectory) {
            return DownloadStorageInfo(0L, 0, emptyList())
        }
        val totalSize = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val fileCount = root.walkTopDown().count { it.isFile }
        val subDirSizes = root.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.map { dir ->
                DirSizeInfo(
                    name = dir.name,
                    size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                )
            }
            ?.filter { it.size > 0 }
            ?.sortedByDescending { it.size }
            ?.take(8)
            ?.toList()
            ?: emptyList()
        DownloadStorageInfo(totalSize, fileCount, subDirSizes)
    } catch (_: Exception) {
        DownloadStorageInfo(0L, 0, emptyList())
    }
}

private suspend fun clearCache(
    context: Context,
    category: CacheCategory
) = withContext(Dispatchers.IO) {
    when (category) {
        CacheCategory.OFFLINE -> {
            OfflineCache.clearAll()
            OfflineCache.clearMemoryCaches()
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

private fun calculateWebViewCacheSize(context: Context): Long {
    var total = 0L
    val dataDir = context.dataDir
    val cacheDirs = listOf(
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
    for (dir in cacheDirs) {
        if (dir.exists() && dir.isDirectory) {
            total += dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }
    return total
}
