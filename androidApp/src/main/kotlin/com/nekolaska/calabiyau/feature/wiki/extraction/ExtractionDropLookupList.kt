package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*

@Composable
internal fun ExtractionDropLookupList(
    lookup: Map<ExtractionDropItem, List<ExtractionDropHit>>, selectedName: String?, selectedQuality: String?,
    onChooseItem: (ExtractionDropItem?) -> Unit, onSource: (ExtractionEntry) -> Unit, errorMessage: String?
) {
    var query by rememberSaveable { mutableStateOf("") }
    var quality by rememberSaveable { mutableStateOf<String?>(null) }
    var includeZero by rememberSaveable { mutableStateOf(false) }
    var sourceCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = lookup.keys.firstOrNull { it.name == selectedName && it.qualityLabel == selectedQuality }
    val results = remember(lookup, query, quality) {
        lookup.keys.filter { it.name.contains(query.trim(), true) && (quality == null || it.qualityLabel == quality) }
    }
    val hits = selected?.let { lookup[it] }.orEmpty()
    val categories = hits.map { it.source.filterCategory }.distinct()
    LaunchedEffect(categories) { if (categories.isNotEmpty() && sourceCategory !in categories) sourceCategory = null }
    val shownHits = hits.filter { (includeZero || it.source.dropCountState != DropCountState.ZERO) &&
        (sourceCategory == null || it.source.filterCategory == sourceCategory) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("lookup-header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(selected?.name ?: "这件物品，在哪个池？", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (selected != null) {
                    Text(selected.qualityLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { onChooseItem(null) }) { Text("返回物品列表") }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = sourceCategory == null, onClick = { sourceCategory = null }, label = { Text("全部来源") })
                        categories.forEach { category ->
                            FilterChip(selected = sourceCategory == category, onClick = { sourceCategory = category }, label = { Text(category) })
                        }
                    }
                    FilterChip(selected = includeZero, onClick = { includeZero = !includeZero }, label = { Text("包含零件数候选") })
                    Text("${shownHits.size} 个来源池 · ${hits.count { it.source.dropCountState == DropCountState.ZERO }} 个零件数来源",
                        style = MaterialTheme.typography.labelMedium)
                    Text("池内收录不代表必出；源表未披露逐项权重，不按出货概率排序。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("同名不同品质分别查询", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SearchBar(keyword = query, onKeywordChange = { query = it }, onSearch = {}, onClear = { query = "" },
                        isSearching = false, placeholder = "输入物品名，如切割器…")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = quality == null, onClick = { quality = null }, label = { Text("全部品质") })
                        lookup.keys.map { it.qualityLabel }.distinct().forEach { value ->
                            FilterChip(selected = quality == value, onClick = { quality = value }, label = { Text(value) })
                        }
                    }
                    Text("${results.size} 项", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selectedName != null) Text("当前数据未收录所选物品／品质，请重新选择。", style = MaterialTheme.typography.bodySmall)
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (selected != null) {
            if (shownHits.isEmpty()) item("no-source") {
                CatalogEmptyResults(message = "没有符合当前条件的来源池", onClear = { sourceCategory = null; includeZero = true })
            }
            items(shownHits, key = { it.source.id }, contentType = { "hit" }) { hit ->
                Surface(onClick = { onSource(hit.source) }, shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(hit.source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text((listOf(hit.source.category) + hit.source.tags).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(when (hit.source.dropCountState) {
                            DropCountState.ZERO -> "仅候选 · 件数恒 0，实际不出货"
                            DropCountState.UNKNOWN -> "池内记录 · 件数规则待确认"
                            DropCountState.POSITIVE -> "池内记录 · 逐项权重未披露"
                        }, style = MaterialTheme.typography.bodyMedium,
                            color = if (hit.source.dropCountState == DropCountState.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        Text("查看该来源完整池", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        } else {
            if (results.isEmpty()) item("empty") { CatalogEmptyResults(onClear = { query = ""; quality = null }) }
            items(results, key = { it.key }, contentType = { "lookup-item" }) { item ->
                Surface(onClick = { onChooseItem(item) }, color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${item.qualityLabel} · ${lookup.getValue(item).count { it.source.dropCountState != DropCountState.ZERO }} 个来源池（排除零件数）",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
