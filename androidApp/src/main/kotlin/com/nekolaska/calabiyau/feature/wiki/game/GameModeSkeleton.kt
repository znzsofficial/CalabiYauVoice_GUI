package com.nekolaska.calabiyau.feature.wiki.game

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

/** Modes initially show only their inset, collapsed header. */
@Composable
internal fun GameModeSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().semantics { contentDescription = "正在加载模式信息" },
        contentPadding = PaddingValues(horizontal = AppSpacing.screen, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
    ) {
        items(5) {
            Card(
                shape = AppShapes.sheet,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Surface(
                    modifier = Modifier.padding(AppSpacing.cardContent),
                    shape = smoothCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = AppSpacing.large),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)
                    ) {
                        ShimmerBox(Modifier.size(40.dp), AppShapes.compactCard)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                            SkeletonTextLine(widthFraction = 0.55f, height = 20.dp)
                            SkeletonTextLine(widthFraction = 0.9f, height = 16.dp)
                        }
                        ShimmerBox(Modifier.size(32.dp), AppShapes.sectionIcon)
                    }
                }
            }
        }
    }
}
