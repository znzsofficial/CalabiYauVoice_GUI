package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.feature.wiki.extraction.model.DropArtworkIndex
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionDropPoolGroup

/** Each visible item is independently lazy, including after expanding a 300+ item pool. */
internal fun LazyListScope.extractionDropPoolSection(
    group: ExtractionDropPoolGroup, expanded: Boolean, onItemClick: ((String) -> Unit)? = null,
    artwork: DropArtworkIndex = DropArtworkIndex.Empty, onToggle: () -> Unit
) {
    item(key = "pool-heading:${group.key}", contentType = "pool-heading") {
        val canExpand = group.items.size > 8
        val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(180), label = "drop-pool-arrow")
        Row(Modifier.dropItemMotion(this).fillMaxWidth()
            .then(if (canExpand) Modifier.clickable(role = Role.Button, onClickLabel = if (expanded) "收起物品" else "展开全部物品",
                onClick = onToggle).semantics { stateDescription = if (expanded) "已展开" else "显示前 8 件" } else Modifier)
            .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ExtractionDropQualityBadge(label = group.label, code = group.qualityCode ?: -1)
                Text("${group.items.size} 件物品", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
            }
            if (canExpand) {
                Text(if (expanded) "收起" else "展开全部", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.primary)
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
        ExtractionDetailItemRow(modifier = Modifier.dropItemMotion(this), name = name, qualityLabel = group.label, qualityCode = group.qualityCode ?: -1,
            imageUrl = artwork.imageFor(name, group.label), onClick = onItemClick?.let { { it(name) } })
    }
}

@Composable
internal fun ExtractionDetailItemRow(
    name: String, qualityLabel: String, qualityCode: Int, imageUrl: String?, onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val action = if (onClick == null) Modifier else Modifier.clickable(
        role = Role.Button, onClickLabel = "查找$name 的掉落来源", onClick = onClick
    )
    Row(
        modifier = modifier.fillMaxWidth().then(action).heightIn(min = 64.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExtractionDropThumbnail(name = name, qualityLabel = qualityLabel, qualityCode = qualityCode,
            imageUrl = imageUrl, modifier = Modifier.size(48.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (qualityLabel.isNotBlank()) ExtractionDropQualityBadge(label = qualityLabel, code = qualityCode)
            if (onClick != null) Text("查来源", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
