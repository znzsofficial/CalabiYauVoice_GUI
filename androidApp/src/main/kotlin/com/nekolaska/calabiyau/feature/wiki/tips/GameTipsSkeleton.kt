package com.nekolaska.calabiyau.feature.wiki.tips

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
internal fun GameTipsSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().semantics { contentDescription = "正在加载游戏Tips" },
        contentPadding = PaddingValues(horizontal = AppSpacing.screen, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = smoothCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(AppSpacing.sectionGap)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.xLarge)) {
                        ShimmerBox(Modifier.size(48.dp), AppShapes.compactCard)
                        SkeletonTextLine(Modifier.weight(1f), height = 20.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)) {
                            repeat(2) { ShimmerBox(Modifier.size(40.dp), AppShapes.capsule) }
                        }
                    }
                    ShimmerBox(Modifier.fillMaxWidth().height(84.dp), smoothCornerShape(20.dp))
                }
            }
        }
        items(3) {
            Card(
                shape = smoothCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(AppSpacing.sectionGap)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.xLarge)) {
                        ShimmerBox(Modifier.size(48.dp), AppShapes.compactCard)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                            SkeletonTextLine(widthFraction = 0.65f, height = 24.dp)
                            SkeletonTextLine(widthFraction = 0.4f, height = 16.dp)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(4) { line -> SkeletonTextLine(widthFraction = if (line == 3) 0.65f else 0.95f, height = 20.dp) }
                    }
                }
            }
        }
    }
}
