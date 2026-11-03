package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionDropPoolGroup

@Composable
internal fun ExtractionDropPoolSection(entryId: String, group: ExtractionDropPoolGroup, query: String) {
    var expanded by rememberSaveable(entryId, group.label, query) { mutableStateOf(false) }
    val visibleItems = if (expanded) group.items else group.items.take(8)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${group.label} · ${group.items.size} 项", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text(visibleItems.joinToString("\n") { "• $it" }, style = MaterialTheme.typography.bodyMedium)
        if (group.items.size > 8) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起" else "展开全部 ${group.items.size} 项")
            }
        }
    }
}
