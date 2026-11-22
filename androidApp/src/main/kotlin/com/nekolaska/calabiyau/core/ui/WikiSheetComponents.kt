package com.nekolaska.calabiyau.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One bounded scroll owner for both short and long detail/selection sheets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiSheetFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // Material3's default modalWindowInsets already pads AND consumes safeDrawing
    // (top/bottom, including IME). Child spacing is visual breathing room, not system insets.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = AppShapes.sheet,
        tonalElevation = 0.dp,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
            )
        },
        content = content
    )
}

@Composable
fun WikiContentSheet(onDismiss: () -> Unit, content: LazyListScope.() -> Unit) {
    WikiSheetFrame(onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
            contentPadding = PaddingValues(
                start = 18.dp, end = 18.dp,
                top = AppSpacing.xSmall, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large), content = content
        )
    }
}

/** Short, non-virtualized detail content; the whole header and body scroll together. */
@Composable
fun WikiScrollableSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    WikiSheetFrame(onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(bottom = AppSpacing.cardContent),
            content = content
        )
    }
}

/** The shell owns no business selection, filtering, grouping or apply/cancel policy. */
@Composable
fun WikiSelectionSheet(
    title: String, onDismiss: () -> Unit,
    query: String? = null, onQueryChange: (String) -> Unit = {},
    searchPlaceholder: String = "搜索…", controls: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    WikiContentSheet(onDismiss) {
        item(key = "selection-header", contentType = "selection-header") {
            Column(
                modifier = Modifier.padding(bottom = AppSpacing.xSmall),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (query != null) SearchBar(keyword = query, onKeywordChange = onQueryChange,
                    onClear = { onQueryChange("") }, isSearching = false, placeholder = searchPlaceholder)
                controls()
            }
        }
        content()
    }
}

@Composable
fun SelectionOptionRow(
    modifier: Modifier = Modifier, label: String, selected: Boolean, onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { this.selected = selected },
        shape = smoothCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)
        ) {
            leading?.invoke()
            Text(
                label,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = "已选择",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailField(modifier: Modifier = Modifier, label: String, value: String) {
    if (value.isBlank()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun CatalogEmptyResults(modifier: Modifier = Modifier, message: String = "没有匹配的条目", onClear: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onClear != null) TextButton(onClick = onClear, shape = AppShapes.capsule) { Text("清除搜索与筛选") }
    }
}
