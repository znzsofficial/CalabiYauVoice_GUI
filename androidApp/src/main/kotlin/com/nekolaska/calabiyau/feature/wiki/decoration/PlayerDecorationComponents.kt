package com.nekolaska.calabiyau.feature.wiki.decoration

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationItem

private fun decorationQualityColor(quality: Int): Color = when (quality) {
    6 -> Color(0xFFFF6B2C)
    5 -> Color(0xFFEF4444)
    4 -> Color(0xFFF59E0B)
    3 -> Color(0xFFAB47BC)
    2 -> Color(0xFF42A5F5)
    else -> Color(0xFF90A4AE)
}

@Composable
private fun DecorationArtwork(url: String, name: String, modifier: Modifier = Modifier) {
    var loaded by remember(url) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (loaded) 1f else 0f, tween(160), label = "decoration-artwork")
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (!loaded) Icon(Icons.Outlined.Image, "图片占位", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url.isNotBlank()) AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
            onSuccess = { loaded = true }, onError = { loaded = false })
    }
}

@Composable
internal fun DecorationCatalogCard(item: DecorationItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        DecorationArtwork(item.iconUrl.ifBlank { item.imageUrl }, item.name,
            Modifier.fillMaxWidth().aspectRatio(1f))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(MaterialTheme.shapes.small).background(decorationQualityColor(item.quality)))
                Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(item.source.ifBlank { "获取方式未注明" }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun DecorationDetailSheet(item: DecorationItem, onDismiss: () -> Unit, onPreview: (PreviewImage) -> Unit) {
    val imageUrl = item.imageUrl.ifBlank { item.iconUrl }
    WikiContentSheet(onDismiss = onDismiss) {
        item("title") { Text(item.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        if (imageUrl.isNotBlank()) item("artwork") {
            Surface(onClick = { onPreview(PreviewImage(imageUrl, item.name)) }, shape = MaterialTheme.shapes.medium) {
                Column {
                    DecorationArtwork(imageUrl, item.name, Modifier.fillMaxWidth().height(220.dp))
                    Text("点按放大 · 可保存图片", Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (item.description.isNotBlank()) item("description") {
            SelectionContainer { DetailField(label = "介绍", value = item.description) }
        }
        if (item.specialDescription.isNotBlank()) item("effect") {
            SelectionContainer { DetailField(label = "外观效果", value = item.specialDescription) }
        }
        item("source") {
            SelectionContainer { DetailField(label = "获取方式", value = item.source.ifBlank { "原文未注明" }) }
        }
        if (item.extraPreviews.isNotEmpty()) item("preview-heading") {
            Text("更多预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(item.extraPreviews, key = { "${it.label}:${it.imageUrl}" }) { preview ->
            Surface(onClick = { onPreview(PreviewImage(preview.imageUrl, "${item.name} · ${preview.label}")) },
                enabled = preview.imageUrl.isNotBlank(),
                shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column {
                    DecorationArtwork(preview.imageUrl, preview.label, Modifier.fillMaxWidth().height(120.dp))
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(preview.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        if (preview.imageUrl.isNotBlank()) Icon(Icons.Outlined.ChevronRight, "放大${preview.label}")
                        else Text("图片暂不可用", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun DecorationCatalogSkeleton(modifier: Modifier = Modifier, withGroupHeading: Boolean = false) {
    val typography = MaterialTheme.typography
    val titleHeight = with(LocalDensity.current) { typography.bodyMedium.lineHeight.toDp() } * 2
    val sourceHeight = with(LocalDensity.current) { typography.bodySmall.lineHeight.toDp() }
    LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (withGroupHeading) item("heading", span = { GridItemSpan(maxLineSpan) }) {
            SkeletonTextLine(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), widthFraction = .45f, height = sourceHeight)
        }
        items(8) {
            Card(shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                ShimmerBox(Modifier.fillMaxWidth().aspectRatio(1f))
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonTextLine(widthFraction = .85f, height = titleHeight)
                    SkeletonTextLine(widthFraction = .7f, height = sourceHeight)
                }
            }
        }
    }
}
