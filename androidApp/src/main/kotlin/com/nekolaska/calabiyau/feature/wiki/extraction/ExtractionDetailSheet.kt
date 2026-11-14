package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.AppShapes
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
    val effectiveQuality = resolveDropQualityLabel(selectedQuality, entry.dropPoolGroups.map { it.label })
    var referenceExpanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val isDropDetail = containerDetails || entry.type != ExtractionEntryType.ITEM
    // These entries are the reference itself, not extra rules behind an item list.
    // Legacy pool text also remains readable when it has no structured item groups.
    val referenceIsMainContent = entry.type == ExtractionEntryType.DROP_STAGE ||
        (entry.isDropPool && !containerDetails && entry.dropPoolGroups.isEmpty())
    val poolGroups = remember(entry.dropPoolGroups, poolQuery, effectiveQuality) {
        filterDropPoolGroups(entry.dropPoolGroups, poolQuery).filter { effectiveQuality == null || it.label == effectiveQuality }
    }
    val fields = remember(entry) {
        if (entry.dropPoolGroups.isNotEmpty()) entry.fields.filterNot { it.label == "掉落池条目（完整）" } else entry.fields
    }
    val evidence = remember(entry, query, isDropDetail) {
        if (isDropDetail) extractionDropMatchEvidence(entry, query) else null
    }
    LaunchedEffect(entry.dropPoolGroups) {
        if (entry.dropPoolGroups.isNotEmpty()) selectedQuality = effectiveQuality
    }
    val isFiltered = poolQuery.isNotBlank() || effectiveQuality != null
    val totalItems = entry.dropPoolGroups.sumOf { it.items.size }
    val matchingItems = poolGroups.sumOf { it.items.size }
    val clearFilters: () -> Unit = { poolQuery = ""; selectedQuality = null }
    val showAllItems: () -> Unit = {
        clearFilters()
        expandedGroups = entry.dropPoolGroups.map { it.key }
    }
    val referenceRotation by animateFloatAsState(if (referenceExpanded) 180f else 0f,
        animationSpec = tween(180), label = "drop-reference-arrow")

    WikiContentSheet(onDismiss = onDismiss) {
        // Ordinary loot/attachment details keep their full artwork and fields.
        if (!isDropDetail && entry.imageUrl != null) item(key = "image", contentType = "image") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                AsyncImage(
                    model = entry.imageUrl,
                    contentDescription = entry.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(180.dp).padding(16.dp)
                )
            }
        }
        item(key = "title", contentType = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val subtitle = (listOf(entry.category, entry.qualityLabel) + entry.tags).filter { it.isNotBlank() }
                if (subtitle.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subtitle.forEach { tag ->
                            Surface(
                                shape = AppShapes.chip,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    tag,
                                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                evidence?.let {
                    Surface(
                        shape = AppShapes.compactCard,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ) {
                        Text(
                            "${it.label}：${it.snippet}",
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        if (isDropDetail && (onViewSourceNotes != null || onRefreshPool != null)) item(key = "detail-actions") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onViewSourceNotes != null) FilledTonalButton(onClick = onViewSourceNotes) { Text("来源说明") }
                if (onRefreshPool != null) OutlinedButton(onClick = onRefreshPool, enabled = !poolIsLoading) {
                    Text(if (poolIsLoading) "正在刷新…" else "刷新物品池")
                }
            }
        }
        if (isDropDetail && entry.dropCountState == DropCountState.ZERO) item(key = "zero-warning") {
            Surface(
                shape = AppShapes.compactCard,
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "掉落数量为 0，下列候选物品不会从此来源掉落。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        if (offlineAgeMs != null || poolError != null || (containerDetails &&
                (entry.dropCountState == DropCountState.UNKNOWN || totalItems == 0))) item(key = "pool-status", contentType = "pool-status") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                offlineAgeMs?.let { OfflineBanner(ageMs = it) }
                poolError?.let {
                    Surface(shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            it,
                            Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                if (containerDetails && entry.dropCountState == DropCountState.UNKNOWN) {
                    Surface(shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "掉落数量待确认，物品名单仅供参考。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (containerDetails && totalItems == 0) {
                    Surface(shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Text(
                            if (entry.containerId == null) "完整物品池尚未公布" else "已公布的物品池为空",
                            Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        if (isDropDetail && entry.type == ExtractionEntryType.DROP_SOURCE) {
            item(key = "source-summary", contentType = "source-summary") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("掉落概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (entry.dropImages.isNotEmpty()) "部分物品配图 · 掉落方式见下方说明"
                        else "掉落摘要 · 完整名单见物品池",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            val summaryFields = fields.filter { it.label in setOf("必掉物", "掉落材料（按权重抽）", "掉落材料（按概率抽）",
                "限定池", "掉落池构成", "掉落池内最高权重", "抽中率最高", "掉落件数") }
            items(summaryFields.size, key = { "source-summary-field-$it" }, contentType = { "field" }) { index ->
                Surface(shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainer) {
                    DetailField(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        label = summaryFields[index].label,
                        value = summaryFields[index].value
                    )
                }
            }
            item(key = "source-pool-link", contentType = "source-pool-link") {
                if (onViewCompletePool != null) {
                    FilledTonalButton(onClick = onViewCompletePool, modifier = Modifier.fillMaxWidth()) {
                        Text(if (entry.containerId == null) "查看共用物品池" else "查看完整物品池")
                    }
                } else if (entry.containerId == null) {
                    Text("暂无对应物品池，已知掉落见上方说明。", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (entry.dropPoolGroups.isNotEmpty()) {
            item(key = "pool-search", contentType = "pool-search") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (entry.dropCountState == DropCountState.ZERO) "候选物品（无实际掉落）" else "池内物品",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    SearchBar(keyword = poolQuery, onKeywordChange = { poolQuery = it }, onSearch = {},
                        onClear = { poolQuery = "" }, isSearching = false, placeholder = "搜索池内物品…")
                    ExtractionPoolQualityMenu(labels = entry.dropPoolGroups.map { it.label }.distinct(),
                        selectedQuality = effectiveQuality, onSelect = { selectedQuality = it },
                        summary = if (isFiltered) "$matchingItems / $totalItems 件物品" else "共 $totalItems 件物品",
                        onReset = showAllItems.takeIf { isFiltered })
                    Text(if (entry.dropCountState == DropCountState.ZERO) "候选名单 · 实际掉落 0 件" else "候选名单 · 非必掉清单", style = MaterialTheme.typography.bodySmall,
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
            item(key = "reference-toggle", contentType = "reference-toggle") {
                Surface(
                    onClick = { referenceExpanded = !referenceExpanded },
                    modifier = Modifier.dropItemMotion(this).fillMaxWidth(),
                    shape = AppShapes.compactCard,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "掉落规则与资料",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            Icons.Outlined.ExpandMore,
                            modifier = Modifier.rotate(referenceRotation),
                            contentDescription = if (referenceExpanded) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (referenceExpanded) {
                if (entry.containerId != null) item(key = "container-id", contentType = "field") {
                    Surface(Modifier.dropItemMotion(this), shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainer) {
                        DetailField(Modifier.fillMaxWidth().padding(14.dp), label = "容器编号", value = entry.containerId)
                    }
                }
                if (containerDetails) item(key = "pool-rules", contentType = "description") {
                    Text(
                        "掉落数量确定后，每件物品按权重抽取。单件掉率及整箱获得概率暂无数据。",
                        modifier = Modifier.dropItemMotion(this),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (entry.description.isNotBlank()) item(key = "description", contentType = "description") {
                    Text(entry.description, modifier = Modifier.dropItemMotion(this), style = MaterialTheme.typography.bodyMedium)
                }
                items(fields.size, key = { "field-$it" }, contentType = { "field" }) { index ->
                    Surface(Modifier.dropItemMotion(this), shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainer) {
                        DetailField(Modifier.fillMaxWidth().padding(14.dp), label = fields[index].label, value = fields[index].value)
                    }
                }
            }
        } else {
            if (entry.description.isNotBlank()) item(key = "description", contentType = "description") {
                Surface(shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text(entry.description, Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(fields.size, key = { "field-$it" }, contentType = { "field" }) { index ->
                Surface(shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainer) {
                    DetailField(Modifier.fillMaxWidth().padding(14.dp), label = fields[index].label, value = fields[index].value)
                }
            }
        }
        item(key = "close", contentType = "close") {
            TextButton(onClick = onDismiss, modifier = Modifier.dropItemMotion(this).fillMaxWidth()) { Text(dismissLabel) }
        }
    }
}

@Composable
private fun ExtractionPoolQualityMenu(labels: List<String>, selectedQuality: String?, onSelect: (String?) -> Unit,
    summary: String, onReset: (() -> Unit)?) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        DropFilterBar(
            summary = summary,
            label = selectedQuality ?: "筛选品质",
            onClick = { expanded = true },
            activeFilter = selectedQuality != null,
            onReset = onReset
        )
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
