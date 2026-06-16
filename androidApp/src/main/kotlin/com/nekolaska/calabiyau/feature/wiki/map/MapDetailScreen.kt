package com.nekolaska.calabiyau.feature.wiki.map

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.feature.wiki.map.api.MapDetailApi
import com.nekolaska.calabiyau.feature.wiki.map.model.MapDetail
import com.nekolaska.calabiyau.feature.wiki.map.model.UpdateEntry
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.ImagePreviewDialog
import com.nekolaska.calabiyau.core.ui.OpenWikiActionButton
import com.nekolaska.calabiyau.core.ui.PreviewImage
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.SectionTitle
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.SkeletonCard
import com.nekolaska.calabiyau.core.ui.SkeletonChipRow
import com.nekolaska.calabiyau.core.ui.SkeletonSectionTitle
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import java.net.URLEncoder

// ════════════════════════════════════════════════════════
//  地图详情页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

private val HeaderCardPadding = 16.dp
private val HeaderCardVerticalPadding = 10.dp

@Composable
private fun headerCardShape() = smoothCornerShape(16.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapDetailScreen(
    mapName: String,
    mapImageUrl: String? = null,
    source: String = "list",
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null
) {
    val sharedElementKey = remember(source, mapName) {
        if (source == "home") "home-map-image-$mapName" else "list-map-image-$mapName"
    }
    val state = rememberLoadState<MapDetail?>(
        null,
        key = mapName
    ) { force ->
        MapDetailApi.fetchMapDetail(mapName, force)
    }
    val wikiUrl = remember(mapName) {
        val enc = URLEncoder.encode(mapName, "UTF-8").replace("+", "%20")
        "https://wiki.biligame.com/klbq/$enc"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(mapName, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    BackNavButton(onClick = onBack)
                },
                actions = {
                    RefreshActionButton(onClick = { state.reload(forceRefresh = true) })
                    OpenWikiActionButton(wikiUrl = wikiUrl, onOpenWikiUrl = onOpenWikiUrl, contentDescription = "在浏览器中打开")
                }
            )
        }
    ) { innerPadding ->
        var previewImage by remember { mutableStateOf<PreviewImage?>(null) }
        val headerShape = headerCardShape()
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // 图片 sharedElement 提到 ApiResourceContent 外，loading/content 切换不影响过渡
            val imageUrl = mapImageUrl
            Card(
                onClick = {
                    if (imageUrl != null) previewImage = PreviewImage(imageUrl, mapName)
                },
                shape = headerShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HeaderCardPadding, vertical = HeaderCardVerticalPadding)
                    .then(
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedElement(
                                    sharedContentState = rememberSharedContentState(key = sharedElementKey),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = { _, _ -> tween(500) }
                                )
                            }
                        } else Modifier
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = mapName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(headerShape)
                    )
                } else {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        shape = headerShape
                    )
                }
            }

            // 文字/数据部分由 ApiResourceContent 控制
            ApiResourceContent(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                isDataEmpty = { it == null },
                loading = { mod ->
                    MapDetailBodySkeleton(modifier = mod)
                }
            ) { detail ->
                MapDetailBody(
                    detail = detail ?: return@ApiResourceContent,
                    onPreviewImage = { url, title -> previewImage = PreviewImage(url, title) }
                )
            }
        }

        previewImage?.let { image ->
            ImagePreviewDialog(
                model = image.url,
                contentDescription = image.title,
                onDismiss = { previewImage = null }
            )
        }
    }
}

