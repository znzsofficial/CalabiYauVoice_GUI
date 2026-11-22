package com.nekolaska.calabiyau.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.core.ui.LocalSeedColor
import com.nekolaska.calabiyau.core.ui.LocalThemeMode
import com.nekolaska.calabiyau.core.usage.UsageStats
import com.nekolaska.calabiyau.feature.wiki.gallery.WallpaperApi
import com.nekolaska.calabiyau.feature.wiki.gallery.WallpaperRefresh
import kotlinx.coroutines.launch

@Composable
internal fun SettingsHome(
    concurrency: Int,
    desktopMode: Boolean,
    wallpaperAutoRefresh: Boolean,
    isActive: () -> Boolean,
    open: (SettingsPage) -> Unit
) {
    val layout by AppPrefs.homeQuickEntryLayoutState.collectAsState()
    val layoutLabel = if (layout == AppPrefs.HOME_QUICK_LAYOUT_BUTTONS) "六按钮" else "网格"
    val theme = themeModeLabel(LocalThemeMode.current.intValue)
    val wallpaperUrl by AppPrefs.wallpaperUrlState.collectAsState()
    val followsWallpaperColor = LocalSeedColor.current.intValue == AppPrefs.SEED_WALLPAPER

    Column(Modifier.fillMaxWidth()) {
        SettingsGroupHeader("个性化", topPadding = AppSpacing.small)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
        ) {
            WallpaperHeroCard(
                wallpaperUrl = wallpaperUrl,
                wallpaperAutoRefresh = wallpaperAutoRefresh,
                followsWallpaperColor = followsWallpaperColor,
                isActive = isActive,
                onOpenWallpaper = { open(SettingsPage.WALLPAPER) }
            )

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Let long labels grow rather than reducing their font or ellipsizing them.
                if (maxWidth >= 296.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)
                    ) {
                        PersonalizationBentoCard(
                            page = SettingsPage.QUICK_ENTRIES,
                            valueLabel = layoutLabel,
                            icon = Icons.Outlined.GridView,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            open = open
                        )
                        PersonalizationBentoCard(
                            page = SettingsPage.APPEARANCE,
                            valueLabel = theme,
                            icon = Icons.Outlined.Palette,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            open = open
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
                        PersonalizationBentoCard(
                            page = SettingsPage.QUICK_ENTRIES,
                            valueLabel = layoutLabel,
                            icon = Icons.Outlined.GridView,
                            modifier = Modifier.fillMaxWidth(),
                            compact = true,
                            open = open
                        )
                        PersonalizationBentoCard(
                            page = SettingsPage.APPEARANCE,
                            valueLabel = theme,
                            icon = Icons.Outlined.Palette,
                            modifier = Modifier.fillMaxWidth(),
                            compact = true,
                            open = open
                        )
                    }
                }
            }
        }

        SettingsGroupHeader("通用")
        SettingsCard {
            SettingsItem(
                icon = Icons.Outlined.Folder,
                title = SettingsPage.DOWNLOADS.title,
                value = "同时下载 $concurrency 项",
                tabularValue = true,
                onClick = { open(SettingsPage.DOWNLOADS) }
            )
            SettingsDivider()
            SettingsItem(
                icon = Icons.Outlined.Language,
                title = SettingsPage.WIKI.title,
                value = if (desktopMode) "桌面版网页" else "移动版网页",
                onClick = { open(SettingsPage.WIKI) }
            )
        }

        SettingsGroupHeader("隐私")
        var statsEnabled by remember { mutableStateOf(AppPrefs.usageStatsEnabled) }
        SettingsCard {
            SettingsToggleItem(
                icon = Icons.Outlined.Shield,
                title = "匿名使用统计",
                subtitle = "上报每日使用次数，不含账号",
                checked = statsEnabled,
                onCheckedChange = { enabled ->
                    if (isActive()) {
                        UsageStats.setEnabled(enabled)
                        statsEnabled = enabled
                    }
                }
            )
        }

        SettingsGroupHeader("应用")
        SettingsCard {
            SettingsUpdateItem(isActive = isActive)
            SettingsDivider()
            SettingsItem(
                icon = Icons.Outlined.Info,
                title = SettingsPage.ABOUT.title,
                onClick = { open(SettingsPage.ABOUT) }
            )
        }
    }
}

