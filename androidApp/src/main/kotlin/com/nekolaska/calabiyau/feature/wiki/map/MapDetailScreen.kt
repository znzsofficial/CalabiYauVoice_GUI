package com.nekolaska.calabiyau.feature.wiki.map

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.ImagePreviewDialog
import com.nekolaska.calabiyau.core.ui.OpenWikiActionButton
import com.nekolaska.calabiyau.core.ui.PreviewImage
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.SectionTitle
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.ShimmerGroup
import com.nekolaska.calabiyau.core.ui.SkeletonChipRow
import com.nekolaska.calabiyau.core.ui.SkeletonSectionTitle
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.core.ui.WikiContentSheet
import com.nekolaska.calabiyau.feature.wiki.extraction.model.extractionArticleUrl
import com.nekolaska.calabiyau.feature.wiki.game.fieldGuideContent
import com.nekolaska.calabiyau.feature.wiki.map.api.MapDetailApi
import com.nekolaska.calabiyau.feature.wiki.map.model.MapDetail
import com.nekolaska.calabiyau.feature.wiki.map.model.RIFT_MAP
import com.nekolaska.calabiyau.feature.wiki.map.model.RIFT_WIDGET_LABEL
import com.nekolaska.calabiyau.feature.wiki.map.model.RIFT_WIDGET_PAGE
import com.nekolaska.calabiyau.feature.wiki.map.model.UpdateEntry
import util.wikiPathEncode

// ════════════════════════════════════════════════════════
//  地图详情页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

private val HeaderCardPadding = 16.dp
private val HeaderCardVerticalPadding = 10.dp
private val MapCardRadius = 22.dp
private val MapMediaRadius = 18.dp
private val MapFrostedSurface = Color(0xCC141720)
private val MapFrostedRim = Color.White.copy(alpha = 0.14f)

@Composable
private fun mapCardShape() = smoothCornerShape(MapCardRadius)

@Composable
private fun mapMediaShape() = smoothCornerShape(MapMediaRadius)

