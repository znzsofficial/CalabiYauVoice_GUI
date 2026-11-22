package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.extraction.model.DropArtworkIndex
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionDropPoolGroup

/** Each visible item is independently lazy, including after expanding a 300+ item pool. */
internal fun LazyListScope.extractionDropPoolSection(
    group: ExtractionDropPoolGroup, expanded: Boolean, onItemClick: ((String) -> Unit)? = null,
    artwork: DropArtworkIndex = DropArtworkIndex.Empty, onToggle: () -> Unit
) {
    item(key = "pool-heading:${group.key}", contentType = "pool-heading") {
        val colors = MaterialTheme.colorScheme
        val shape = smoothCornerShape(12.dp)
        val canExpand = group.items.size > 8
        val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(DropMotion.reveal), label = "drop-pool-arrow")
        Surface(
            modifier = Modifier.dropItemMotion(this).fillMaxWidth().clip(shape)
                .then(if (canExpand) Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "收起物品" else "展开全部物品",
                    onClick = onToggle
                ).semantics { stateDescription = if (expanded) "已展开" else "显示前 8 项" } else Modifier),
            shape = shape,
            color = colors.surfaceContainer,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
        ) {
            FlowRow(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExtractionDropQualityBadge(label = group.label, code = group.qualityCode ?: -1)
                    Text(
                        "${group.items.size} 项候选物品",
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (canExpand) {
                    Row(
                        Modifier.align(Alignment.CenterVertically),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            if (expanded) "收起" else "展开全部",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            Icons.Outlined.ExpandMore,
                            null,
                            Modifier.size(18.dp).rotate(rotation),
                            tint = colors.primary
                        )
                    }
                }
            }
        }
    }
    val occurrences = mutableMapOf<String, Int>()
    val visible = if (expanded) group.items else group.items.take(8)
    val keys = visible.map { text ->
        val occurrence = occurrences.getOrDefault(text, 0)
        occurrences[text] = occurrence + 1
        "${group.key.length}:${group.key}:${text.length}:$text:$occurrence"
    }
    items(count = visible.size, key = { "pool-item:${keys[it]}" }, contentType = { "pool-item" }) { index ->
        val name = visible[index]
        ExtractionDetailItemRow(
            modifier = Modifier.dropItemMotion(this),
            name = name,
            qualityLabel = group.label,
            qualityCode = group.qualityCode ?: -1,
            imageUrl = artwork.imageFor(name, group.label),
            onClick = onItemClick?.let { { it(name) } }
        )
    }
}

@Composable
internal fun ExtractionDetailItemRow(
    name: String, qualityLabel: String, qualityCode: Int, imageUrl: String?, onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val focus = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val shape = smoothCornerShape(12.dp)
    Surface(
        modifier = modifier.fillMaxWidth().clip(shape)
            .then(if (onClick == null) Modifier else Modifier.clickable(
                role = Role.Button, onClickLabel = "查看物品来源", onClick = { focus.clearFocus(); onClick() }
            )),
        shape = shape,
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExtractionDropThumbnail(
                qualityCode = qualityCode,
                imageUrl = imageUrl,
                modifier = Modifier.size(48.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (qualityLabel.isNotBlank()) ExtractionDropQualityBadge(label = qualityLabel, code = qualityCode)
            }
            if (onClick != null) Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onSurfaceVariant
            )
        }
    }
}
