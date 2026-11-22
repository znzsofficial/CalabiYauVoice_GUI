package com.nekolaska.calabiyau.feature.wiki.extraction.equipment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.DetailField
import com.nekolaska.calabiyau.core.ui.ImagePreviewDialog
import com.nekolaska.calabiyau.core.ui.WikiContentSheet
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.model.*

@Composable
internal fun EquipmentDetail(
    kind: EquipmentKind, series: EquipmentSeries, item: EquipmentVariant,
    isActive: () -> Boolean, onSelect: (EquipmentVariant) -> Unit, onOriginal: () -> Unit, onDismiss: () -> Unit
) {
    var compareId by rememberSaveable(series.key) { mutableStateOf<Int?>(null) }
    var showImage by rememberSaveable(series.key, item.id) { mutableStateOf(false) }
    val peer = series.variants.find { it.id == compareId && it.id != item.id }
    val artworkLabel = "${series.name}，${item.quality.label}，装备图"
    val fields = remember(item) { item.displayFields() }
    val peerFields = remember(peer) { peer?.displayFields().orEmpty() }
    val colors = MaterialTheme.colorScheme
    val qualityAccent = equipmentQualityColor(item.quality)
    val proseFields = remember(item) {
        buildList {
            if (item.lootText.isNotBlank()) add("来源" to item.lootText)
            item.sourceTags.forEach { (label, text) ->
                // The renderer stores gun cost/time under 用途. Those fields are already in the comparison.
                val remaining = if (label == "用途") {
                    text.split(" · ").filterNot { it.startsWith("售价 ") || it.startsWith("搜刮 ") }.joinToString(" · ")
                } else text
                if (remaining.isNotEmpty()) add(label to remaining)
            }
            if (item.description.isNotBlank()) add("描述" to item.description)
        }
    }
    WikiContentSheet(onDismiss) {
        item(key = "header") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(12.dp),
                color = colors.surfaceContainer,
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(qualityAccent))
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EquipmentThumbnail(
                            kind,
                            item,
                            Modifier.size(width = 88.dp, height = 70.dp),
                            previewLabel = artworkLabel,
                            onPreview = { if (isActive()) showImage = true }
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(series.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                EquipmentQualityLabel(item.quality, asBadge = true)
                                item.tags.filter { it.isNotBlank() }.forEach { tag ->
                                    Surface(
                                        shape = smoothCornerShape(6.dp),
                                        color = colors.surfaceContainerHigh,
                                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
                                    ) {
                                        Text(
                                            tag,
                                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "qualities") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VariantMenu(
                        prefix = "当前",
                        selectedQuality = item.quality,
                        placeholder = item.quality.label,
                        variants = series.variants,
                        selectedId = item.id,
                        highlighted = true,
                        isActive = isActive
                    ) { selected ->
                        if (selected.id == peer?.id) compareId = item.id
                        onSelect(selected)
                    }
                    if (series.variants.size > 1) {
                        VariantMenu(
                            prefix = if (peer != null) "对比" else null,
                            selectedQuality = peer?.quality,
                            placeholder = "品质对比",
                            variants = series.variants.filterNot { it.id == item.id },
                            selectedId = peer?.id,
                            highlighted = peer != null,
                            isActive = isActive
                        ) { compareId = it.id }
                    }
                }
                if (peer != null) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = { if (isActive()) { compareId = item.id; onSelect(peer) } },
                            shape = smoothCornerShape(8.dp),
                            color = colors.surfaceContainerLow,
                            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Outlined.SwapHoriz, null, Modifier.size(16.dp), tint = colors.primary)
                                Text("互换品质", style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
                            }
                        }
                        Surface(
                            onClick = { if (isActive()) compareId = null },
                            shape = smoothCornerShape(8.dp),
                            color = colors.surfaceContainerLow,
                            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
                        ) {
                            Text(
                                "取消对比",
                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item(key = "numbers") {
            EquipmentValues(item.quality, fields, peer?.quality, peerFields)
        }
        if (proseFields.isNotEmpty()) {
            item(key = "prose") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = smoothCornerShape(12.dp),
                    color = colors.surfaceContainer,
                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        proseFields.forEachIndexed { index, (label, value) ->
                            if (index > 0) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.40f))
                            DetailField(label = label, value = value)
                        }
                    }
                }
            }
        }
        item(key = "original") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = smoothCornerShape(6.dp),
                    color = colors.surfaceContainer,
                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
                ) {
                    Text(
                        "编号 ${item.id}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = colors.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = onOriginal,
                    shape = smoothCornerShape(10.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Wiki 原文")
                }
            }
        }
    }
    if (showImage && item.imageUrl != null && isActive()) {
        ImagePreviewDialog(
            model = WikiImageUrls.originalFromThumbnail(item.imageUrl),
            contentDescription = artworkLabel,
            onDismiss = { if (isActive()) showImage = false }
        )
    }
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
private fun VariantMenu(
    prefix: String?,
    selectedQuality: EquipmentQuality?,
    placeholder: String,
    variants: List<EquipmentVariant>,
    selectedId: Int?,
    highlighted: Boolean,
    isActive: () -> Boolean,
    choose: (EquipmentVariant) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val dotColor = selectedQuality?.let { equipmentQualityColor(it) }
    Box {
        Surface(
            onClick = { if (isActive()) open = true },
            modifier = Modifier.heightIn(min = 40.dp).semantics { role = Role.Button },
            shape = smoothCornerShape(10.dp),
            color = if (highlighted) colors.primaryContainer.copy(alpha = 0.45f) else colors.surfaceContainer,
            border = BorderStroke(
                1.dp,
                if (highlighted) colors.primary.copy(alpha = 0.50f) else colors.outlineVariant.copy(alpha = 0.55f)
            )
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (prefix != null) {
                    Text(
                        prefix,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
                if (dotColor != null) {
                    Spacer(Modifier.size(6.dp).background(dotColor, CircleShape))
                }
                Text(
                    selectedQuality?.label ?: placeholder,
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = if (highlighted) colors.onSurface else colors.primary
                )
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    null,
                    Modifier.size(18.dp),
                    tint = colors.onSurfaceVariant
                )
            }
        }
        DropdownMenu(expanded = open && isActive(), onDismissRequest = { open = false }) {
            variants.forEach { variant ->
                DropdownMenuItem(
                    text = { EquipmentQualityLabel(variant.quality) },
                    modifier = Modifier.semantics { selected = selectedId == variant.id },
                    trailingIcon = { if (selectedId == variant.id) Icon(Icons.Outlined.Check, null) },
                    onClick = { if (isActive()) { open = false; choose(variant) } }
                )
            }
        }
    }
}

