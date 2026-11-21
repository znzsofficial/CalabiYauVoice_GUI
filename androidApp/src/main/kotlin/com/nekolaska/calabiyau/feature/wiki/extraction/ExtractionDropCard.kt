package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*

@Composable
internal fun ExtractionDropCard(
    modifier: Modifier = Modifier, entry: ExtractionEntry, query: String = "",
    artwork: DropArtworkIndex = DropArtworkIndex.Empty, onClick: () -> Unit
) {
    val evidence = remember(entry, query) { extractionDropMatchEvidence(entry, query) }
    val previewItem = remember(entry, evidence, artwork, query) {
        val candidates = if (query.isNotBlank()) evidence?.items.orEmpty() else entry.dropPoolGroups.flatMap { group ->
            group.items.map { ExtractionDropItem(it, group.label, group.qualityCode) }
        }
        candidates.firstNotNullOfOrNull { item -> artwork.imageFor(item.name, item.qualityLabel)?.let { item to it } }
    }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.compactCard,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            1.dp,
            if (entry.dropCountState == DropCountState.ZERO) MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtractionDropSourceIconBox(entry)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val subtitle = buildList {
                        add(entry.category)
                        addAll(entry.tags)
                        if (entry.dropPoolGroups.isNotEmpty()) add("${entry.dropPoolGroups.sumOf { it.items.size }} 项候选")
                    }.filter { it.isNotBlank() }.distinct().joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.dropCountState == DropCountState.ZERO) {
                Surface(shape = AppShapes.chip, color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        "无物品掉落 · 0 件",
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (evidence != null) {
                Surface(
                    shape = AppShapes.chip,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(evidence.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(evidence.snippet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            previewItem?.let { (item, url) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExtractionDropThumbnail(item.name, item.qualityLabel, item.qualityCode ?: 0, url, Modifier.size(40.dp))
                    Text(
                        "${item.name}（${item.qualityLabel}）",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            entry.fields.firstOrNull { it.label in setOf("掉落件数", "件数概率", "阈值") }?.let {
                Text("${it.label}：${it.value}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (evidence == null) {
                val preview = if (entry.type == ExtractionEntryType.DROP_STAGE) {
                    entry.fields.firstOrNull { it.label == "候选池" }?.let { "候选池 · ${it.value}" }
                } else if (entry.dropPoolGroups.isEmpty()) {
                    entry.fields.firstOrNull { it.label in setOf("必掉物", "掉落材料（按权重抽）", "掉落池条目（完整）", "掉落池内最高权重") }
                        ?.let { "${it.label} · ${it.value}" }
                } else null
                preview?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

/** A source's semantic type is reliable; an item in its pool is not a portrait of that source. */
@Composable
internal fun ExtractionDropSourceIconBox(entry: ExtractionEntry, modifier: Modifier = Modifier) {
    val isZero = entry.dropCountState == DropCountState.ZERO
    Surface(
        modifier = modifier.size(40.dp),
        shape = AppShapes.sectionIcon,
        color = if (isZero) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            ExtractionDropSourceIcon(
                entry = entry,
                tint = if (isZero) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun ExtractionDropSourceIcon(
    entry: ExtractionEntry,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Icon(
        when (entry.type) {
            ExtractionEntryType.DROP_STAGE -> Icons.Outlined.AccountTree
            ExtractionEntryType.DROP_POOL -> Icons.Outlined.Inventory2
            else -> Icons.Outlined.LocationOn
        },
        contentDescription = null,
        modifier = Modifier.size(22.dp),
        tint = tint
    )
}
