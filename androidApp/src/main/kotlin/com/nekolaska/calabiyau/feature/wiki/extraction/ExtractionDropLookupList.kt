package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.qualityCode

@Composable
internal fun ExtractionDropLookupList(
    lookup: Map<ExtractionDropItem, List<ExtractionDropHit>>, selectedName: String?, selectedQuality: String?,
    onChooseItem: (ExtractionDropItem?) -> Unit, onSource: (ExtractionEntry) -> Unit,
    artwork: DropArtworkIndex = DropArtworkIndex.Empty,
    filtersAuthoritative: Boolean = true, isActive: Boolean = true,
    backLabel: String = "返回物品列表",
    loading: Boolean = false,
    loadFailed: Boolean = false,
    rates: LoadState<List<DropRate>>?, onShowRates: (Boolean) -> Unit,
    status: @Composable () -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var quality by rememberSaveable { mutableStateOf<String?>(null) }
    var includeZero by rememberSaveable { mutableStateOf(false) }
    var sourceCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showLowRates by rememberSaveable(selectedName, selectedQuality) { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val itemListState = rememberLazyListState()
    val sourceListState = key(selectedName, selectedQuality) { rememberLazyListState() }
    val currentSelectedQuality = resolveDropQualityLabel(selectedQuality,
        lookup.keys.filter { it.name == selectedName }.map { it.qualityLabel })
    val selected = lookup.keys.singleOrNull { it.name == selectedName && it.qualityLabel == currentSelectedQuality }
    val selectedCode = selected?.qualityCode ?: qualityCode(selectedQuality.orEmpty())
    val rateSelection = remember(rates?.data, selectedCode, showLowRates) {
        rates?.let { selectDropRates(it.data, selectedCode, showLowRates) }
    }
    val ratesListState = rememberLazyListState()
    val qualities = remember(lookup) { lookup.keys.distinctBy { it.qualityLabel } }
    val currentQuality = resolveDropQualityLabel(quality, qualities.map { it.qualityLabel })
    val results = remember(lookup, query, currentQuality) {
        lookup.keys.filter { it.name.contains(query.trim(), true) && (currentQuality == null || it.qualityLabel == currentQuality) }
    }
    val distinctNames = remember(results) { results.distinctBy { it.name }.size }
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
    LazyColumn(
        Modifier.fillMaxSize(),
        state = if (selectedName == null) itemListState else if (rates != null) ratesListState else sourceListState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item("lookup-header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedName != null) {
                    val label = selected?.qualityLabel ?: selectedQuality.orEmpty()
                    val qCode = selectedCode ?: 0
                    val qColor = extractionDropQualityColor(qCode)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = smoothCornerShape(12.dp),
                        color = colors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Box(Modifier.fillMaxWidth().height(2.dp).background(qColor))
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ExtractionDropThumbnail(qCode, artwork.imageFor(selectedName, label), Modifier.size(64.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        selectedName,
                                        Modifier.semantics { heading() },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    ExtractionDropQualityBadge(label, qCode)
                                }
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = rates != null, onClick = { onShowRates(true) }, label = { Text("单件抽中率") })
                        FilterChip(selected = rates == null, onClick = { onShowRates(false) }, label = { Text("候选容器") })
                    }
                }
                if (selectedName != null && rates == null) {
                    val sourceFilterCount = (if (sourceCategory != null) 1 else 0) + (if (includeZero) 1 else 0)
                    DropFilterBar(
                        summary = if (loading && lookup.isEmpty()) "正在加载来源…"
                        else if (loadFailed && lookup.isEmpty()) "候选来源"
                        else if (hiddenZeroCount == 0) "${shownHits.size} 个候选来源"
                        else "${shownHits.size} 个候选来源（${if (includeZero) "含" else "已隐藏"} $hiddenZeroCount 个零掉落来源）",
                        label = sourceCategory ?: "筛选来源",
                        onClick = { showFilters = true },
                        enabled = selected != null,
                        activeFilter = sourceFilterCount > 0,
                        activeCount = sourceFilterCount
                    )
                    if (shownHits.isNotEmpty()) {
                        Text(
                            "件数概率不代表这件物品的掉率。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    if (selected == null && !loading && !loadFailed) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = smoothCornerShape(12.dp),
                            color = colors.surfaceContainerLow,
                            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("暂未找到该品质物品的掉落来源", style = MaterialTheme.typography.bodyMedium)
                                FilledTonalButton(
                                    onClick = { onChooseItem(null) },
                                    shape = smoothCornerShape(8.dp)
                                ) { Text(backLabel) }
                            }
                        }
                    }
                } else if (selectedName == null) {
                    SearchBar(
                        keyword = query,
                        onKeywordChange = { query = it },
                        onClear = { query = "" },
                        isSearching = false,
                        placeholder = "搜索物品名称"
                    )
                    DropFilterBar(
                        summary = if (loading && lookup.isEmpty()) "正在加载物品…"
                        else if (loadFailed && lookup.isEmpty()) "候选物品"
                        else if (distinctNames == results.size) "$distinctNames 种物品"
                        else "$distinctNames 种物品（按品质细分为 ${results.size} 项）",
                        label = currentQuality ?: "筛选品质",
                        onClick = { focus.clearFocus(); showFilters = true },
                        enabled = lookup.isNotEmpty(),
                        activeFilter = currentQuality != null,
                        activeCount = if (currentQuality != null) 1 else 0
                    )
                }
                if (rates == null) status()
            }
        }
        if (rates != null && rateSelection != null) {
            dropRateItems(rates, rateSelection, showLowRates, onShowAll = { showLowRates = it })
        } else if (loading && lookup.isEmpty()) {
            items(6, key = { "lookup-skeleton:$it" }, contentType = { "skeleton" }) {
                DropLookupRowSkeleton(Modifier.dropItemMotion(this), source = selectedName != null)
            }
        } else if (selected != null) {
            if (shownHits.isEmpty()) item("no-source") {
                ExtractionEmptyResults(
                    message = if (!includeZero && hiddenZeroCount > 0)
                        "仅有 $hiddenZeroCount 个零掉落来源" else "没有符合条件的来源",
                    onShowZero = if (!includeZero && hiddenZeroCount > 0) ({ includeZero = true }) else null,
                    onClearFilters = if (sourceCategory != null) ({ sourceCategory = null; includeZero = false }) else null
                )
            }
            items(shownHits, key = { it.source.id }, contentType = { "hit" }) { hit ->
                val isZero = hit.source.dropCountState == DropCountState.ZERO
                Surface(
                    onClick = { onSource(hit.source) },
                    modifier = Modifier.dropItemMotion(this).semantics { onClick(label = "查看来源详情", action = null) },
                    shape = smoothCornerShape(12.dp),
                    color = colors.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (isZero) colors.error.copy(alpha = 0.45f)
                        else colors.outlineVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExtractionDropSourceIconBox(hit.source)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                hit.source.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                listOfNotNull(dropCandidateSummary(hit.source, isContainer = true), hit.source.filterCategory).joinToString("，"),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                color = colors.onSurfaceVariant
                            )
                            DropCountStatus(hit.source)
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colors.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (selectedName == null) {
            if (results.isEmpty() && !(loadFailed && lookup.isEmpty())) item("empty") {
                ExtractionEmptyResults(
                    message = if (lookup.isEmpty()) "暂无候选物品" else "没有符合条件的物品",
                    onClearQuery = if (query.isNotEmpty()) ({ query = "" }) else null,
                    onClearFilters = if (currentQuality != null) ({ quality = null }) else null
                )
            }
            items(results, key = { it.key }, contentType = { "lookup-item" }) { item ->
                Surface(
                    onClick = { onChooseItem(item) },
                    modifier = Modifier.dropItemMotion(this).semantics { onClick(label = "查看物品来源", action = null) },
                    color = colors.surfaceContainerLow,
                    shape = smoothCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExtractionDropThumbnail(
                            item.qualityCode ?: 0,
                            artwork.imageFor(item.name, item.qualityLabel)
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ExtractionDropQualityBadge(item.qualityLabel, item.qualityCode ?: 0)
                                val total = lookup.getValue(item).size
                                val count = lookup.getValue(item).count { it.source.dropCountState != DropCountState.ZERO }
                                Text(
                                    when {
                                        count == 0 -> "$total 个来源均为零掉落"
                                        count < total -> "$count 个候选来源（另有 ${total - count} 个零掉落来源）"
                                        else -> "$count 个候选来源"
                                    },
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    if (showFilters && isActive) {
        WikiSelectionSheet(title = if (selectedName == null) "物品品质" else "筛选来源", onDismiss = { showFilters = false }) {
            if (selectedName == null) {
                item("all-quality") {
                    SelectionOptionRow(
                        label = "全部品质",
                        selected = currentQuality == null,
                        onClick = { quality = null; showFilters = false }
                    )
                }
                items(qualities, key = { "quality:${it.qualityLabel}" }) { item ->
                    SelectionOptionRow(
                        label = item.qualityLabel,
                        selected = currentQuality == item.qualityLabel,
                        onClick = { quality = item.qualityLabel; showFilters = false }
                    )
                }
            } else {
                item("zero-count") {
                    Surface(
                        shape = smoothCornerShape(12.dp),
                        color = colors.surfaceContainer,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(value = includeZero, role = Role.Switch, onValueChange = { includeZero = it })
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "显示零掉落来源",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(checked = includeZero, onCheckedChange = null)
                        }
                    }
                }
                item("all-sources") {
                    SelectionOptionRow(
                        label = "全部来源类别",
                        selected = sourceCategory == null,
                        onClick = { sourceCategory = null; showFilters = false }
                    )
                }
                items(categories, key = { "source:$it" }) { category ->
                    SelectionOptionRow(
                        label = category,
                        selected = sourceCategory == category,
                        onClick = { sourceCategory = category; showFilters = false }
                    )
                }
            }
        }
    }
}
