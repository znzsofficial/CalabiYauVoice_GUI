package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
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
    onChooseItem: (ExtractionDropItem?) -> Unit, onSource: (ExtractionEntry) -> Unit,
    artwork: DropArtworkIndex = DropArtworkIndex.Empty,
    filtersAuthoritative: Boolean = true, isActive: Boolean = true,
    backLabel: String = "返回物品列表",
    loading: Boolean = false,
    loadFailed: Boolean = false,
    status: @Composable () -> Unit = {}
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
                if (selectedName != null) {
                    val label = selected?.qualityLabel ?: selectedQuality.orEmpty()
                    val qCode = selected?.qualityCode ?: 0
                    val qColor = extractionDropQualityColor(qCode)
                    Surface(
                        shape = AppShapes.compactCard,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, qColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ExtractionDropThumbnail(
                                selectedName, label, qCode,
                                artwork.imageFor(selectedName, label), Modifier.size(72.dp)
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(selectedName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                ExtractionDropQualityBadge(label, qCode)
                            }
                        }
                    }
                    DropFilterBar(
                        summary = if (loading && lookup.isEmpty()) "正在读取物品来源…"
                        else "${shownHits.size} 个来源 · ${if (includeZero) "含零掉落来源" else "已隐藏零掉落来源"}",
                        label = sourceCategory ?: "筛选来源",
                        onClick = { showFilters = true },
                        enabled = selected != null,
                        activeFilter = sourceCategory != null || includeZero,
                        onReset = if (sourceCategory != null || includeZero) ({ sourceCategory = null; includeZero = false }) else null
                    )
                    Text("可能来源 · 非必掉，具体掉率暂无数据", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selected == null && !loading && !loadFailed) {
                        Text("暂无该品质物品的来源资料", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { onChooseItem(null) }) { Text(backLabel) }
                    }
                } else {
                    Text("物品来源", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    SearchBar(keyword = query, onKeywordChange = { query = it }, onSearch = {}, onClear = { query = "" },
                        isSearching = false, placeholder = "搜索物品名，如切割器…")
                    DropFilterBar(
                        summary = if (loading && lookup.isEmpty()) "正在读取物品…" else "${results.size} 件物品",
                        label = currentQuality ?: "筛选品质",
                        onClick = { showFilters = true },
                        enabled = lookup.isNotEmpty(),
                        activeFilter = currentQuality != null,
                        onReset = if (currentQuality != null) ({ quality = null }) else null
                    )
                    Text("按品质区分物品 · 来源数不含零掉落来源", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                status()
            }
        }
        if (loading && lookup.isEmpty()) {
            items(6, key = { "lookup-skeleton:$it" }, contentType = { "skeleton" }) {
                DropLookupRowSkeleton(Modifier.dropItemMotion(this), source = selectedName != null)
            }
        } else if (selected != null) {
            if (shownHits.isEmpty()) item("no-source") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CatalogEmptyResults(message = if (!includeZero && hiddenZeroCount > 0)
                        "仅找到 $hiddenZeroCount 个零掉落来源，已被筛选隐藏" else "没有符合筛选条件的来源",
                        onClear = if (sourceCategory != null) ({ sourceCategory = null }) else null)
                }
            }
            items(shownHits, key = { it.source.id }, contentType = { "hit" }) { hit ->
                val isZero = hit.source.dropCountState == DropCountState.ZERO
                Surface(
                    onClick = { onSource(hit.source) },
                    modifier = Modifier.dropItemMotion(this),
                    shape = AppShapes.compactCard,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (isZero) MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExtractionDropSourceIconBox(hit.source)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(hit.source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                hit.source.filterCategory,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = smoothCornerShape(8.dp),
                                color = if (isZero) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    when (hit.source.dropCountState) {
                                        DropCountState.ZERO -> "无物品掉落 · 0 件"
                                        DropCountState.UNKNOWN -> "掉落数量待确认"
                                        DropCountState.POSITIVE -> "具体物品掉率暂无数据"
                                    },
                                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isZero) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isZero) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = "查看来源详情",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (selectedName == null) {
            if (results.isEmpty() && !(loadFailed && lookup.isEmpty())) item("empty") {
                CatalogEmptyResults(onClear = { query = ""; quality = null })
            }
            items(results, key = { it.key }, contentType = { "lookup-item" }) { item ->
                val qColor = extractionDropQualityColor(item.qualityCode ?: 0)
                Surface(
                    onClick = { onChooseItem(item) },
                    modifier = Modifier.dropItemMotion(this),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = AppShapes.compactCard,
                    border = BorderStroke(1.dp, qColor.copy(alpha = 0.28f))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExtractionDropThumbnail(
                            item.name, item.qualityLabel, item.qualityCode ?: 0,
                            artwork.imageFor(item.name, item.qualityLabel)
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            ExtractionDropQualityBadge(item.qualityLabel, item.qualityCode ?: 0)
                            Text(
                                "${lookup.getValue(item).count { it.source.dropCountState != DropCountState.ZERO }} 个来源",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = "查看物品来源",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    Surface(
                        shape = AppShapes.compactCard,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("显示零掉落来源", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("有候选物品，但掉落数量为 0", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = includeZero, onCheckedChange = { includeZero = it },
                                modifier = Modifier.semantics { contentDescription = "显示零掉落来源" })
                        }
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
