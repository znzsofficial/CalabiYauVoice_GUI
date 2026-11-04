package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.SimpleDropdownSelector
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtractionFilterSheet(
    kind: ExtractionCatalogKind, categories: List<String>, qualities: List<Pair<Int, String>>, sorts: List<ExtractionSort>,
    category: String?, quality: Int?, valuable: Boolean, sort: ExtractionSort, descending: Boolean,
    onCategory: (String?) -> Unit, onQuality: (Int?) -> Unit, onValuable: (Boolean) -> Unit,
    onSort: (ExtractionSort) -> Unit, onDescending: (Boolean) -> Unit, onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 620.dp), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("筛选与排序", style = MaterialTheme.typography.headlineSmall) }
            item {
                SimpleDropdownSelector(label = if (kind == ExtractionCatalogKind.ATTACHMENTS) "槽位" else "分类",
                    options = listOf("全部") + categories, selected = category ?: "全部", onSelected = { onCategory(it.takeUnless { it == "全部" }) })
            }
            if (qualities.isNotEmpty()) item {
                SimpleDropdownSelector(label = "稀有度", options = listOf("全部") + qualities.map { it.second },
                    selected = qualities.find { it.first == quality }?.second ?: "全部",
                    onSelected = { label -> onQuality(qualities.find { it.second == label }?.first) })
            }
            if (kind == ExtractionCatalogKind.LOOT) item {
                FilterChip(selected = valuable, onClick = { onValuable(!valuable) }, label = { Text("仅贵重物品") })
            }
            if (sorts.size > 1) item {
                SimpleDropdownSelector(label = "排序", options = sorts.map { it.label }, selected = sort.label,
                    onSelected = { label -> sorts.find { it.label == label }?.let(onSort) })
                if (sort != ExtractionSort.DEFAULT) FilterChip(selected = descending, onClick = { onDescending(!descending) }, label = { Text(if (descending) "降序" else "升序") })
            }
            item { FilledTonalButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") } }
        }
    }
}
