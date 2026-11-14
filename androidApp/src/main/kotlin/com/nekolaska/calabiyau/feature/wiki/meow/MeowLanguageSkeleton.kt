package com.nekolaska.calabiyau.feature.wiki.meow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*

@Composable
internal fun MeowLanguageSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().semantics { contentDescription = "正在加载喵言喵语" },
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
                    ShimmerBox(Modifier.fillMaxWidth().height(60.dp), smoothCornerShape(20.dp))
                }
            }
        }
        item {
            Card(
                shape = smoothCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xLarge)
                ) {
                    ShimmerBox(Modifier.size(48.dp), AppShapes.compactCard)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                        SkeletonTextLine(widthFraction = 0.65f, height = 24.dp)
                        SkeletonTextLine(widthFraction = 0.4f, height = 20.dp)
                    }
                }
            }
        }
        item {
            Card(
                shape = AppShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.fillMaxWidth().padding(AppSpacing.cardContent), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonTextLine(widthFraction = 0.95f, height = 20.dp)
                    SkeletonTextLine(widthFraction = 0.7f, height = 20.dp)
                }
            }
        }
        items(2) {
            Card(
                shape = AppShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
                        ShimmerBox(Modifier.size(32.dp), AppShapes.chip)
                        SkeletonTextLine(Modifier.weight(1f), widthFraction = 0.6f, height = 20.dp)
                    }
                    // The group body is copyable prose, without audio controls.
                    Surface(shape = AppShapes.compactCard, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(3) { line -> SkeletonTextLine(widthFraction = if (line == 2) 0.65f else 0.95f, height = 20.dp) }
                        }
                    }
                }
            }
        }
    }
}
