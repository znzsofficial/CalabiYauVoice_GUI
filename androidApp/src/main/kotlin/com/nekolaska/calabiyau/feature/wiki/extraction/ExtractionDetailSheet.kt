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
import com.nekolaska.calabiyau.core.ui.OfflineBanner
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtractionDetailSheet(
    entry: ExtractionEntry, query: String,
    onViewCompletePool: (() -> Unit)? = null,
    onFindItem: ((ExtractionDropItem) -> Unit)? = null,
    containerDetails: Boolean = false,
    initialPoolQuality: String? = null,
    offlineAgeMs: Long? = null,
    poolError: String? = null,
    poolIsLoading: Boolean = false,
    onRefreshPool: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var poolQuery by rememberSaveable(entry.id) {
        mutableStateOf(query.takeIf { keyword ->
            entry.dropPoolGroups.any { group -> group.items.any { it.contains(keyword.trim(), true) } }
        }.orEmpty())
    }
    var expandedGroups by rememberSaveable(entry.id) { mutableStateOf(emptyList<String>()) }
    var selectedQuality by rememberSaveable(entry.id) { mutableStateOf(initialPoolQuality) }
    val poolGroups = remember(entry, poolQuery, selectedQuality) {
        filterDropPoolGroups(entry.dropPoolGroups, poolQuery).filter { selectedQuality == null || it.label == selectedQuality }
    }
    LaunchedEffect(entry.dropPoolGroups) {
        if (entry.dropPoolGroups.isNotEmpty() && entry.dropPoolGroups.none { it.label == selectedQuality }) selectedQuality = null
    }
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
             if (onViewCompletePool != null) item(key = "complete-link") {
                 FilledTonalButton(onClick = onViewCompletePool, modifier = Modifier.fillMaxWidth()) {
                     Text(if (entry.containerId != null) "查看完整池 · 容器 ${entry.containerId}" else "浏览容器完整池")
                 }
                 if (entry.containerId == null) Text("此卡未提供可对应的容器编号，可在完整表中浏览来源。",
                     style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
             }
             if (containerDetails) item(key = "container-semantics") {
                 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                     offlineAgeMs?.let { OfflineBanner(ageMs = it) }
                     poolError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                     entry.containerId?.let { Text("容器编号 $it", style = MaterialTheme.typography.labelLarge) }
                     Text(if (entry.dropCountState == DropCountState.ZERO) "此来源件数恒为 0，不会出货。下方仅为配置的候选池。"
                         else "先决定件数，再逐件独立按权重抽取。单件率不等于整箱至少一个。",
                         style = MaterialTheme.typography.bodyMedium,
                         color = if (entry.dropCountState == DropCountState.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                      if (entry.dropPoolGroups.isEmpty()) Text(
                          if (entry.containerId == null) "源表未公布此容器的完整池。" else "完整池为空（0 项）。",
                          style = MaterialTheme.typography.bodyMedium)
                     onRefreshPool?.let { refresh -> TextButton(onClick = refresh, enabled = !poolIsLoading) {
                         Text(if (poolIsLoading) "正在刷新完整池…" else "刷新完整池")
                     } }
                 }
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
                         FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                             FilterChip(selected = selectedQuality == null, onClick = { selectedQuality = null }, label = { Text("全部品质") })
                             entry.dropPoolGroups.map { it.label }.distinct().forEach { label ->
                                 FilterChip(selected = selectedQuality == label, onClick = { selectedQuality = label }, label = { Text(label) })
                             }
                         }
                         if (onFindItem != null) Text("点击物品，查看其它来源池", style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (poolGroups.isEmpty()) item(key = "pool-empty", contentType = "pool-empty") { Text("没有匹配的掉落物") }
                poolGroups.forEach { group ->
                     extractionDropPoolSection(group, expanded = group.key in expandedGroups, onItemClick = onFindItem?.let { find ->
                         { name -> find(ExtractionDropItem(name, group.label, group.qualityCode)) }
                     }, onToggle = {
                        expandedGroups = if (group.key in expandedGroups) expandedGroups - group.key else expandedGroups + group.key
                    })
                }
            }
            item(key = "close", contentType = "close") { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") } }
    }
}
