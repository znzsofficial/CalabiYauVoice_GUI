package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.DetailField
import com.nekolaska.calabiyau.core.ui.ImagePreviewDialog
import com.nekolaska.calabiyau.core.ui.OfflineBanner
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.WikiContentSheet
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*

@Composable
internal fun ExtractionItemDetailSheet(entry: ExtractionEntry, kind: ExtractionCatalogKind, onDismiss: () -> Unit) {
    var preview by rememberSaveable(entry.id) { mutableStateOf(false) }
    val image = entry.imageUrl?.takeIf { it.isNotBlank() }
    var imageReady by remember(image) { mutableStateOf(false) }
    val isAttachment = kind == ExtractionCatalogKind.ATTACHMENTS
    val fields = remember(entry.fields) { entry.fields.filter { it.value.isNotBlank() }.distinct() }
    val (metrics, prose) = remember(fields) { fields.partition { it.label in itemMetricLabels } }
    val (effects, other) = remember(prose) { prose.partition { it.label in itemEffectLabels } }
    WikiContentSheet(onDismiss = onDismiss) {
        item("title") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (image != null) Surface(modifier = Modifier.size(if (LocalDensity.current.fontScale > 1.3f) 60.dp else 76.dp),
                    shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Box(if (imageReady) Modifier.clickable(role = Role.Button, onClickLabel = "预览图片") { preview = true } else Modifier) {
                        ExtractionCatalogArtwork(entry, Modifier.fillMaxSize().padding(6.dp), onReady = { imageReady = it })
                        if (imageReady) Icon(Icons.Outlined.ZoomIn, "${entry.name}图片", Modifier.align(Alignment.BottomEnd).padding(3.dp).size(16.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (entry.qualityLabel.isNotBlank()) ExtractionDropQualityBadge(entry.qualityLabel, entry.quality ?: -1)
                        Text(if (isAttachment) "槽位：${entry.category}" else entry.category, modifier = Modifier.align(Alignment.CenterVertically),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (!isAttachment && metrics.isNotEmpty()) item("metrics") { ItemMetrics(metrics) }
        items(effects.size, key = { "effect:$it" }) { index -> DetailField(label = effects[index].label, value = effects[index].value) }
        if (entry.description.isNotBlank() && fields.none { it.value == entry.description }) item("description") {
            DetailField(label = "描述", value = entry.description)
        }
        if (isAttachment && metrics.isNotEmpty()) item("metrics") { ItemMetrics(metrics) }
        items(other.size, key = { "field:$it" }) { index -> DetailField(label = other[index].label, value = other[index].value) }
        val tags = entry.tags.distinct().filter { it.isNotBlank() && it != entry.category && it != entry.qualityLabel }
        if (tags.isNotEmpty()) item("tags") { DetailField(label = "标签", value = tags.joinToString("、")) }
        item("close") { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") } }
    }
    if (preview && image != null) ImagePreviewDialog(model = image, contentDescription = entry.name, onDismiss = { preview = false })
}

private val itemMetricLabels = setOf("价值", "占格", "重量", "售价", "搜刮耗时", "叠加上限")
private val itemEffectLabels = setOf("实际效果", "适用", "使用效果")

@Composable
private fun ItemMetrics(fields: List<ExtractionField>) {
    Surface(shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainer) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(14.dp)) {
            val gap = 12.dp
            val cellWidth = 88.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
            val columns = ((maxWidth + gap) / (cellWidth + gap)).toInt().coerceIn(1, 3)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                fields.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { field ->
                            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                                Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(field.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

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
    sharedSourceList: Boolean = false,
    evidenceGroups: List<ExtractionDropPoolGroup> = entry.dropPoolGroups,
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
    val readingDescription = remember(entry.description, entry.sectionIntroduction) {
        listOf(entry.description, entry.sectionIntroduction).filter { it.isNotBlank() }.joinToString("\n")
    }
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
    val countRule = if (containerDetails && entry.dropCountState != DropCountState.ZERO) fields.firstOrNull { it.label == "件数概率" } else null
    val (summaryFields, referenceFields) = remember(entry.type, fields) {
        fields.partition { entry.type == ExtractionEntryType.DROP_SOURCE && it.label in sourceSummaryLabels }
    }
    val otherFields = referenceFields.filterNot { it == countRule }
    val evidence = remember(entry, query, evidenceGroups) { extractionDropMatchEvidence(entry, query, evidenceGroups) }
    LaunchedEffect(entry.dropPoolGroups) {
        if (entry.dropPoolGroups.isNotEmpty()) selectedQuality = effectiveQuality
    }
    val isFiltered = poolQuery.isNotBlank() || effectiveQuality != null
    val totalItems = entry.dropPoolGroups.sumOf { it.items.size }
    val matchingItems = poolGroups.sumOf { it.items.size }
    val showAllItems: () -> Unit = {
        poolQuery = ""
        selectedQuality = null
        expandedGroups = entry.dropPoolGroups.map { it.key }
    }
    val referenceRotation by animateFloatAsState(if (referenceExpanded) 180f else 0f,
        animationSpec = tween(DropMotion.reveal), label = "drop-reference-arrow")

    WikiContentSheet(onDismiss = onDismiss) {
        item(key = "title", contentType = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val subtitle = (listOf(entry.category, entry.qualityLabel) + entry.tags).filter { it.isNotBlank() }.distinct()
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
                evidence?.caption?.let { caption ->
                    Surface(
                        shape = AppShapes.compactCard,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ) {
                        Text(
                            caption,
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        if (onViewSourceNotes != null || onRefreshPool != null) item(key = "detail-actions") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onViewSourceNotes != null) FilledTonalButton(onClick = onViewSourceNotes) { Text("来源说明") }
                if (onRefreshPool != null) OutlinedButton(onClick = onRefreshPool, enabled = !poolIsLoading) {
                    Text(if (poolIsLoading) "正在刷新…" else "刷新掉落池")
                }
            }
        }
        if (entry.dropCountState == DropCountState.ZERO) item(key = "zero-warning") {
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
                        "此来源固定掉落 0 件物品。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        if (offlineAgeMs != null || poolError != null || countRule != null || (containerDetails &&
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
                if (countRule != null && entry.dropCountState == DropCountState.POSITIVE) {
                    DetailField(label = countRule.label, value = countRule.value)
                }
                if (containerDetails && entry.dropCountState == DropCountState.UNKNOWN) {
                    Surface(shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            val reportedCount = countRule?.value
                            Text(
                                if (reportedCount.isNullOrBlank() || reportedCount == "—") "掉落件数未知"
                                else "掉落件数未知\n件数概率：$reportedCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (containerDetails && totalItems == 0) {
                    Surface(shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Text(
                            if (entry.containerId == null) "候选物品尚未公布" else "已公布的掉落池中没有候选物品",
                            Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        if (entry.type == ExtractionEntryType.DROP_SOURCE) {
            item(key = "source-summary", contentType = "source-summary") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onViewCompletePool != null) {
                        FilledTonalButton(onClick = onViewCompletePool, modifier = Modifier.fillMaxWidth()) {
                            Text(if (entry.containerId == null) "查看共用掉落池" else "查看完整掉落池")
                        }
                    } else if (entry.containerId == null) {
                        Text("暂无对应的掉落池", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (entry.dropImages.isNotEmpty() || summaryFields.isNotEmpty()) Text("掉落概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
            items(summaryFields.size, key = { "source-summary-field-$it" }, contentType = { "field" }) { index ->
                Surface(shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainer) {
                    DetailField(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        label = summaryFields[index].label,
                        value = summaryFields[index].value
                    )
                }
            }
        }
        if (entry.dropPoolGroups.isNotEmpty()) {
            item(key = "pool-search", contentType = "pool-search") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("候选物品",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    SearchBar(keyword = poolQuery, onKeywordChange = { poolQuery = it },
                        onClear = { poolQuery = "" }, isSearching = false, placeholder = "搜索候选物品")
                    ExtractionPoolQualityFilter(labels = entry.dropPoolGroups.map { it.label }.distinct(),
                        selectedQuality = effectiveQuality, onSelect = { selectedQuality = it },
                        summary = if (isFiltered) "$matchingItems / $totalItems 项" else "$totalItems 项")
                    if (isFiltered) {
                        val focus = LocalFocusManager.current
                        TextButton(onClick = { focus.clearFocus(); showAllItems() }) { Text("查看全部候选物品") }
                    }
                    Text(if (sharedSourceList) "共用名单的顺序不代表此来源的掉率高低。" else "候选名单不代表物品掉率。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (matchingItems == 0 && isFiltered) item(key = "pool-empty", contentType = "pool-empty") {
                ExtractionEmptyResults(message = "没有符合条件的候选物品", onClearQuery = if (poolQuery.isNotEmpty()) ({ poolQuery = "" }) else null,
                    onClearFilters = if (effectiveQuality != null) ({ selectedQuality = null }) else null)
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
        if (!referenceIsMainContent) {
            val hasReference = otherFields.isNotEmpty() || readingDescription.isNotBlank() || containerDetails || entry.containerId != null
            if (hasReference) item(key = "reference-toggle", contentType = "reference-toggle") {
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
                            "掉落规则与说明",
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
                        "先确定件数，再按权重逐件独立抽取。单件抽中率不等于整箱至少获得一件该物品的概率。",
                        modifier = Modifier.dropItemMotion(this),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (readingDescription.isNotBlank()) item(key = "description", contentType = "description") {
                    Text(readingDescription, modifier = Modifier.dropItemMotion(this), style = MaterialTheme.typography.bodyMedium)
                }
                items(otherFields.size, key = { "field-$it" }, contentType = { "field" }) { index ->
                    Surface(Modifier.dropItemMotion(this), shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainer) {
                        DetailField(Modifier.fillMaxWidth().padding(14.dp), label = otherFields[index].label, value = otherFields[index].value)
                    }
                }
            }
        } else {
            if (readingDescription.isNotBlank()) item(key = "description", contentType = "description") {
                Surface(shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text(readingDescription, Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium)
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
private fun ExtractionPoolQualityFilter(labels: List<String>, selectedQuality: String?, onSelect: (String?) -> Unit, summary: String) {
    var expanded by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Column {
        DropFilterBar(summary = summary, label = selectedQuality ?: "筛选品质", activeFilter = selectedQuality != null,
            onClick = { focus.clearFocus(); expanded = !expanded })
        if (expanded) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf(null) + labels).forEach { label ->
                FilterChip(selected = selectedQuality == label, label = { Text(label ?: "全部品质") },
                    onClick = { onSelect(label); expanded = false })
            }
        }
    }
}

private val sourceSummaryLabels = setOf("必掉物", "掉落材料（按权重抽）", "掉落材料（按概率抽）", "掉落物品",
    "限定池", "掉落池构成", "掉落池内最高权重", "抽中率最高", "掉落件数")
