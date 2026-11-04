package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionEntry
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionEntryType

@Composable
internal fun ExtractionDropCard(modifier: Modifier = Modifier, entry: ExtractionEntry, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text((listOf(entry.category) + entry.tags).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            entry.fields.firstOrNull { it.label in setOf("掉落件数", "件数概率", "池规模", "阈值") }?.let {
                Text("${it.label} · ${it.value}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val preview = when {
                entry.dropPoolGroups.isNotEmpty() -> entry.dropPoolGroups.joinToString(" · ") { "${it.label} ${it.items.size} 项" }
                entry.type == ExtractionEntryType.DROP_STAGE -> entry.fields.firstOrNull { it.label == "候选池" }?.let { "候选池 · ${it.value}" }
                else -> entry.fields.firstOrNull { it.label in setOf("必掉物", "掉落材料（按权重抽）", "掉落池条目（完整）", "掉落池内最高权重") }?.value
            }
            preview?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) }
            Text("查看掉落详情", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
