package com.nekolaska.calabiyau.feature.wiki.navigation

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
internal fun NavigationMenuSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().semantics { contentDescription = "正在加载 Wiki 导航" },
        contentPadding = PaddingValues(horizontal = AppSpacing.screen, vertical = AppSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
    ) {
        // All sections start collapsed, including the embedded navigation page.
        items(6) {
            Card(
                shape = AppShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.cardContent, vertical = AppSpacing.xLarge),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ShimmerBox(Modifier.size(40.dp), AppShapes.sectionIcon)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                        SkeletonTextLine(widthFraction = 0.55f, height = 20.dp)
                        SkeletonTextLine(widthFraction = 0.3f, height = 16.dp)
                    }
                    ShimmerBox(Modifier.size(24.dp), AppShapes.chip)
                }
            }
        }
    }
}
