package com.nekolaska.calabiyau.feature.wiki.bio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.TacticalBadge
import com.nekolaska.calabiyau.core.ui.TacticalSectionHeader
import com.nekolaska.calabiyau.core.ui.TacticalSpecTile
import com.nekolaska.calabiyau.core.ui.tacticalTabular

@Composable
internal fun BioColorBadge(text: String, color: Color) {
    TacticalBadge(
        text = text,
        dotColor = color,
        containerColor = color.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = color.copy(alpha = 0.32f)
    )
}

@Composable
internal fun BioDeckQualitySummary(counts: Map<String, Int>, minimums: Map<String, Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TacticalSectionHeader("卡组品质")
        Text(
            "含默认卡 · 每种品质最多 $MAX_PER_RARITY 张",
            style = MaterialTheme.typography.bodySmall.tacticalTabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = (maxWidth / (88.dp * LocalDensity.current.fontScale.coerceAtLeast(1f))).toInt().coerceIn(1, 3)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in listOf("完美", "卓越", "精致").chunked(columns)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (quality in row) {
                            val count = counts[quality] ?: 0
                            val minimum = minimums[quality] ?: 0
                            val status = when {
                                count < minimum -> "还差 ${minimum - count} 张"
                                count > MAX_PER_RARITY -> "超出 ${count - MAX_PER_RARITY} 张"
                                else -> "已达要求"
                            }
                            TacticalSpecTile(
                                label = quality,
                                value = "已选 $count 张",
                                modifier = Modifier.weight(1f),
                                supportingText = "至少 $minimum 张",
                                deltaText = status,
                                deltaColor = if (count in minimum..MAX_PER_RARITY) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.error
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
