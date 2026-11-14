package com.nekolaska.calabiyau.feature.wiki.bio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.QualityFilterChips
import com.nekolaska.calabiyau.core.ui.WikiScrollableSheet

@Composable
internal fun BioFilterStatusRow(
    summary: String,
    hasActiveFilter: Boolean,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
    ) {
        Box(Modifier.weight(1f)) {
            Surface(
                shape = AppShapes.chip,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = summary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (hasActiveFilter) {
            TextButton(onClick = onReset) {
                Text("重置")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BioCardFilterSheet(
    factions: List<String>,
    categories: List<String>,
    selectedFaction: String,
    selectedCategory: String,
    selectedRarity: Int,
    onFactionChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onRarityChange: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val hasFilter = selectedFaction != "全部阵营" || selectedCategory != "全部分类" || selectedRarity != 0
    WikiScrollableSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = AppSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xLarge)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "筛选卡牌",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (hasFilter) {
                    TextButton(onClick = onReset) { Text("重置") }
                }
            }

            if (factions.size > 1) {
                FilterSection(title = "阵营") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
                    ) {
                        factions.forEach { faction ->
                            val selected = faction == selectedFaction
                            FilterChip(
                                selected = selected,
                                onClick = { onFactionChange(faction) },
                                shape = AppShapes.chip,
                                label = { Text(faction) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            if (categories.size > 1) {
                FilterSection(title = "分类") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
                    ) {
                        categories.forEach { category ->
                            val selected = category == selectedCategory
                            FilterChip(
                                selected = selected,
                                onClick = { onCategoryChange(category) },
                                shape = AppShapes.chip,
                                label = { Text(category) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            FilterSection(title = "品质") {
                QualityFilterChips(
                    selectedLevel = selectedRarity.takeIf { it != 0 },
                    levels = listOf(2 to "精致", 3 to "卓越", 4 to "完美"),
                    onSelectedLevelChange = { onRarityChange(it ?: 0) },
                    colorForLevel = ::bioRarityColor
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BioDeckFilterSheet(
    factions: List<String>,
    selectedFaction: String,
    onFactionChange: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val hasFilter = selectedFaction != "全部卡组"
    WikiScrollableSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = AppSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xLarge)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "筛选卡组",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (hasFilter) {
                    TextButton(onClick = onReset) { Text("重置") }
                }
            }
            FilterSection(title = "阵营") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
                ) {
                    factions.forEach { faction ->
                        val selected = faction == selectedFaction
                        FilterChip(
                            selected = selected,
                            onClick = { onFactionChange(faction) },
                            shape = AppShapes.chip,
                            label = { Text(faction) },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

@Composable
internal fun bioRarityColor(rarity: Int): Color = when (rarity) {
    4 -> Color(0xFFF59E0B)
    3 -> Color(0xFFAB47BC)
    2 -> Color(0xFF42A5F5)
    else -> MaterialTheme.colorScheme.primary
}

internal fun bioRarityLabel(rarity: Int): String = when (rarity) {
    2 -> "精致"
    3 -> "卓越"
    4 -> "完美"
    else -> ""
}

@Composable
internal fun bioFactionColor(faction: String): Color = when {
    faction.contains("晶源体") -> Color(0xFFF06A6A)
    faction.contains("超弦体") -> Color(0xFF8B5CF6)
    else -> MaterialTheme.colorScheme.primary
}
