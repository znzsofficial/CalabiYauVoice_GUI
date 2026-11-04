package com.nekolaska.calabiyau.feature.wiki.bio

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*

/** PC and mobile use the same controls, but retain independent saved selections in the screen. */
@Composable
internal fun BioCardFilterBar(
    factions: List<String>, categories: List<String>, selectedFaction: String,
    selectedCategory: String, selectedRarity: Int, onFactionChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit, onRarityChange: (Int) -> Unit
) {
    BioFilterCard {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { SimpleDropdownSelector("阵营", factions, selectedFaction, onFactionChange) }
            Box(Modifier.weight(1f)) { SimpleDropdownSelector("分类", categories, selectedCategory, onCategoryChange) }
        }
        QualityFilterChips(selectedLevel = selectedRarity.takeIf { it != 0 },
            levels = listOf(2 to "精致", 3 to "卓越", 4 to "完美"),
            onSelectedLevelChange = { onRarityChange(it ?: 0) }, colorForLevel = ::bioRarityColor)
    }
}

@Composable
internal fun DeckFilterBar(factions: List<String>, selectedFaction: String, onFactionChange: (String) -> Unit) {
    BioFilterCard { SimpleDropdownSelector("卡组", factions, selectedFaction, onFactionChange) }
}

@Composable
private fun BioFilterCard(content: @Composable () -> Unit) {
    Card(shape = smoothCornerShape(24.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FilterAlt, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text("筛选", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
internal fun bioRarityColor(rarity: Int) = when (rarity) {
    4 -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
    3 -> androidx.compose.ui.graphics.Color(0xFFAB47BC)
    2 -> androidx.compose.ui.graphics.Color(0xFF42A5F5)
    else -> MaterialTheme.colorScheme.primary
}
