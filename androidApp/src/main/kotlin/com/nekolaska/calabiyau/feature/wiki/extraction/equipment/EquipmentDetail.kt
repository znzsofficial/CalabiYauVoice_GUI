package com.nekolaska.calabiyau.feature.wiki.extraction.equipment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.DetailField
import com.nekolaska.calabiyau.core.ui.ImagePreviewDialog
import com.nekolaska.calabiyau.core.ui.WikiContentSheet
import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.model.*

@Composable
internal fun EquipmentDetail(kind: EquipmentKind, series: EquipmentSeries, item: EquipmentVariant,
                             isActive: () -> Boolean, onSelect: (EquipmentVariant) -> Unit, onOriginal: () -> Unit, onDismiss: () -> Unit) {
    var compareId by rememberSaveable(series.key) { mutableStateOf<Int?>(null) }
    var showImage by rememberSaveable(series.key, item.id) { mutableStateOf(false) }
    val peer = series.variants.find { it.id == compareId && it.id != item.id }
    val artworkLabel = "${series.name}，${item.quality.label}，装备图"
    val fields = remember(item) { item.displayFields() }
    val peerFields = remember(peer) { peer?.displayFields().orEmpty() }
    WikiContentSheet(onDismiss) {
        item(key = "header") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                EquipmentThumbnail(kind, item, Modifier.size(width = 84.dp, height = 68.dp), previewLabel = artworkLabel,
                    onPreview = { if (isActive()) showImage = true })
                Column(Modifier.weight(1f)) {
                    Text(series.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EquipmentQualityLabel(item.quality)
                        Text(item.tags.joinToString("  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item(key = "qualities") {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VariantMenu("当前：${item.quality.label}", series.variants, item.id, isActive) { selected ->
                        if (selected.id == peer?.id) compareId = item.id
                        onSelect(selected)
                    }
                    if (series.variants.size > 1) VariantMenu(peer?.let { "对比：${it.quality.label}" } ?: "品质对比",
                        series.variants.filterNot { it.id == item.id }, peer?.id, isActive) { compareId = it.id }
                }
                if (peer != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { if (isActive()) { compareId = item.id; onSelect(peer) } }) {
                        Icon(Icons.Outlined.SwapHoriz, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("互换品质")
                    }
                    TextButton(onClick = { if (isActive()) compareId = null }) { Text("取消对比") }
                }
            }
        }
        item(key = "numbers") {
            EquipmentValues(item.quality, fields, peer?.quality, peerFields)
        }
        if (item.lootText.isNotBlank()) item(key = "loot") { DetailField(label = "来源", value = item.lootText) }
        item.sourceTags.forEach { (label, text) ->
            // The renderer stores gun cost/time under 用途. Those fields are already in the comparison.
            val remaining = if (label == "用途") text.split(" · ").filterNot { it.startsWith("售价 ") || it.startsWith("搜刮 ") }.joinToString(" · ") else text
            if (remaining.isNotEmpty()) item(key = "tag-$label") { DetailField(label = label, value = remaining) }
        }
        item(key = "description") { DetailField(label = "描述", value = item.description) }
        item(key = "original") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("编号 ${item.id}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onOriginal) { Text("Wiki 原文") }
            }
        }
    }
    if (showImage && item.imageUrl != null && isActive()) ImagePreviewDialog(model = WikiImageUrls.originalFromThumbnail(item.imageUrl),
        contentDescription = artworkLabel, onDismiss = { if (isActive()) showImage = false })
}

private fun EquipmentVariant.displayFields(): Map<String, String> = buildMap {
    putAll(attributes)
    put("价值", value)
    if (!containsKey("重量")) put("重量", weight)
    val costs = sourceTags["用途"].orEmpty().split(" · ")
    costs.firstOrNull { it.startsWith("售价 ") }?.let { put("售价", it.removePrefix("售价 ")) }
    put("搜刮耗时", costs.firstOrNull { it.startsWith("搜刮 ") }?.removePrefix("搜刮 ") ?: "$searchTimeMs ms")
}

@Composable
private fun VariantMenu(label: String, variants: List<EquipmentVariant>, selectedId: Int?, isActive: () -> Boolean,
                        choose: (EquipmentVariant) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { if (isActive()) open = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text(label); Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open && isActive(), onDismissRequest = { open = false }) {
            variants.forEach { variant -> DropdownMenuItem(text = { EquipmentQualityLabel(variant.quality) },
                modifier = Modifier.semantics { selected = selectedId == variant.id },
                trailingIcon = { if (selectedId == variant.id) Icon(Icons.Outlined.Check, null) },
                onClick = { if (isActive()) { open = false; choose(variant) } }) }
        }
    }
}

@Composable
internal fun EquipmentThumbnail(kind: EquipmentKind, item: EquipmentVariant, modifier: Modifier, previewLabel: String? = null,
                               onPreview: (() -> Unit)? = null) {
    var ready by remember(item.imageUrl) { mutableStateOf(false) }
    val interactive = if (item.imageUrl != null && onPreview != null)
        Modifier.clickable(onClickLabel = "查看原图", onClick = onPreview).semantics {
            previewLabel?.let { contentDescription = it }
        } else Modifier
    Box(modifier.clip(AppShapes.compactCard).background(MaterialTheme.colorScheme.surfaceContainerHigh).then(interactive),
        contentAlignment = Alignment.Center) {
        if (!ready) Icon(if (kind == EquipmentKind.ARMOR) Icons.Outlined.Shield else Icons.Outlined.CenterFocusStrong,
            null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (item.imageUrl != null) AsyncImage(model = item.imageUrl, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(5.dp), onSuccess = { ready = true }, onError = { ready = false })
        if (onPreview != null && item.imageUrl != null && ready) Icon(Icons.Outlined.ZoomIn, null, Modifier.align(Alignment.BottomEnd).size(16.dp))
    }
}
