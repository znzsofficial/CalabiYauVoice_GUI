package com.nekolaska.calabiyau.feature.wiki.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*

@Composable
internal fun GameHistorySkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().semantics { contentDescription = "正在加载游戏历史" },
        contentPadding = PaddingValues(horizontal = AppSpacing.screen, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sectionGap)
    ) {
        // History is a section directory, not a vertical event timeline.
        items(3) { section ->
            Card(
                shape = smoothCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.xLarge)) {
                        ShimmerBox(Modifier.size(44.dp), AppShapes.compactCard)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                            SkeletonTextLine(widthFraction = 0.65f, height = 24.dp)
                            SkeletonTextLine(widthFraction = 0.9f, height = 20.dp)
                        }
                    }
                    Spacer(Modifier.height(AppSpacing.cardContent))
                    if (section == 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
                            repeat(2) { ShimmerBox(Modifier.weight(1f).aspectRatio(1.5f), smoothCornerShape(20.dp)) }
                        }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            repeat(4) {
                                ShimmerBox(Modifier.fillMaxWidth(0.42f).height(40.dp), AppShapes.compactCard)
                            }
                        }
                    }
                }
            }
        }
    }
}
