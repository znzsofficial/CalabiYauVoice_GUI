package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.CatalogEmptyResults
import com.nekolaska.calabiyau.core.ui.DetailField
import com.nekolaska.calabiyau.core.ui.OfflineBanner
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.WikiContentSheet
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
    artwork: DropArtworkIndex = DropArtworkIndex.Empty,
    dismissLabel: String = "返回",
    onViewSourceNotes: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    // A source name or rule can match the browser without matching any pool item.
    var poolQuery by rememberSaveable(entry.id) {
        mutableStateOf(query.takeIf { filterDropPoolGroups(entry.dropPoolGroups, it).isNotEmpty() }.orEmpty())
    }
    var expandedGroups by rememberSaveable(entry.id) { mutableStateOf(emptyList<String>()) }
    var selectedQuality by rememberSaveable(entry.id) { mutableStateOf(initialPoolQuality) }
    var referenceExpanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val isDropDetail = containerDetails || entry.type != ExtractionEntryType.ITEM
    // These entries are the reference itself, not extra rules behind an item list.
    // Legacy pool text also remains readable when it has no structured item groups.
    val referenceIsMainContent = entry.type == ExtractionEntryType.DROP_STAGE ||
        (entry.isDropPool && !containerDetails && entry.dropPoolGroups.isEmpty())
    val poolGroups = remember(entry.dropPoolGroups, poolQuery, selectedQuality) {
        filterDropPoolGroups(entry.dropPoolGroups, poolQuery).filter { selectedQuality == null || it.label == selectedQuality }
    }
    val fields = remember(entry) {
        if (entry.dropPoolGroups.isNotEmpty()) entry.fields.filterNot { it.label == "掉落池条目（完整）" } else entry.fields
    }
    val evidence = remember(entry, query, isDropDetail) {
        if (isDropDetail) extractionDropMatchEvidence(entry, query) else null
    }
    LaunchedEffect(entry.dropPoolGroups) {
        if (entry.dropPoolGroups.isNotEmpty() && entry.dropPoolGroups.none { it.label == selectedQuality }) selectedQuality = null
    }
    val isFiltered = poolQuery.isNotBlank() || selectedQuality != null
    val totalItems = entry.dropPoolGroups.sumOf { it.items.size }
    val matchingItems = poolGroups.sumOf { it.items.size }
    val clearFilters: () -> Unit = { poolQuery = ""; selectedQuality = null }
    val showAllItems: () -> Unit = {
        clearFilters()
        expandedGroups = entry.dropPoolGroups.map { it.key }
    }

    WikiContentSheet(onDismiss = onDismiss) {
        // Ordinary loot/attachment details keep their full artwork and fields.
        if (!isDropDetail && entry.imageUrl != null) item(key = "image", contentType = "image") {
            AsyncImage(model = entry.imageUrl, contentDescription = entry.name, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(180.dp))
        }
        item(key = "title", contentType = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val subtitle = (listOf(entry.category, entry.qualityLabel) + entry.tags).filter { it.isNotBlank() }
                if (subtitle.isNotEmpty()) Text(subtitle.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                evidence?.let {
                    Text("${it.label}：${it.snippet}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (isDropDetail && entry.dropCountState == DropCountState.ZERO) item(key = "zero-warning") {
            Text("这个来源不会掉落物品（件数恒为 0）。下方即使列有候选物品，也不会从这里获得。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        if (containerDetails && (offlineAgeMs != null || poolError != null ||
                entry.dropCountState == DropCountState.UNKNOWN || totalItems == 0)) item(key = "pool-status", contentType = "pool-status") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                offlineAgeMs?.let { OfflineBanner(ageMs = it) }
                poolError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (entry.dropCountState == DropCountState.UNKNOWN) Text(
                    "掉落件数尚不明确；池内有某件物品，不代表一定能从这里获得。",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (totalItems == 0) Text(
                    if (entry.containerId == null) "完整物品池尚未公布。" else "完整物品池已公布，目前为 0 件物品。",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (isDropDetail && entry.type == ExtractionEntryType.DROP_SOURCE) {
            item(key = "source-summary", contentType = "source-summary") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("掉落概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (entry.dropImages.isNotEmpty()) "来源卡中的物品配图，不代表完整物品池；是否必掉以原始说明为准。"
                        else "来源卡展示已公布的掉落摘要，完整物品名单以对应的物品池为准。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(entry.dropImages.size, key = { "source-image-$it" }, contentType = { "pool-item" }) { index ->
                val image = entry.dropImages[index]
                // The image is explicitly attached to this named item, not to the container.
                // An unqualified image must not create a name-only reverse-lookup identity.
                ExtractionDetailItemRow(name = image.name, qualityLabel = image.qualityLabel.orEmpty(),
                    qualityCode = image.qualityLabel?.let { Regex("^Q(\\d+)\\b").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: -1,
                    imageUrl = image.imageUrl, onClick = null)
            }
            // Only gameplay-facing summaries are promoted; every original field remains below.
            val summaryFields = fields.filter { it.label in setOf("必掉物", "掉落材料（按权重抽）", "掉落池构成", "掉落池内最高权重") }
            items(summaryFields.size, key = { "source-summary-field-$it" }, contentType = { "field" }) { index ->
                DetailField(label = summaryFields[index].label, value = summaryFields[index].value)
            }
            item(key = "source-pool-link", contentType = "source-pool-link") {
                if (entry.containerId != null && onViewCompletePool != null) {
                    FilledTonalButton(onClick = onViewCompletePool, modifier = Modifier.fillMaxWidth()) {
                        Text("查看这个来源的完整物品池")
                    }
                } else if (entry.containerId == null) {
                    Text("暂时无法对应完整物品池。", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (entry.dropPoolGroups.isNotEmpty()) {
            item(key = "pool-search", contentType = "pool-search") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (entry.dropCountState == DropCountState.ZERO) "配置中的候选物品" else "池内物品",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (isFiltered) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("正在显示筛选结果 · $matchingItems / $totalItems 件", style = MaterialTheme.typography.labelLarge)
                                Text(buildList {
                                    if (poolQuery.isNotBlank()) add("名称或品质包含「${poolQuery.trim()}」")
                                    selectedQuality?.let { add("品质：$it") }
                                }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = showAllItems) { Text("查看全部 $totalItems 件物品") }
                            }
                        }
                    } else Text("共 $totalItems 件物品 · 入池不代表必掉", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SearchBar(keyword = poolQuery, onKeywordChange = { poolQuery = it }, onSearch = {},
                        onClear = { poolQuery = "" }, isSearching = false, placeholder = "搜索池内物品…")
                    ExtractionPoolQualityMenu(labels = entry.dropPoolGroups.map { it.label }.distinct(),
                        selectedQuality = selectedQuality, onSelect = { selectedQuality = it })
                    if (onFindItem != null) Text("点击物品查找来源", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (matchingItems == 0 && isFiltered) item(key = "pool-empty", contentType = "pool-empty") {
                CatalogEmptyResults(message = "当前条件下没有物品", onClear = clearFilters.takeIf { isFiltered })
            }
            poolGroups.forEach { group ->
                extractionDropPoolSection(group, expanded = group.key in expandedGroups, artwork = artwork,
                    onItemClick = onFindItem?.let { find ->
                        { name -> find(ExtractionDropItem(name, group.label, group.qualityCode)) }
                    }, onToggle = {
                        expandedGroups = if (group.key in expandedGroups) expandedGroups - group.key else expandedGroups + group.key
                    })
            }
        }
        if (isDropDetail && !referenceIsMainContent) {
            if (onRefreshPool != null) item(key = "pool-refresh", contentType = "pool-refresh") {
                TextButton(onClick = onRefreshPool, enabled = !poolIsLoading) {
                    Text(if (poolIsLoading) "正在刷新…" else "刷新物品池")
                }
            }
            if (onViewSourceNotes != null) item(key = "source-notes", contentType = "source-notes") {
                TextButton(onClick = onViewSourceNotes) { Text("查看来源说明") }
            }
            item(key = "reference-toggle", contentType = "reference-toggle") {
                TextButton(onClick = { referenceExpanded = !referenceExpanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (referenceExpanded) "收起规则与图鉴资料" else "规则与图鉴资料", modifier = Modifier.weight(1f))
                    Icon(if (referenceExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (referenceExpanded) "收起" else "展开")
                }
            }
            if (referenceExpanded) {
                if (entry.containerId != null) item(key = "container-id", contentType = "field") {
                    DetailField(label = "容器编号", value = entry.containerId)
                }
                if (containerDetails) item(key = "pool-rules", contentType = "description") {
                    Text("先决定掉落件数，再逐件按权重抽取。池内名单不提供单件物品概率，也不能据此判断整箱至少获得一件的概率。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (entry.description.isNotBlank()) item(key = "description", contentType = "description") {
                    Text(entry.description, style = MaterialTheme.typography.bodyLarge)
                }
                items(fields.size, key = { "field-$it" }, contentType = { "field" }) { index ->
                    DetailField(label = fields[index].label, value = fields[index].value)
                }
            }
        } else {
            if (entry.description.isNotBlank()) item(key = "description", contentType = "description") {
                Text(entry.description, style = MaterialTheme.typography.bodyLarge)
            }
            items(fields.size, key = { "field-$it" }, contentType = { "field" }) { index ->
                DetailField(label = fields[index].label, value = fields[index].value)
            }
        }
        item(key = "close", contentType = "close") {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(dismissLabel) }
        }
    }
}

@Composable
private fun ExtractionPoolQualityMenu(labels: List<String>, selectedQuality: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("品质：${selectedQuality ?: "全部"}")
            Icon(Icons.Outlined.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("全部品质") }, modifier = Modifier.semantics { selected = selectedQuality == null },
                onClick = { onSelect(null); expanded = false })
            labels.forEach { label ->
                DropdownMenuItem(text = { Text(label) }, modifier = Modifier.semantics { selected = selectedQuality == label },
                    onClick = { onSelect(label); expanded = false })
            }
        }
    }
}
