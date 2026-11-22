package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.WikiSheetFrame
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtractionFilterSheet(
    kind: ExtractionCatalogKind, categories: List<String>, qualities: List<Pair<Int, String>>, sorts: List<ExtractionSort>,
    category: String?, quality: Int?, valuable: Boolean, sort: ExtractionSort, descending: Boolean,
    onCategory: (String?) -> Unit, onQuality: (Int?) -> Unit, onValuable: (Boolean) -> Unit,
    onSort: (ExtractionSort) -> Unit, onDescending: (Boolean) -> Unit, onClear: (() -> Unit)?, onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    WikiSheetFrame(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp)) {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item("title") {
                    Text(
                        "筛选与排序",
                        Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                item("category") {
                    val label = if (kind == ExtractionCatalogKind.ATTACHMENTS) "槽位" else "分类"
                    FilterGroup(label) {
                        FilterOption("全部$label", category == null) { onCategory(null) }
                        categories.forEach { value ->
                            FilterOption(value, value == category) { onCategory(if (value == category) null else value) }
                        }
                    }
                }
                if (qualities.isNotEmpty()) item("quality") {
                    FilterGroup("品质") {
                        FilterOption("全部品质", quality == null) { onQuality(null) }
                        qualities.forEach { (code, label) ->
                            FilterOption(
                                label = label,
                                selected = code == quality,
                                dotColor = extractionDropQualityColor(code)
                            ) { onQuality(if (code == quality) null else code) }
                        }
                    }
                }
                if (kind == ExtractionCatalogKind.LOOT) item("valuable") {
                    FilterGroup("特殊标记") {
                        FilterOption("仅贵重物品", valuable) { onValuable(!valuable) }
                    }
                }
                if (sorts.size > 1) item("sort") {
                    FilterGroup("排序") {
                        sorts.forEach { FilterOption(it.label, it == sort) { onSort(it) } }
                    }
                }
                if (sort != ExtractionSort.DEFAULT) item("direction") {
                    FilterGroup("排序方向") {
                        FilterOption("降序", descending) { onDescending(true) }
                        FilterOption("升序", !descending) { onDescending(false) }
                    }
                }
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.50f))
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { onClear?.invoke() },
                    enabled = onClear != null,
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) { Text("重置筛选与排序") }
                FilledTonalButton(
                    onClick = onDismiss,
                    shape = smoothCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) { Text("完成") }
            }
        }
    }
}

@Composable
private fun FilterGroup(label: String, options: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(12.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                label,
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) { options() }
        }
    }
}

@Composable
private fun FilterOption(label: String, selected: Boolean, dotColor: Color? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = smoothCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (selected) colors.primary.copy(alpha = 0.55f) else colors.outlineVariant.copy(alpha = 0.55f)
        ),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.primaryContainer.copy(alpha = 0.65f),
            selectedLabelColor = colors.onPrimaryContainer,
            containerColor = colors.surfaceContainerLow,
            labelColor = colors.onSurface
        ),
        leadingIcon = dotColor?.let { color ->
            { Box(Modifier.size(6.dp).background(color, smoothCornerShape(2.dp))) }
        },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) }
    )
}
