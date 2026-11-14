package com.nekolaska.calabiyau.feature.wiki.collaboration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.*

@Composable
internal fun CollaborationSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().semantics { contentDescription = "正在加载联动数据" },
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
        // The default "all" view starts with year cards before illustrated event details.
        items(3) {
            Card(
                shape = smoothCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.fillMaxWidth().padding(AppSpacing.cardContent), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
                        ShimmerBox(Modifier.size(36.dp), AppShapes.sectionIcon)
                        SkeletonTextLine(Modifier.weight(1f), widthFraction = 0.45f, height = 20.dp)
                        ShimmerBox(Modifier.fillMaxWidth(0.18f).height(24.dp), AppShapes.chip)
                    }
                    repeat(3) { index ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ShimmerBox(Modifier.fillMaxWidth(0.25f).height(30.dp), AppShapes.sectionIcon)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                                SkeletonTextLine(widthFraction = 0.95f, height = 20.dp)
                                SkeletonTextLine(widthFraction = 0.65f, height = 20.dp)
                            }
                        }
                        if (index < 2) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    }
                }
            }
        }
    }
}
