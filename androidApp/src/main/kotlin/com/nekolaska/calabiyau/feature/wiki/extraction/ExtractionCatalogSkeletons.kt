package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSizes
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.ShimmerGroup
import com.nekolaska.calabiyau.core.ui.SkeletonTextLine
import com.nekolaska.calabiyau.core.ui.TacticalStyle
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionCatalogKind

/** Mirror the catalog header and cards; category/rarity filters remain in their sheet. */
@Composable
internal fun ExtractionCatalogSkeleton(kind: ExtractionCatalogKind, modifier: Modifier = Modifier) = ShimmerGroup {
    val loading = modifier.clearAndSetSemantics {
        contentDescription = "正在加载${kind.title}"
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }
    LazyVerticalGrid(
        columns = extractionItemGridCells(),
        modifier = loading.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        item(key = "controls", span = { GridItemSpan(maxLineSpan) }) { ExtractionControlsSkeleton() }
        items(12, key = { "extraction-grid-skeleton-$it" }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = TacticalStyle.compactCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = TacticalStyle.cardBorder()
            ) {
                ExtractionItemImageHeader(
                    accent = MaterialTheme.colorScheme.outlineVariant,
                    artwork = { ShimmerBox(Modifier.fillMaxSize().padding(10.dp), shape = smoothCornerShape(12.dp)) }
                ) {
                    ShimmerBox(Modifier.width(36.dp * LocalDensity.current.fontScale)
                        .height(skeletonTextHeight(MaterialTheme.typography.labelSmall) + 6.dp), shape = smoothCornerShape(6.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonTextLine(widthFraction = 0.8f, height = skeletonTextHeight(MaterialTheme.typography.titleSmall))
                    SkeletonTextLine(widthFraction = 0.55f, height = skeletonTextHeight(MaterialTheme.typography.labelSmall) + 4.dp)
                }
            }
        }
    }
}

@Composable
private fun ExtractionControlsSkeleton() {
    val buttonHeight = skeletonTextHeight(MaterialTheme.typography.labelLarge) + 16.dp
    val fontScale = LocalDensity.current.fontScale
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShimmerBox(
            Modifier.fillMaxWidth().height(maxOf(AppSizes.searchMinHeight, skeletonTextHeight(MaterialTheme.typography.bodyLarge) + 32.dp)),
            shape = smoothCornerShape(AppShapes.sheetRadius)
        )
        FlowRow(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            ShimmerBox(Modifier.align(Alignment.CenterVertically).padding(end = 8.dp).width(40.dp * fontScale).height(skeletonTextHeight(MaterialTheme.typography.bodySmall)))
            ShimmerBox(Modifier.align(Alignment.CenterVertically).width(46.dp + 66.dp * fontScale).height(buttonHeight), shape = AppShapes.chip)
        }
    }
}

@Composable
private fun skeletonTextHeight(style: TextStyle): Dp = with(LocalDensity.current) { style.lineHeight.toDp() }
