package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
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
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtractionDropSourceIcon(entry)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text((listOf(entry.category) + entry.tags).filter { it.isNotBlank() }.distinct().joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (entry.dropCountState == DropCountState.ZERO) Text("实际不出货 · 件数恒为 0",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            if (evidence != null) {
                Text(evidence.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(evidence.snippet, style = MaterialTheme.typography.bodyMedium)
            }
            previewItem?.let { (item, url) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExtractionDropThumbnail(item.name, item.qualityLabel, item.qualityCode ?: 0, url, Modifier.size(40.dp))
                    Text("包含 · ${item.name}（${item.qualityLabel}）", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (entry.dropPoolGroups.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.dropPoolGroups.forEach { group ->
                        ExtractionDropQualityBadge("${group.label} · ${group.items.size}", group.qualityCode ?: 0)
                    }
                }
            }
            entry.fields.firstOrNull { it.label in setOf("掉落件数", "件数概率", "池规模", "阈值") }?.let {
                Text("${it.label} · ${it.value}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(if (entry.containerId != null && entry.isDropPool) "查看包含物品 · ${entry.dropPoolGroups.sumOf { it.items.size }} 项"
                else "查看来源详情", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** A source's semantic type is reliable; an item in its pool is not a portrait of that source. */
@Composable
internal fun ExtractionDropSourceIcon(entry: ExtractionEntry) {
    Icon(when (entry.type) {
        ExtractionEntryType.DROP_STAGE -> Icons.Outlined.AccountTree
        ExtractionEntryType.DROP_POOL -> Icons.Outlined.Inventory2
        else -> Icons.Outlined.LocationOn
    }, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
}
