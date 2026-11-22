package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
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
import com.nekolaska.calabiyau.core.ui.DetailField
import com.nekolaska.calabiyau.core.ui.ImagePreviewDialog
import com.nekolaska.calabiyau.core.ui.LoadState
import com.nekolaska.calabiyau.core.ui.OfflineBanner
import com.nekolaska.calabiyau.core.ui.SearchBar
import com.nekolaska.calabiyau.core.ui.WikiContentSheet
import com.nekolaska.calabiyau.core.ui.retainedWikiPageError
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*

@Composable
internal fun ExtractionItemDetailSheet(entry: ExtractionEntry, kind: ExtractionCatalogKind, onDismiss: () -> Unit) {
    var preview by rememberSaveable(entry.id) { mutableStateOf(false) }
    val image = entry.imageUrl?.takeIf { it.isNotBlank() }
    var imageReady by remember(image) { mutableStateOf(false) }
    val isAttachment = kind == ExtractionCatalogKind.ATTACHMENTS
    val qualityCode = entry.quality?.takeIf { it in 1..5 } ?: -1
    val qualityColor = extractionDropQualityColor(qualityCode)
    val colors = MaterialTheme.colorScheme
    val fields = remember(entry.fields) { entry.fields.filter { it.value.isNotBlank() }.distinct() }
    val (metrics, prose) = remember(fields) { fields.partition { it.label in itemMetricLabels } }
    val (effects, other) = remember(prose) { prose.partition { it.label in itemEffectLabels } }
    val descriptionField = remember(entry.description, fields) {
        entry.description.takeIf { it.isNotBlank() && fields.none { f -> f.value == it } }
            ?.let { ExtractionField("描述", it) }
    }
    val primaryProse = remember(effects, descriptionField) { effects + listOfNotNull(descriptionField) }
    val tags = remember(entry.tags, entry.category, entry.qualityLabel) {
        entry.tags.distinct().filter { it.isNotBlank() && it != entry.category && it != entry.qualityLabel }
    }
    val secondaryProse = remember(other, tags) {
        other + listOfNotNull(tags.takeIf { it.isNotEmpty() }?.let { ExtractionField("标签", it.joinToString("、")) })
    }
    WikiContentSheet(onDismiss = onDismiss) {
        item("title") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(12.dp),
                color = colors.surfaceContainer,
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(qualityColor))
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (image != null) {
                            Surface(
                                modifier = Modifier.size(if (LocalDensity.current.fontScale > 1.3f) 64.dp else 78.dp),
                                shape = smoothCornerShape(10.dp),
                                color = colors.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    if (qualityCode in 1..5) qualityColor.copy(alpha = 0.45f)
                                    else colors.outlineVariant.copy(alpha = 0.55f)
                                )
                            ) {
                                Box(if (imageReady) Modifier.clickable(role = Role.Button, onClickLabel = "预览图片") { preview = true } else Modifier) {
                                    ExtractionCatalogArtwork(entry, Modifier.fillMaxSize().padding(6.dp), onReady = { imageReady = it })
                                    if (imageReady) {
                                        Surface(
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                                            shape = smoothCornerShape(5.dp),
                                            color = colors.surfaceContainerLow.copy(alpha = 0.88f),
                                            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
                                        ) {
                                            Icon(
                                                Icons.Outlined.ZoomIn,
                                                "${entry.name}图片",
                                                Modifier.padding(2.dp).size(13.dp),
                                                tint = colors.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(
                                entry.name,
                                Modifier.semantics { heading() },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (entry.qualityLabel.isNotBlank()) ExtractionDropQualityBadge(entry.qualityLabel, qualityCode)
                                if (entry.category.isNotBlank()) {
                                    Surface(
                                        shape = smoothCornerShape(6.dp),
                                        color = colors.surfaceContainerHigh,
                                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
                                    ) {
                                        Text(
                                            if (isAttachment) "槽位：${entry.category}" else entry.category,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!isAttachment && metrics.isNotEmpty()) item("metrics") { ItemMetrics(metrics) }
        if (primaryProse.isNotEmpty()) item("primary-prose") { DetailInfoGroupCard(primaryProse) }
        if (isAttachment && metrics.isNotEmpty()) item("metrics") { ItemMetrics(metrics) }
        if (secondaryProse.isNotEmpty()) item("secondary-prose") { DetailInfoGroupCard(secondaryProse) }
        item("close") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilledTonalButton(onClick = onDismiss, shape = smoothCornerShape(10.dp)) { Text("关闭") }
            }
        }
    }
    if (preview && image != null) ImagePreviewDialog(model = image, contentDescription = entry.name, onDismiss = { preview = false })
}

private val itemMetricLabels = setOf("价值", "占格", "重量", "售价", "搜刮耗时", "叠加上限")
private val itemEffectLabels = setOf("实际效果", "适用", "使用效果")

@Composable
private fun ItemMetrics(fields: List<ExtractionField>) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val cellWidth = 92.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        val columns = ((maxWidth + gap) / (cellWidth + gap)).toInt().coerceIn(1, 3)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            fields.chunked(columns).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    row.forEach { field ->
                        Surface(
                            modifier = Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) {},
                            shape = smoothCornerShape(10.dp),
                            color = colors.surfaceContainer,
                            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
                        ) {
                            Column(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    field.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
                                Text(
                                    field.value,
                                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DetailInfoGroupCard(fields: List<ExtractionField>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = smoothCornerShape(12.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            fields.forEachIndexed { index, field ->
                if (index > 0) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.40f))
                DetailField(label = field.label, value = field.value)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtractionDetailSheet(
    entry: ExtractionEntry, query: String, resource: LoadState<List<ExtractionEntry>>,
    onViewCompletePool: (() -> Unit)? = null,
    onFindItem: ((ExtractionDropItem) -> Unit)? = null,
    containerDetails: Boolean = false,
    initialPoolQuality: String? = null,
    artwork: DropArtworkIndex = DropArtworkIndex.Empty,
    dismissLabel: String = "返回",
    onViewSourceNotes: (() -> Unit)? = null,
    sharedSourceList: Boolean = false,
    evidenceGroups: List<ExtractionDropPoolGroup> = entry.dropPoolGroups,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val subject = when (entry.type) {
        ExtractionEntryType.DROP_SOURCE -> "来源说明"
        ExtractionEntryType.DROP_STAGE -> "首领阶段对照"
        ExtractionEntryType.DROP_POOL -> "掉落池"
        ExtractionEntryType.ITEM -> "掉落图鉴"
    }
    val pending = resource.isLoading || resource.isShowingCachedPrefetch
    val offlineAgeMs = resource.cacheAgeMs.takeIf { resource.isOffline && !resource.isShowingCachedPrefetch }
    val refreshError = resource.error?.let { "${if (containerDetails) "容器掉落表" else subject}：${retainedWikiPageError(it)}" }
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
    val referenceRotation by animateFloatAsState(
        if (referenceExpanded) 180f else 0f,
        animationSpec = tween(DropMotion.reveal),
        label = "drop-reference-arrow"
    )
    val headerAccent = if (entry.dropCountState == DropCountState.ZERO) colors.error else colors.primary

    WikiContentSheet(onDismiss = onDismiss) {
        item(key = "title", contentType = "title") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(12.dp),
                color = colors.surfaceContainer,
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(headerAccent))
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            entry.name,
                            Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val subtitle = (listOf(entry.category, entry.qualityLabel) + entry.tags).filter { it.isNotBlank() }.distinct()
                        if (subtitle.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                subtitle.forEach { tag ->
                                    Surface(
                                        shape = smoothCornerShape(6.dp),
                                        color = colors.surfaceContainerHigh,
                                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
                                    ) {
                                        Text(
                                            tag,
                                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        evidence?.caption?.let { caption ->
                            Surface(
                                shape = smoothCornerShape(8.dp),
                                color = colors.primaryContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.28f))
                            ) {
                                Text(
                                    caption,
                                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "detail-actions") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (onViewSourceNotes != null) {
                    FilledTonalButton(onClick = onViewSourceNotes, shape = smoothCornerShape(10.dp)) {
                        Text("来源说明")
                    }
                }
                OutlinedButton(
                    onClick = { resource.reload(forceRefresh = true) },
                    enabled = !pending,
                    shape = smoothCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.65f))
                ) {
                    Text(if (pending) "正在刷新…" else if (refreshError != null) "重新加载$subject" else "刷新$subject")
                }
            }
        }
        if (entry.dropCountState == DropCountState.ZERO) item(key = "zero-warning") {
            Surface(
                shape = smoothCornerShape(12.dp),
                color = colors.errorContainer,
                border = BorderStroke(1.dp, colors.error.copy(alpha = 0.35f))
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
                        tint = colors.onErrorContainer
                    )
                    Text(
                        "此来源固定掉落 0 件物品。",
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = colors.onErrorContainer
                    )
                }
            }
        }
        if (offlineAgeMs != null || refreshError != null || countRule != null || (containerDetails &&
                (entry.dropCountState == DropCountState.UNKNOWN || totalItems == 0))) item(key = "pool-status", contentType = "pool-status") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                offlineAgeMs?.let { OfflineBanner(ageMs = it) }
                refreshError?.let {
                    Surface(
                        shape = smoothCornerShape(12.dp),
                        color = colors.errorContainer,
                        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.35f))
                    ) {
                        Text(
                            it,
                            Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onErrorContainer
                        )
                    }
                }
                if (countRule != null && entry.dropCountState == DropCountState.POSITIVE) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = smoothCornerShape(12.dp),
                        color = colors.surfaceContainer,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                countRule.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                countRule.value,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                            )
                        }
                    }
                }
                if (containerDetails && entry.dropCountState == DropCountState.UNKNOWN) {
                    Surface(
                        shape = smoothCornerShape(12.dp),
                        color = colors.surfaceContainer,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = colors.primary)
                            val reportedCount = countRule?.value
                            Text(
                                if (reportedCount.isNullOrBlank() || reportedCount == "—") "掉落件数未知"
                                else "掉落件数未知\n件数概率：$reportedCount",
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
                if (containerDetails && totalItems == 0) {
                    Surface(
                        shape = smoothCornerShape(12.dp),
                        color = colors.surfaceContainer,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
                    ) {
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
                        FilledTonalButton(
                            onClick = onViewCompletePool,
                            modifier = Modifier.fillMaxWidth(),
                            shape = smoothCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.Inventory2, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (entry.containerId == null) "查看共用掉落池" else "查看完整掉落池")
                        }
                    } else if (entry.containerId == null) {
                        Text("暂无对应的掉落池", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    if (entry.dropImages.isNotEmpty() || summaryFields.isNotEmpty()) {
                        Text("掉落概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            items(entry.dropImages.size, key = { "source-image-$it" }, contentType = { "pool-item" }) { index ->
                val image = entry.dropImages[index]
                // The image is explicitly attached to this named item, not to the container.
                // An unqualified image must not create a name-only reverse-lookup identity.
                ExtractionDetailItemRow(
                    name = image.name,
                    qualityLabel = image.qualityLabel.orEmpty(),
                    qualityCode = image.qualityLabel?.let { Regex("^Q(\\d+)\\b").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: -1,
                    imageUrl = image.imageUrl,
                    onClick = null
                )
            }
            items(summaryFields.size, key = { "source-summary-field-$it" }, contentType = { "field" }) { index ->
                val field = summaryFields[index]
                if (field.value.isNotBlank()) {
                    Surface(
                        shape = smoothCornerShape(12.dp),
                        color = colors.surfaceContainer,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
                    ) {
                        DetailField(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            label = field.label,
                            value = field.value
                        )
                    }
                }
            }
        }
        if (entry.dropPoolGroups.isNotEmpty()) {
            item(key = "pool-search", contentType = "pool-search") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("候选物品", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    SearchBar(
                        keyword = poolQuery,
                        onKeywordChange = { poolQuery = it },
                        onClear = { poolQuery = "" },
                        isSearching = false,
                        placeholder = "搜索候选物品"
                    )
                    ExtractionPoolQualityFilter(
                        labels = entry.dropPoolGroups.map { it.label }.distinct(),
                        selectedQuality = effectiveQuality,
                        onSelect = { selectedQuality = it },
                        summary = if (isFiltered) "$matchingItems / $totalItems 项" else "$totalItems 项"
                    )
                    if (isFiltered) {
                        val focus = LocalFocusManager.current
                        TextButton(onClick = { focus.clearFocus(); showAllItems() }) { Text("查看全部候选物品") }
                    }
                    Text(
                        if (sharedSourceList) "共用名单的顺序不代表此来源的掉率高低。" else "候选名单不代表物品掉率。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            if (matchingItems == 0 && isFiltered) item(key = "pool-empty", contentType = "pool-empty") {
                ExtractionEmptyResults(
                    message = "没有符合条件的候选物品",
                    onClearQuery = if (poolQuery.isNotEmpty()) ({ poolQuery = "" }) else null,
                    onClearFilters = if (effectiveQuality != null) ({ selectedQuality = null }) else null
                )
            }
            poolGroups.forEach { group ->
                extractionDropPoolSection(
                    group,
                    expanded = group.key in expandedGroups,
                    artwork = artwork,
                    onItemClick = onFindItem?.let { find ->
                        { name -> find(ExtractionDropItem(name, group.label, group.qualityCode)) }
                    },
                    onToggle = {
                        expandedGroups = if (group.key in expandedGroups) expandedGroups - group.key else expandedGroups + group.key
                    }
                )
            }
        }
        if (!referenceIsMainContent) {
            val hasReference = otherFields.isNotEmpty() || readingDescription.isNotBlank() || containerDetails || entry.containerId != null
            if (hasReference) item(key = "reference-toggle", contentType = "reference-toggle") {
                Surface(
                    onClick = { referenceExpanded = !referenceExpanded },
                    modifier = Modifier.dropItemMotion(this).fillMaxWidth(),
                    shape = smoothCornerShape(12.dp),
                    color = colors.surfaceContainer,
                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "掉落规则与说明",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            Icons.Outlined.ExpandMore,
                            modifier = Modifier.rotate(referenceRotation),
                            contentDescription = if (referenceExpanded) "收起" else "展开",
                            tint = colors.primary
                        )
                    }
                }
            }
            if (referenceExpanded) {
                if (entry.containerId != null) item(key = "container-id", contentType = "field") {
                    Surface(
                        Modifier.dropItemMotion(this).fillMaxWidth(),
                        shape = smoothCornerShape(12.dp),
                        color = colors.surfaceContainer,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("容器编号", style = MaterialTheme.typography.labelLarge, color = colors.primary, fontWeight = FontWeight.SemiBold)
                            Text(entry.containerId, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                        }
                    }
                }
                if (containerDetails) item(key = "pool-rules", contentType = "description") {
                    Surface(
                        Modifier.dropItemMotion(this).fillMaxWidth(),
                        shape = smoothCornerShape(12.dp),
                        color = colors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
                    ) {
                        Text(
                            "先确定件数，再按权重逐件独立抽取。单件抽中率不等于整箱至少获得一件该物品的概率。",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                if (readingDescription.isNotBlank()) item(key = "description", contentType = "description") {
                    Surface(
                        Modifier.dropItemMotion(this).fillMaxWidth(),
                        shape = smoothCornerShape(12.dp),
                        color = colors.surfaceContainer,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
                    ) {
                        Text(readingDescription, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(otherFields.size, key = { "field-$it" }, contentType = { "field" }) { index ->
                    val field = otherFields[index]
                    if (field.value.isNotBlank()) {
                        Surface(
                            Modifier.dropItemMotion(this).fillMaxWidth(),
                            shape = smoothCornerShape(12.dp),
                            color = colors.surfaceContainer,
                            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
                        ) {
                            DetailField(
                                Modifier.fillMaxWidth().padding(14.dp),
                                label = field.label,
                                value = field.value
                            )
                        }
                    }
                }
            }
        } else {
            if (readingDescription.isNotBlank()) item(key = "description", contentType = "description") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = smoothCornerShape(12.dp),
                    color = colors.surfaceContainer,
                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
                ) {
                    Text(readingDescription, Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(fields.size, key = { "field-$it" }, contentType = { "field" }) { index ->
                val field = fields[index]
                if (field.value.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = smoothCornerShape(12.dp),
                        color = colors.surfaceContainer,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
                    ) {
                        DetailField(
                            Modifier.fillMaxWidth().padding(14.dp),
                            label = field.label,
                            value = field.value
                        )
                    }
                }
            }
        }
        item(key = "close", contentType = "close") {
            Row(Modifier.dropItemMotion(this).fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilledTonalButton(onClick = onDismiss, shape = smoothCornerShape(10.dp)) { Text(dismissLabel) }
            }
        }
    }
}

@Composable
private fun ExtractionPoolQualityFilter(labels: List<String>, selectedQuality: String?, onSelect: (String?) -> Unit, summary: String) {
    var expanded by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DropFilterBar(
            summary = summary,
            label = selectedQuality ?: "筛选品质",
            activeFilter = selectedQuality != null,
            activeCount = if (selectedQuality != null) 1 else 0,
            onClick = { focus.clearFocus(); expanded = !expanded }
        )
        if (expanded) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            (listOf(null) + labels).forEach { label ->
                val isSelected = selectedQuality == label
                FilterChip(
                    selected = isSelected,
                    shape = smoothCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) colors.primary.copy(alpha = 0.55f) else colors.outlineVariant.copy(alpha = 0.55f)
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primaryContainer.copy(alpha = 0.65f),
                        selectedLabelColor = colors.onPrimaryContainer,
                        containerColor = colors.surfaceContainerLow,
                        labelColor = colors.onSurface
                    ),
                    label = { Text(label ?: "全部品质") },
                    onClick = { onSelect(label); expanded = false }
                )
            }
        }
    }
}

private val sourceSummaryLabels = setOf(
    "必掉物", "掉落材料（按权重抽）", "掉落材料（按概率抽）", "掉落物品",
    "限定池", "掉落池构成", "掉落池内最高权重", "抽中率最高", "掉落件数"
)