@Composable
private fun MapDetailBody(
    detail: MapDetail,
    onPreviewImage: (url: String, title: String) -> Unit
) {
    val headerShape = headerCardShape()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HeaderCardPadding, vertical = HeaderCardVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 标题 + 简介 ──
        Card(
            shape = headerShape,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    detail.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (detail.description.isNotBlank()) {
                    Text(
                        detail.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 24.sp
                    )
                }
            }
        }

        // ── 支持模式 + 平台 ──
        MapInfoCard(detail)

        // ── 地形图 ──
        if (detail.terrainMapUrl != null) {
            MapTerrainCard(
                terrainMapUrl = detail.terrainMapUrl,
                onPreview = { onPreviewImage(detail.terrainMapUrl, "${detail.name} 地形图") }
            )
        }

        // ── 地图概览（横向滚动图片） ──
        if (detail.galleryUrls.isNotEmpty()) {
            MapGalleryCard(
                galleryUrls = detail.galleryUrls,
                onPreview = { url -> onPreviewImage(url, "${detail.name} 地图概览") }
            )
        }

        // ── 更新改动历史 ──
        if (detail.updateHistory.isNotEmpty()) {
            MapUpdateHistoryCard(detail.updateHistory)
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ────────────────────────────────────────────
//  信息卡片（支持模式 + 平台）
// ────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapInfoCard(detail: MapDetail) {
    Card(
        shape = smoothCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(Icons.Outlined.Info, "地图信息")

            // 支持模式
            if (detail.supportedModes.isNotBlank()) {
                Text("支持模式", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detail.supportedModes.split("、", "，", ",").forEach { mode ->
                        val trimmed = mode.trim()
                        if (trimmed.isNotBlank()) {
                            Surface(
                                shape = smoothCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    trimmed,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 上线平台
            if (detail.platforms.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text("上线平台", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detail.platforms.split("、", "，", ",").forEach { platform ->
                        val trimmed = platform.trim()
                        if (trimmed.isNotBlank()) {
                            Surface(
                                shape = smoothCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    trimmed,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  地形图卡片
// ────────────────────────────────────────────

@Composable
private fun MapTerrainCard(terrainMapUrl: String, onPreview: () -> Unit) {
    Card(
        shape = smoothCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(Icons.Outlined.Layers, "地形图")
            Surface(shape = smoothCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                AsyncImage(
                    model = terrainMapUrl,
                    contentDescription = "地形图",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPreview)
                        .clip(smoothCornerShape(18.dp))
                )
            }
        }
    }
}

// ────────────────────────────────────────────
//  地图概览（横向滚动图片）
// ────────────────────────────────────────────

@Composable
private fun MapGalleryCard(galleryUrls: List<String>, onPreview: (String) -> Unit) {
    Card(
        shape = smoothCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(Icons.Outlined.PhotoLibrary, "地图概览")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(galleryUrls, key = { it }) { url ->
                    Card(
                        shape = smoothCornerShape(20.dp),
                        onClick = { onPreview(url) },
                        modifier = Modifier.width(280.dp)
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = "地图概览",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun MapUpdateHistoryCard(updateHistory: List<UpdateEntry>) {
    var expanded by remember { mutableStateOf(false) }
    val visibleHistory = if (expanded) updateHistory else updateHistory.take(3)

    Card(
        shape = smoothCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(Icons.Outlined.Update, "更新改动历史")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                visibleHistory.forEach { entry ->
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = smoothCornerShape(16.dp)
                    ) {
                        Text(
                            text = entry.date,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    entry.changes.forEach { change ->
                        Row(Modifier.padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.Top) {
                            Text(
                                "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = change,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            if (updateHistory.size > 3) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    shape = smoothCornerShape(14.dp)
                ) {
                    Text(if (expanded) "收起" else "查看全部 ${updateHistory.size} 条更新")
                }
            }
        }
    }
}

@Composable
private fun MapDetailBodySkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HeaderCardPadding, vertical = HeaderCardVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题 + 简介 shimmer
        SkeletonCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerBox(Modifier.width(160.dp).height(36.dp))
                ShimmerBox(Modifier.fillMaxWidth().height(48.dp), shape = smoothCornerShape(8.dp))
            }
        }

        // 地图信息卡片骨架
        SkeletonCard {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonSectionTitle()
                SkeletonChipRow(count = 4)
            }
        }

        // 地形图卡片骨架
        SkeletonCard {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonSectionTitle()
                ShimmerBox(
                    Modifier.fillMaxWidth().height(200.dp),
                    shape = smoothCornerShape(18.dp)
                )
            }
        }
    }
}


