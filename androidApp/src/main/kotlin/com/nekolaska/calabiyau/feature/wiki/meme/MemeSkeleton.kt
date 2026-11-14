package com.nekolaska.calabiyau.feature.wiki.meme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*

@Composable
internal fun MemeSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().semantics { contentDescription = "正在加载梗百科" },
        contentPadding = PaddingValues(horizontal = AppSpacing.screen, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
    ) {
        item { ShimmerBox(Modifier.fillMaxWidth().height(AppSizes.searchMinHeight), AppShapes.sheet) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = AppSpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)
            ) {
                repeat(3) { ShimmerBox(Modifier.weight(1f).height(32.dp), AppShapes.chip) }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xSmall, vertical = AppSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)
            ) {
                ShimmerBox(Modifier.size(36.dp), AppShapes.sectionIcon)
                SkeletonTextLine(Modifier.weight(1f), widthFraction = 0.6f, height = 24.dp)
                ShimmerBox(Modifier.fillMaxWidth(0.18f).height(24.dp), AppShapes.chip)
            }
        }
        // Official illustrated issues precede editor-written definitions in the default filter.
        items(3) {
            Card(
                shape = smoothCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
                    ShimmerBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), RectangleShape)
                    SkeletonTextLine(
                        modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = AppSpacing.xLarge),
                        widthFraction = 0.65f,
                        height = 24.dp
                    )
                }
            }
        }
    }
}
