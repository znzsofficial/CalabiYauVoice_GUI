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
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*

@Composable
internal fun ExtractionDropCard(
    modifier: Modifier = Modifier, entry: ExtractionEntry, query: String = "",
    isContainer: Boolean = false, sharedPool: ExtractionEntry? = null,
    artwork: DropArtworkIndex = DropArtworkIndex.Empty, onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val groups = sharedPool?.dropPoolGroups ?: entry.dropPoolGroups
    val evidence = remember(entry, query, groups) { extractionDropMatchEvidence(entry, query, groups) }
    val previewItem = remember(groups, evidence, artwork, query) {
        dropPreviewArtwork(query, evidence, groups, artwork)
    }
    val poolSummary = dropCandidateSummary(entry, isContainer, sharedPool)
    val isZero = entry.dropCountState == DropCountState.ZERO
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = smoothCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(
            1.dp,
            if (isZero) colors.error.copy(alpha = 0.45f)
            else colors.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtractionDropSourceIconBox(entry)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = if (evidence?.isNameMatch == true) colors.primary else colors.onSurface
                    )
                    val subtitle = (listOfNotNull(poolSummary, entry.category) + entry.tags)
                        .filter { it.isNotBlank() && it != entry.name }.distinct().joinToString("，")
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.onSurfaceVariant
                )
            }
            if (isZero || (isContainer && entry.dropCountState == DropCountState.UNKNOWN)) DropCountStatus(entry)
            val preview = when {
                evidence != null -> evidence.captionWithThumbnail(previewItem?.first)
                previewItem != null -> "候选物品：${previewItem.first.name}（${previewItem.first.qualityLabel}）"
                !isContainer -> entry.fields.firstOrNull {
                    it.label in setOf("候选池", "阈值", "必掉物", "掉落材料（按权重抽）", "掉落材料（按概率抽）", "掉落件数")
                }?.let { "${it.label}：${it.value}" }
                else -> null
            }
            if (preview != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = smoothCornerShape(8.dp),
                    color = colors.surfaceContainerHigh.copy(alpha = 0.55f),
                    border = BorderStroke(
                        1.dp,
                        if (evidence != null) colors.primary.copy(alpha = 0.28f)
                        else colors.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        previewItem?.let { (item, url) ->
                            ExtractionDropThumbnail(item.qualityCode ?: -1, url, Modifier.size(36.dp))
                        }
                        Text(
                            preview,
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = if (evidence != null) colors.primary else colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DropCountStatus(entry: ExtractionEntry) {
    val colors = MaterialTheme.colorScheme
    val zero = entry.dropCountState == DropCountState.ZERO
    val unknown = entry.dropCountState == DropCountState.UNKNOWN
    val label = when (entry.dropCountState) {
        DropCountState.ZERO -> "掉落 0 件"
        DropCountState.UNKNOWN -> "掉落件数未知"
        DropCountState.POSITIVE -> entry.fields.firstOrNull { it.label == "件数概率" }?.let { "件数概率：${it.value}" } ?: return
    }
    Surface(
        shape = smoothCornerShape(6.dp),
        color = when {
            zero -> colors.errorContainer
            unknown -> colors.secondaryContainer
            else -> colors.surfaceContainerHigh
        },
        border = BorderStroke(
            1.dp,
            when {
                zero -> colors.error.copy(alpha = 0.30f)
                unknown -> colors.outlineVariant.copy(alpha = 0.45f)
                else -> colors.outlineVariant.copy(alpha = 0.40f)
            }
        )
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium,
            color = when {
                zero -> colors.onErrorContainer
                unknown -> colors.onSecondaryContainer
                else -> colors.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** An explicitly empty pool and an unpublished table row are different player-facing states. */
internal fun dropCandidateSummary(entry: ExtractionEntry, isContainer: Boolean, sharedPool: ExtractionEntry? = null): String? {
    val groups = sharedPool?.dropPoolGroups ?: entry.dropPoolGroups
    return when {
        isContainer && entry.containerId == null -> "候选物品尚未公布"
        groups.isNotEmpty() || isContainer || sharedPool != null -> "${groups.sumOf { it.items.size }} 项候选物品"
        else -> null
    }
}

/** A source's semantic type is reliable; an item in its pool is not a portrait of that source. */
@Composable
internal fun ExtractionDropSourceIconBox(entry: ExtractionEntry, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val isZero = entry.dropCountState == DropCountState.ZERO
    Surface(
        modifier = modifier.size(40.dp),
        shape = smoothCornerShape(10.dp),
        color = if (isZero) colors.errorContainer else colors.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            if (isZero) colors.error.copy(alpha = 0.35f) else colors.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            ExtractionDropSourceIcon(
                entry = entry,
                tint = if (isZero) colors.onErrorContainer else colors.primary
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
        modifier = Modifier.size(20.dp),
        tint = tint
    )
}
