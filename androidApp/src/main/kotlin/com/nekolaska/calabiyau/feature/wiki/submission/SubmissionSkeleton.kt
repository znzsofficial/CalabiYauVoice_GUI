package com.nekolaska.calabiyau.feature.wiki.submission

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
internal fun SubmissionSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().semantics { contentDescription = "正在加载投稿作品" },
        contentPadding = PaddingValues(horizontal = AppSpacing.screen, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
    ) {
        item {
            Card(
                shape = AppShapes.sheet,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.fillMaxWidth().padding(AppSpacing.cardContent), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
                        ShimmerBox(Modifier.size(28.dp), AppShapes.chip)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                            SkeletonTextLine(widthFraction = 0.8f, height = 24.dp)
                            SkeletonTextLine(widthFraction = 0.95f, height = 20.dp)
                            SkeletonTextLine(widthFraction = 0.6f, height = 20.dp)
                        }
                    }
                    ShimmerBox(Modifier.fillMaxWidth(0.6f).height(40.dp), AppShapes.capsule)
                }
            }
        }
        item { ShimmerBox(Modifier.fillMaxWidth().height(AppSizes.searchMinHeight), AppShapes.sheet) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = AppSpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)
            ) {
                repeat(3) { ShimmerBox(Modifier.weight(1f).height(32.dp), AppShapes.chip) }
            }
        }
        // Entries are text cards with metadata, not a cover-art grid.
        items(3) {
            Card(
                shape = AppShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
                        ShimmerBox(Modifier.size(42.dp), AppShapes.compactCard)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                            SkeletonTextLine(widthFraction = 0.9f, height = 20.dp)
                            SkeletonTextLine(widthFraction = 0.45f, height = 16.dp)
                        }
                        ShimmerBox(Modifier.size(24.dp), AppShapes.chip)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemGap), verticalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)) {
                        repeat(3) { ShimmerBox(Modifier.fillMaxWidth(0.28f).height(28.dp), AppShapes.capsule) }
                    }
                }
            }
        }
    }
}
