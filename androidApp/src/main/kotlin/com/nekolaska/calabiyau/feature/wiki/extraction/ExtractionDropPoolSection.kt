package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionDropPoolGroup

/** Each visible item is independently lazy, including after expanding a 300+ item pool. */
internal fun LazyListScope.extractionDropPoolSection(
    group: ExtractionDropPoolGroup, expanded: Boolean, onItemClick: ((String) -> Unit)? = null, onToggle: () -> Unit
) {
    item(key = "pool-heading:${group.key}", contentType = "pool-heading") {
        Text("${group.label} · ${group.items.size} 项", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
    val occurrences = mutableMapOf<String, Int>()
    val visible = if (expanded) group.items else group.items.take(8)
    val keys = visible.map { text ->
        val occurrence = occurrences.getOrDefault(text, 0)
        occurrences[text] = occurrence + 1
        "${group.key}:$text:$occurrence"
    }
    items(count = visible.size, key = { "pool-item:${keys[it]}" }, contentType = { "pool-item" }) { index ->
        if (onItemClick == null) Text("• ${visible[index]}", style = MaterialTheme.typography.bodyMedium)
        else TextButton(onClick = { onItemClick(visible[index]) }, modifier = Modifier.fillMaxWidth()) {
            Text(visible[index], modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("查来源", style = MaterialTheme.typography.labelMedium)
        }
    }
    if (group.items.size > 8) item(key = "pool-toggle:${group.key}", contentType = "pool-toggle") {
        TextButton(onClick = onToggle) { Text(if (expanded) "收起" else "展开全部 ${group.items.size} 项") }
    }
}
