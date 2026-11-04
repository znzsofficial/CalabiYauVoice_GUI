package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.DetailField
import com.nekolaska.calabiyau.core.ui.WikiContentSheet
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtractionDetailSheet(entry: ExtractionEntry, query: String, onDismiss: () -> Unit) {
    var poolQuery by rememberSaveable(entry.id) {
        mutableStateOf(query.takeIf { keyword ->
            entry.dropPoolGroups.any { group -> group.items.any { it.contains(keyword.trim(), true) } }
        }.orEmpty())
    }
    var expandedGroups by rememberSaveable(entry.id, poolQuery) { mutableStateOf(emptyList<String>()) }
    val poolGroups = remember(entry, poolQuery) { filterDropPoolGroups(entry.dropPoolGroups, poolQuery) }
    val fields = remember(entry) {
        if (entry.dropPoolGroups.isNotEmpty()) entry.fields.filterNot { it.label == "掉落池条目（完整）" } else entry.fields
    }
    WikiContentSheet(onDismiss = onDismiss) {
            if (entry.imageUrl != null) item(key = "image", contentType = "image") {
                AsyncImage(model = entry.imageUrl, contentDescription = entry.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(180.dp))
            }
            item(key = "title", contentType = "title") {
                Text(entry.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text((listOf(entry.category, entry.qualityLabel) + entry.tags).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.description.isNotBlank()) item(key = "description", contentType = "description") {
                Text(entry.description, style = MaterialTheme.typography.bodyLarge)
            }
            items(fields.size, key = { index -> "field-$index" }, contentType = { "field" }) { index ->
                val field = fields[index]
                DetailField(label = field.label, value = field.value)
            }
            if (entry.dropPoolGroups.isNotEmpty()) {
                item(key = "pool-search", contentType = "pool-search") {
                    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("完整掉落池", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        SearchBar(keyword = poolQuery, onKeywordChange = { poolQuery = it }, onSearch = {},
                            onClear = { poolQuery = "" }, isSearching = false, placeholder = "在掉落池中搜索物品…")
                    }
                }
                if (poolGroups.isEmpty()) item(key = "pool-empty", contentType = "pool-empty") { Text("没有匹配的掉落物") }
                poolGroups.forEach { group ->
                    extractionDropPoolSection(group, expanded = group.key in expandedGroups, onToggle = {
                        expandedGroups = if (group.key in expandedGroups) expandedGroups - group.key else expandedGroups + group.key
                    })
                }
            }
            item(key = "close", contentType = "close") { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") } }
    }
}
