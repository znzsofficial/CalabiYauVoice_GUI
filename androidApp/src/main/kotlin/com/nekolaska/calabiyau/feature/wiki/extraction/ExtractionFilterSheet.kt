package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.WikiSheetFrame
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtractionFilterSheet(
    kind: ExtractionCatalogKind, categories: List<String>, qualities: List<Pair<Int, String>>, sorts: List<ExtractionSort>,
    category: String?, quality: Int?, valuable: Boolean, sort: ExtractionSort, descending: Boolean,
    onCategory: (String?) -> Unit, onQuality: (Int?) -> Unit, onValuable: (Boolean) -> Unit,
    onSort: (ExtractionSort) -> Unit, onDescending: (Boolean) -> Unit, onClear: (() -> Unit)?, onDismiss: () -> Unit
) {
    WikiSheetFrame(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp)) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item("title") { Text("筛选与排序", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                item("category") {
                    val label = if (kind == ExtractionCatalogKind.ATTACHMENTS) "槽位" else "分类"
                    FilterGroup(label) {
                        FilterOption("全部$label", category == null) { onCategory(null) }
                        categories.forEach { value -> FilterOption(value, value == category) { onCategory(if (value == category) null else value) } }
                    }
                }
                if (qualities.isNotEmpty()) item("quality") {
                    FilterGroup("品质") {
                        FilterOption("全部品质", quality == null) { onQuality(null) }
                        qualities.forEach { (code, label) -> FilterOption(label, code == quality) { onQuality(if (code == quality) null else code) } }
                    }
                }
                if (kind == ExtractionCatalogKind.LOOT) item("valuable") {
                    FilterOption("仅贵重物品", valuable) { onValuable(!valuable) }
                }
                if (sorts.size > 1) item("sort") {
                    FilterGroup("排序") { sorts.forEach { FilterOption(it.label, it == sort) { onSort(it) } } }
                }
                if (sort != ExtractionSort.DEFAULT) item("direction") {
                    FilterGroup("排序方向") {
                        FilterOption("降序", descending) { onDescending(true) }
                        FilterOption("升序", !descending) { onDescending(false) }
                    }
                }
            }
            HorizontalDivider()
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onClear?.invoke() }, enabled = onClear != null,
                    modifier = Modifier.align(Alignment.CenterVertically)) { Text("重置筛选与排序") }
                FilledTonalButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterVertically)) { Text("完成") }
            }
        }
    }
}

@Composable
private fun FilterGroup(label: String, options: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { options() }
    }
}

@Composable
private fun FilterOption(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
