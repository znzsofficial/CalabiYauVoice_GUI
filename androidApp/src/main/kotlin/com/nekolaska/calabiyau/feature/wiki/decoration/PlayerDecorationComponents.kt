package com.nekolaska.calabiyau.feature.wiki.decoration

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationItem

private val DecorationCardRadius = 18.dp
private val DecorationStageRadius = 22.dp
private val FrostedPillBackground = Color(0xCC141720)
private val FrostedPillRim = Color.White.copy(alpha = 0.14f)

private fun decorationQualityColor(quality: Int): Color = when (quality) {
    6 -> Color(0xFFFF6B2C)
    5 -> Color(0xFFEF4444)
    4 -> Color(0xFFF59E0B)
    3 -> Color(0xFFAB47BC)
    2 -> Color(0xFF42A5F5)
    else -> Color(0xFF90A4AE)
}

@Composable
private fun DecorationArtwork(
    url: String,
    name: String,
    modifier: Modifier = Modifier,
    imagePadding: Dp = 8.dp,
    showMissingHint: Boolean = false,
    qualityAccent: Color? = null
) {
    var loaded by remember(url) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (loaded) 1f else 0f, tween(160), label = "decoration-artwork")
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(
                if (qualityAccent != null) {
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(qualityAccent.copy(alpha = 0.14f), Color.Transparent)
                        )
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!loaded) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Outlined.Image,
                    contentDescription = "图片占位",
                    modifier = Modifier.size(if (showMissingHint) 30.dp else 26.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                )
                if (showMissingHint && url.isBlank()) {
                    Text(
                        "暂无图片",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }
        }
        if (url.isNotBlank()) AsyncImage(
            model = url,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(imagePadding).graphicsLayer { this.alpha = alpha },
            onSuccess = { loaded = true },
            onError = { loaded = false }
        )
    }
}

@Composable
private fun BoxScope.FrostedZoomPill(
    label: String = "放大",
    padding: Dp = 12.dp
) {
    Surface(
        shape = AppShapes.capsule,
        color = FrostedPillBackground,
        border = BorderStroke(0.5.dp, FrostedPillRim),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(padding)
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
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Composable
internal fun DecorationCatalogCard(item: DecorationItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val qualityAccent = if (item.quality >= 0) decorationQualityColor(item.quality) else null
    val cardShape = smoothCornerShape(DecorationCardRadius)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        DecorationArtwork(
            url = item.iconUrl.ifBlank { item.imageUrl },
            name = item.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(cardShape),
            imagePadding = 8.dp,
            qualityAccent = qualityAccent
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                if (qualityAccent != null) {
                    // Reserve two title lines for grid rhythm, but align the dot to the first line.
                    Box(
                        modifier = Modifier.width(7.dp).height(
                            with(LocalDensity.current) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() }
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(qualityAccent))
                    }
                }
                Text(
                    item.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                item.source.ifBlank { "获取方式未注明" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun DecorationDetailSheet(item: DecorationItem, onDismiss: () -> Unit, onPreview: (PreviewImage) -> Unit) {
    val imageUrl = item.imageUrl.ifBlank { item.iconUrl }
    val qualityAccent = if (item.quality >= 0) decorationQualityColor(item.quality) else null
    WikiContentSheet(onDismiss = onDismiss) {
        item("title") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (qualityAccent != null) {
                        Box(
                            modifier = Modifier.width(10.dp).height(
                                with(LocalDensity.current) { MaterialTheme.typography.headlineSmall.lineHeight.toDp() }
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(qualityAccent))
                        }
                    }
                    Text(
                        item.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (item.platform.isNotBlank()) {
                    Surface(
                        shape = AppShapes.capsule,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            item.platform,
                            Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (imageUrl.isNotBlank()) item("artwork") {
            Surface(
                onClick = { onPreview(PreviewImage(imageUrl, item.name)) },
                shape = smoothCornerShape(DecorationStageRadius),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Box(Modifier.fillMaxWidth().height(232.dp)) {
                    DecorationArtwork(
                        url = imageUrl,
                        name = item.name,
                        modifier = Modifier.fillMaxSize(),
                        imagePadding = 16.dp,
                        showMissingHint = true,
                        qualityAccent = qualityAccent
                    )
                    FrostedZoomPill(label = "放大", padding = 12.dp)
                }
            }
        }
        if (item.description.isNotBlank()) item("description") {
            DecorationDetailCardField(label = "介绍", value = item.description)
        }
        if (item.specialDescription.isNotBlank()) item("effect") {
            DecorationDetailCardField(label = "外观效果", value = item.specialDescription)
        }
        item("source") {
            DecorationDetailCardField(label = "获取方式", value = item.source.ifBlank { "原文未注明" })
        }
        if (item.extraPreviews.isNotEmpty()) item("preview-heading") {
            Text(
                "更多预览",
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        items(item.extraPreviews, key = { "${it.label}:${it.imageUrl}" }) { preview ->
            val available = preview.imageUrl.isNotBlank()
            Surface(
                onClick = { onPreview(PreviewImage(preview.imageUrl, "${item.name} · ${preview.label}")) },
                enabled = available,
                shape = smoothCornerShape(DecorationCardRadius),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column {
                    Box(Modifier.fillMaxWidth().height(132.dp)) {
                        DecorationArtwork(
                            url = preview.imageUrl,
                            name = preview.label,
                            modifier = Modifier.fillMaxSize(),
                            imagePadding = 10.dp,
                            showMissingHint = true
                        )
                        if (available) {
                            FrostedZoomPill(label = "放大", padding = 10.dp)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            preview.label,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!available) {
                            Surface(
                                shape = AppShapes.capsule,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    "图片暂不可用",
                                    Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun DecorationDetailCardField(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(DecorationCardRadius),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        SelectionContainer {
            DetailField(
                modifier = Modifier.padding(16.dp),
                label = label,
                value = value
            )
        }
    }
}

@Composable
internal fun DecorationCatalogSkeleton(modifier: Modifier = Modifier, withGroupHeading: Boolean = false) = ShimmerGroup {
    val typography = MaterialTheme.typography
    val titleHeight = with(LocalDensity.current) { typography.bodyMedium.lineHeight.toDp() } * 2
    val sourceHeight = with(LocalDensity.current) { typography.bodySmall.lineHeight.toDp() }
    val headingHeight = with(LocalDensity.current) { typography.titleMedium.lineHeight.toDp() }
    val cardShape = smoothCornerShape(DecorationCardRadius)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false
    ) {
        if (withGroupHeading) item("heading", span = { GridItemSpan(maxLineSpan) }) {
            SkeletonTextLine(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), widthFraction = .45f, height = headingHeight)
        }
        items(8) {
            Card(
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                ShimmerBox(Modifier.fillMaxWidth().aspectRatio(1f), shape = cardShape)
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkeletonTextLine(widthFraction = .85f, height = titleHeight)
                    SkeletonTextLine(widthFraction = .7f, height = sourceHeight)
                }
            }
        }
    }
}
