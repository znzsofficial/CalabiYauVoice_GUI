package com.nekolaska.calabiyau.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.feature.wiki.hub.QuickEntry
import com.nekolaska.calabiyau.feature.wiki.hub.allQuickEntries
import com.nekolaska.calabiyau.feature.wiki.hub.defaultQuickEntryIds
import com.nekolaska.calabiyau.feature.wiki.hub.quickEntryById

@Composable
internal fun QuickEntryCustomizeSheet(
    selectedIds: List<String>,
    onSelectedIdsChange: (List<String>) -> Unit,
    onClose: () -> Unit
) {
    val selectedEntries = remember(selectedIds) { selectedIds.mapNotNull(quickEntryById::get) }
    val remainingEntries = remember(selectedIds) {
        allQuickEntries.filterNot { candidate -> selectedIds.contains(candidate.id) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.cardContent)
            .padding(bottom = 24.dp)
    ) {
        Text(
            "顶部六按钮自定义",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(AppSpacing.xSmall))
        Text(
            "已选中的按钮会显示在 Wiki 首页顶部。可移除、补充，并调整顺序。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(AppSpacing.sectionGap))
        Text(
            "当前顺序",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))

        selectedEntries.forEachIndexed { index, entry ->
            QuickEntryEditorRow(
                entry = entry,
                index = index,
                canMoveUp = index > 0,
                canMoveDown = index < selectedEntries.lastIndex,
                onMoveUp = { onSelectedIdsChange(selectedIds.moveQuickEntry(index, index - 1)) },
                onMoveDown = { onSelectedIdsChange(selectedIds.moveQuickEntry(index, index + 1)) },
                onRemove = {
                    if (selectedIds.size <= 1) return@QuickEntryEditorRow
                    onSelectedIdsChange(selectedIds.filterNot { it == entry.id })
                }
            )
            if (index < selectedEntries.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = AppSpacing.medium),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.sectionGap))
        Text(
            "可添加项",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)) {
            items(remainingEntries, key = { it.id }) { entry ->
                AssistChip(
                    onClick = {
                        if (selectedIds.size >= 6) return@AssistChip
                        onSelectedIdsChange(selectedIds + entry.id)
                    },
                    label = { Text(entry.label) },
                    leadingIcon = {
                        Icon(entry.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.sectionGap))
        TextButton(onClick = { onSelectedIdsChange(defaultQuickEntryIds) }) {
            Text("恢复默认六按钮")
        }

        Spacer(Modifier.height(AppSpacing.xSmall))
        TextButton(onClick = onClose) {
            Text("完成")
        }
    }
}

private fun List<String>.moveQuickEntry(fromIndex: Int, toIndex: Int): List<String> {
    val mutable = toMutableList()
    mutable[fromIndex] = mutable[toIndex].also { mutable[toIndex] = mutable[fromIndex] }
    return mutable
}

@Composable
private fun QuickEntryEditorRow(
    entry: QuickEntry,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(entry.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(AppSpacing.iconGap))
        Column(Modifier.weight(1f)) {
            Text(entry.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "位置 ${index + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Default.ExpandLess, contentDescription = "上移")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Default.ExpandMore, contentDescription = "下移")
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Close, contentDescription = "移除")
        }
    }
}
