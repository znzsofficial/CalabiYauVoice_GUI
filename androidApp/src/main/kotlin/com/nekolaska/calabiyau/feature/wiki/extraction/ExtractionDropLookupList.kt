package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*

@Composable
internal fun ExtractionDropLookupList(
    lookup: Map<ExtractionDropItem, List<ExtractionDropHit>>, selectedName: String?, selectedQuality: String?,
    onChooseItem: (ExtractionDropItem?) -> Unit, onSource: (ExtractionEntry) -> Unit, errorMessage: String?,
    artwork: DropArtworkIndex = DropArtworkIndex.Empty,
    filtersAuthoritative: Boolean = true, isActive: Boolean = true,
    backLabel: String = "返回物品列表"
) {
    var query by rememberSaveable { mutableStateOf("") }
    var quality by rememberSaveable { mutableStateOf<String?>(null) }
    var includeZero by rememberSaveable { mutableStateOf(false) }
    var sourceCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val itemListState = rememberLazyListState()
    val sourceListState = key(selectedName, selectedQuality) { rememberLazyListState() }
    val currentSelectedQuality = resolveDropQualityLabel(selectedQuality,
        lookup.keys.filter { it.name == selectedName }.map { it.qualityLabel })
    val selected = lookup.keys.singleOrNull { it.name == selectedName && it.qualityLabel == currentSelectedQuality }
    val qualities = remember(lookup) { lookup.keys.distinctBy { it.qualityLabel } }
    val currentQuality = resolveDropQualityLabel(quality, qualities.map { it.qualityLabel })
    val results = remember(lookup, query, currentQuality) {
        lookup.keys.filter { it.name.contains(query.trim(), true) && (currentQuality == null || it.qualityLabel == currentQuality) }
    }
    val hits = selected?.let { lookup[it] }.orEmpty()
    val categories = hits.map { it.source.filterCategory }.distinct()
    // Empty prefetches are not authoritative: retain the user's filters until data arrives.
    LaunchedEffect(lookup, selectedName, selectedQuality, filtersAuthoritative) {
        if (filtersAuthoritative && lookup.isNotEmpty()) {
            quality = currentQuality
            if (selectedName != null && sourceCategory != null && sourceCategory !in categories) sourceCategory = null
        }
    }
    val shownHits = hits.filter { (includeZero || it.source.dropCountState != DropCountState.ZERO) &&
        (sourceCategory == null || it.source.filterCategory == sourceCategory) }
    val hiddenZeroCount = hits.count { it.source.dropCountState == DropCountState.ZERO &&
        (sourceCategory == null || it.source.filterCategory == sourceCategory) }
    LazyColumn(Modifier.fillMaxSize(), state = if (selectedName == null) itemListState else sourceListState,
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item("lookup-header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selected != null) {
                    TextButton(onClick = { onChooseItem(null) }) { Text(backLabel) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExtractionDropThumbnail(selected.name, selected.qualityLabel, selected.qualityCode ?: 0,
                            artwork.imageFor(selected.name, selected.qualityLabel), Modifier.size(72.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(selected.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            ExtractionDropQualityBadge(selected.qualityLabel, selected.qualityCode ?: 0)
                        }
                    }
                    Text("去哪找 · ${shownHits.size} 个已收录来源", style = MaterialTheme.typography.titleMedium)
                    DropLookupFilterEntry(
                        summary = "${sourceCategory ?: "全部来源"} · ${if (includeZero) "显示不出货的来源" else "排除不出货的来源"}",
                        onClick = { showFilters = true }
                    )
                    Text("候选不等于掉率；已收录不代表必出。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (selectedName != null) {
                    Text(selectedName, style = MaterialTheme.typography.titleLarge)
                    Text(if (lookup.isEmpty()) "来源数据尚未就绪" else "当前数据未收录所选物品／品质",
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onChooseItem(null) }) { Text(backLabel) }
                } else {
                    Text("这件物品，去哪找？", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    SearchBar(keyword = query, onKeywordChange = { query = it }, onSearch = {}, onClear = { query = "" },
                        isSearching = false, placeholder = "搜索物品名，如切割器…")
                    DropLookupFilterEntry(summary = "${currentQuality ?: "全部品质"} · ${results.size} 项", onClick = { showFilters = true })
                    Text("同名不同品质分别收录 · 来源数排除不出货来源", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (selected != null) {
            if (shownHits.isEmpty()) item("no-source") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CatalogEmptyResults(message = "没有符合当前筛选的已收录来源")
                    if (sourceCategory != null) TextButton(onClick = { sourceCategory = null }) { Text("查看全部来源类别") }
                    if (!includeZero && hiddenZeroCount > 0) TextButton(onClick = { includeZero = true }) {
                        Text("显示不出货的来源（$hiddenZeroCount）")
                    }
                }
            }
            items(shownHits, key = { it.source.id }, contentType = { "hit" }) { hit ->
                Surface(onClick = { onSource(hit.source) }, shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExtractionDropSourceIcon(hit.source)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(hit.source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(hit.source.filterCategory, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(when (hit.source.dropCountState) {
                                DropCountState.ZERO -> "实际不出货 · 件数恒为 0"
                                DropCountState.UNKNOWN -> "已收录 · 件数规则待确认"
                                DropCountState.POSITIVE -> "已收录 · 逐项掉率未披露"
                            }, style = MaterialTheme.typography.bodySmall,
                                color = if (hit.source.dropCountState == DropCountState.ZERO) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "查看来源详情",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else if (selectedName == null) {
            if (results.isEmpty()) item("empty") { CatalogEmptyResults(onClear = { query = ""; quality = null }) }
            items(results, key = { it.key }, contentType = { "lookup-item" }) { item ->
                Surface(onClick = { onChooseItem(item) }, color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExtractionDropThumbnail(item.name, item.qualityLabel, item.qualityCode ?: 0,
                            artwork.imageFor(item.name, item.qualityLabel))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            ExtractionDropQualityBadge(item.qualityLabel, item.qualityCode ?: 0)
                            Text("${lookup.getValue(item).count { it.source.dropCountState != DropCountState.ZERO }} 个已收录来源",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "查看物品来源",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (showFilters && isActive) {
        WikiSelectionSheet(title = if (selectedName == null) "筛选物品品质" else "筛选来源", onDismiss = { showFilters = false }) {
            if (selectedName == null) {
                item("all-quality") {
                    SelectionOptionRow(label = "全部品质", selected = currentQuality == null, onClick = { quality = null; showFilters = false })
                }
                items(qualities, key = { "quality:${it.qualityLabel}" }) { item ->
                    SelectionOptionRow(label = item.qualityLabel, selected = currentQuality == item.qualityLabel,
                        onClick = { quality = item.qualityLabel; showFilters = false })
                }
            } else {
                item("zero-count") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("显示不出货的来源", style = MaterialTheme.typography.bodyLarge)
                            Text("件数恒为 0，仅保留候选池记录", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = includeZero, onCheckedChange = { includeZero = it },
                            modifier = Modifier.semantics { contentDescription = "显示不出货的来源" })
                    }
                }
                item("all-sources") {
                    SelectionOptionRow(label = "全部来源类别", selected = sourceCategory == null,
                        onClick = { sourceCategory = null; showFilters = false })
                }
                items(categories, key = { "source:$it" }) { category ->
                    SelectionOptionRow(label = category, selected = sourceCategory == category,
                        onClick = { sourceCategory = category; showFilters = false })
                }
            }
        }
    }
}

@Composable
private fun DropLookupFilterEntry(summary: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onClick) {
            Icon(Icons.Outlined.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("筛选")
        }
        Text(summary, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