@Composable
private fun WallpaperHeroCard(
    wallpaperUrl: String?,
    wallpaperAutoRefresh: Boolean,
    followsWallpaperColor: Boolean,
    isActive: () -> Boolean,
    onOpenWallpaper: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isPreview = LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    val pageIsActive by rememberUpdatedState(isActive)
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshFeedback by remember(wallpaperUrl) { mutableStateOf<String?>(null) }

    val triggerRefresh: () -> Unit = {
        if (!isRefreshing && pageIsActive()) {
            isRefreshing = true
            refreshFeedback = null
            scope.launch {
                try {
                    val result = WallpaperApi.refresh(manual = true)
                    if (pageIsActive()) {
                        refreshFeedback = when (result) {
                            WallpaperRefresh.UPDATED -> null
                            WallpaperRefresh.RETAINED -> "沿用当前壁纸"
                            WallpaperRefresh.UNAVAILABLE -> "获取失败"
                        }
                    }
                } finally {
                    isRefreshing = false
                }
            }
        }
    }

    Surface(
        onClick = { if (pageIsActive()) onOpenWallpaper() },
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        color = colors.surfaceContainerLow,
        border = settingsCardBorder(highlighted = true)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Signature 2dp primary accent top hairline
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SettingsAccentBarHeight)
                    .background(colors.primary)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(width = 88.dp, height = 78.dp),
                    shape = SettingsIconFrameShape,
                    color = colors.surfaceContainerHigh,
                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(colors.primaryContainer, colors.tertiaryContainer)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Wallpaper,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier.size(26.dp)
                        )
                        if (!wallpaperUrl.isNullOrBlank() && !isPreview) {
                            AsyncImage(
                                model = wallpaperUrl,
                                contentDescription = "当前壁纸缩略图",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (isRefreshing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.36f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
                    ) {
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
                        ) {
                            Text(
                                text = SettingsPage.WALLPAPER.title,
                                style = AppTextStyles.cardTitle.copy(fontWeight = FontWeight.Bold),
                                color = colors.onSurface,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            if (followsWallpaperColor) {
                                SettingsValueBadge(
                                    text = "跟随取色",
                                    highlighted = true,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            }
                        }
                        SettingsNavigationChevron()
                    }

                    Text(
                        text = refreshFeedback ?: if (wallpaperAutoRefresh) "启动时随机更换" else "手动更换",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Surface(
                        onClick = triggerRefresh,
                        enabled = !isRefreshing && pageIsActive(),
                        shape = SettingsBadgeShape,
                        color = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.32f))
                    ) {
                        Row(
                            modifier = Modifier
                                .heightIn(min = 30.dp)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 1.8.dp,
                                    color = colors.onPrimaryContainer
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = if (isRefreshing) "更换中…" else "换一张",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalizationBentoCard(
    page: SettingsPage,
    valueLabel: String,
    icon: ImageVector,
    modifier: Modifier,
    compact: Boolean = false,
    open: (SettingsPage) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = { open(page) },
        modifier = modifier,
        shape = SettingsCardShape,
        color = colors.surfaceContainerLow,
        border = settingsCardBorder()
    ) {
        if (compact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .padding(horizontal = SettingsRowHorizontalPadding, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsFramedIcon(icon = icon)
                Spacer(Modifier.width(SettingsRowIconGap))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
                ) {
                    Text(
                        text = page.title,
                        style = AppTextStyles.settingsItemTitle.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                    SettingsValueBadge(text = valueLabel)
                }
                Spacer(Modifier.width(AppSpacing.small))
                SettingsNavigationChevron()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SettingsFramedIcon(icon = icon)
                    SettingsNavigationChevron()
                }
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(
                        text = page.title,
                        style = AppTextStyles.settingsItemTitle.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                    SettingsValueBadge(text = valueLabel)
                }
            }
        }
    }
}