@Composable
internal fun EquipmentThumbnail(
    kind: EquipmentKind, item: EquipmentVariant, modifier: Modifier, previewLabel: String? = null,
    onPreview: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val shape = smoothCornerShape(10.dp)
    var ready by remember(item.imageUrl) { mutableStateOf(false) }
    val interactive = if (item.imageUrl != null && onPreview != null)
        Modifier.clickable(onClickLabel = "查看原图", onClick = onPreview).semantics {
            previewLabel?.let { contentDescription = it }
        } else Modifier
    Box(
        modifier
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.55f), shape)
            .then(interactive),
        contentAlignment = Alignment.Center
    ) {
        if (!ready) Icon(
            if (kind == EquipmentKind.ARMOR) Icons.Outlined.Shield else Icons.Outlined.CenterFocusStrong,
            null,
            Modifier.size(24.dp),
            tint = colors.onSurfaceVariant
        )
        if (item.imageUrl != null) AsyncImage(
            model = item.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(6.dp),
            onSuccess = { ready = true },
            onError = { ready = false }
        )
        if (onPreview != null && item.imageUrl != null && ready) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                shape = smoothCornerShape(5.dp),
                color = colors.surfaceContainerLow.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
            ) {
                Icon(
                    Icons.Outlined.ZoomIn,
                    null,
                    Modifier.padding(2.dp).size(13.dp),
                    tint = colors.onSurfaceVariant
                )
            }
        }
    }
}