@Composable
private fun mapCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapDetailScreen(
    mapName: String,
    mapImageUrl: String? = null,
    source: String = "list",
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
    isActive: () -> Boolean = { true }
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
        "https://wiki.biligame.com/klbq/${mapName.wikiPathEncode()}"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        mapName,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
        var chapterName by rememberSaveable(mapName) { mutableStateOf<String?>(null) }
        val chapterStates = rememberSaveableStateHolder()
        val cardShape = mapCardShape()
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // 图片 sharedElement 提到 ApiResourceContent 外，loading/content 切换不影响过渡
            val imageUrl = mapImageUrl ?: state.data?.overviewImageUrl ?: state.data?.terrainMapUrl
            Card(
                onClick = {
                    if (imageUrl != null) previewImage = PreviewImage(imageUrl, mapName)
                },
                shape = cardShape,
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
                colors = mapCardColors()
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = mapName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(cardShape)
                    )
                } else if (state.isLoading) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        shape = cardShape
                    )
                } else {
                    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "暂无地图图片",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (mapName == RIFT_MAP) {
                FilledTonalButton(onClick = { onOpenWikiUrl(extractionArticleUrl(RIFT_WIDGET_PAGE)) },
                    modifier = Modifier.padding(horizontal = HeaderCardPadding).heightIn(min = 48.dp), shape = AppShapes.compactCard) {
                    Icon(Icons.Outlined.Map, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(RIFT_WIDGET_LABEL, Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
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
                    onChapter = { if (isActive()) chapterName = it },
                    onPreviewImage = { url, title -> previewImage = PreviewImage(url, title) }
                )
            }
        }

        if (isActive()) state.data?.chapters?.firstOrNull { it.title == chapterName }?.let { chapter ->
            chapterStates.SaveableStateProvider(chapter.title) {
                WikiContentSheet(onDismiss = { if (isActive() && chapterName == chapter.title) chapterName = null }) {
                    item("title") { Text(chapter.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall) }
                    fieldGuideContent(chapter, onOpenWikiUrl)
                }
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
    onChapter: (String) -> Unit,
    onPreviewImage: (url: String, title: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HeaderCardPadding, vertical = HeaderCardVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 标题 + 简介 ──
        Card(
            shape = mapCardShape(),
            modifier = Modifier.fillMaxWidth(),
            colors = mapCardColors()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

        if (detail.chapters.isNotEmpty()) Card(shape = mapCardShape(), colors = mapCardColors(), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(Icons.Outlined.Map, "地图资料")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    detail.chapters.forEach { chapter ->
                        FilledTonalButton(onClick = { onChapter(chapter.title) }, shape = AppShapes.compactCard) { Text(chapter.title) }
                    }
                }
            }
        }

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

@Composable
private fun MapInfoCard(detail: MapDetail) {
    Card(
        shape = mapCardShape(),
        modifier = Modifier.fillMaxWidth(),
        colors = mapCardColors()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(Icons.Outlined.Info, "地图信息")

            // 支持模式
            if (detail.supportedModes.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "支持模式",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        detail.supportedModes.split("、", "，", ",").forEach { mode ->
                            val trimmed = mode.trim()
                            if (trimmed.isNotBlank()) {
                                Surface(
                                    shape = AppShapes.capsule,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        trimmed,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 上线平台
            if (detail.platforms.isNotBlank()) {
                if (detail.supportedModes.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "上线平台",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        detail.platforms.split("、", "，", ",").forEach { platform ->
                            val trimmed = platform.trim()
                            if (trimmed.isNotBlank()) {
                                Surface(
                                    shape = AppShapes.capsule,
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        trimmed,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
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
    val mediaShape = mapMediaShape()
    Card(
        shape = mapCardShape(),
        modifier = Modifier.fillMaxWidth(),
        colors = mapCardColors()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(Icons.Outlined.Layers, "地形图")
            Surface(
                onClick = onPreview,
                shape = mediaShape,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Box(Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = terrainMapUrl,
                        contentDescription = "地形图",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(mediaShape)
                    )
                    Surface(
                        shape = AppShapes.capsule,
                        color = MapFrostedSurface,
                        border = BorderStroke(0.5.dp, MapFrostedRim),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.ZoomIn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White
                            )
                            Text(
                                "放大",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  地图概览（横向滚动图片）
// ────────────────────────────────────────────

@Composable
private fun MapGalleryCard(galleryUrls: List<String>, onPreview: (String) -> Unit) {
    val mediaShape = mapMediaShape()
    Card(
        shape = mapCardShape(),
        modifier = Modifier.fillMaxWidth(),
        colors = mapCardColors()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(Icons.Outlined.PhotoLibrary, "地图概览")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(galleryUrls, key = { it }) { url ->
                    Card(
                        shape = mediaShape,
                        onClick = { onPreview(url) },
                        modifier = Modifier.width(280.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
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
        shape = mapCardShape(),
        modifier = Modifier.fillMaxWidth(),
        colors = mapCardColors()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(Icons.Outlined.Update, "更新改动历史")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visibleHistory.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = AppShapes.capsule
                        ) {
                            Text(
                                text = entry.date,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        entry.changes.forEach { change ->
                            Row(Modifier.padding(start = 4.dp, top = 2.dp), verticalAlignment = Alignment.Top) {
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
            }

            if (updateHistory.size > 3) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    shape = AppShapes.capsule
                ) {
                    Text(if (expanded) "收起" else "查看全部 ${updateHistory.size} 条更新")
                }
            }
        }
    }
}

@Composable
private fun MapDetailBodySkeleton(modifier: Modifier = Modifier) = ShimmerGroup {
    val cardShape = mapCardShape()
    val cardColors = mapCardColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HeaderCardPadding, vertical = HeaderCardVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 标题 + 简介 shimmer
        Card(shape = cardShape, modifier = Modifier.fillMaxWidth(), colors = cardColors) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ShimmerBox(Modifier.width(160.dp).height(34.dp))
                ShimmerBox(Modifier.fillMaxWidth().height(48.dp), shape = smoothCornerShape(10.dp))
            }
        }

        // 地图信息卡片骨架
        Card(shape = cardShape, modifier = Modifier.fillMaxWidth(), colors = cardColors) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonSectionTitle()
                SkeletonChipRow(count = 4)
            }
        }

        // 地形图卡片骨架
        Card(shape = cardShape, modifier = Modifier.fillMaxWidth(), colors = cardColors) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonSectionTitle()
                ShimmerBox(
                    Modifier.fillMaxWidth().height(200.dp),
                    shape = mapMediaShape()
                )
            }
        }
    }
}


