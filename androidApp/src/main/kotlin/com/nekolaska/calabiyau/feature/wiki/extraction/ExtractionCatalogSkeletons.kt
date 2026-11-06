package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.nekolaska.calabiyau.core.ui.SkeletonTextLine
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionCatalogKind

/** Mirror the catalog header and cards; category/rarity filters remain in their sheet. */
@Composable
internal fun ExtractionCatalogSkeleton(kind: ExtractionCatalogKind, modifier: Modifier = Modifier) {
    val loading = modifier.clearAndSetSemantics {
        contentDescription = "正在加载${kind.title}"
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }
    when (kind) {
        ExtractionCatalogKind.LOOT -> ExtractionLootSkeleton(loading)
        ExtractionCatalogKind.ATTACHMENTS -> ExtractionAttachmentsSkeleton(loading)
        ExtractionCatalogKind.DROPS -> ExtractionDropsSkeleton(loading)
    }
}

@Composable
private fun ExtractionLootSkeleton(modifier: Modifier) {
    // Loot uses square art + rarity + a two-line name, not generic list placeholders.
    ExtractionItemGridSkeleton(modifier)
}

@Composable
private fun ExtractionAttachmentsSkeleton(modifier: Modifier) {
    // Attachment detail is loaded from the same cards, so its initial grid has the same
    // physical frame as loot. Neither screen displays compatibility/effects before a tap.
    ExtractionItemGridSkeleton(modifier)
}

@Composable
private fun ExtractionItemGridSkeleton(modifier: Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        item(key = "controls", span = { GridItemSpan(maxLineSpan) }) { ExtractionControlsSkeleton(drops = false) }
        items(12, key = { "extraction-grid-skeleton-$it" }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    ShimmerBox(Modifier.fillMaxSize().padding(8.dp), shape = smoothCornerShape(14.dp))
                    ShimmerBox(Modifier.align(Alignment.TopEnd).padding(4.dp).width(36.dp).height(18.dp))
                }
                Column(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.Center) {
                    SkeletonTextLine(widthFraction = 0.8f, height = 12.dp)
                    Spacer(Modifier.height(5.dp))
                    SkeletonTextLine(widthFraction = 0.55f, height = 12.dp)
                }
            }
        }
    }
}

@Composable
private fun ExtractionDropsSkeleton(modifier: Modifier) {
    // Drop sources have no hero art or item rarity: mirror the text-card list instead.
    val titleHeight = skeletonTextHeight(MaterialTheme.typography.titleMedium)
    val metadataHeight = skeletonTextHeight(MaterialTheme.typography.bodySmall)
    val previewHeight = skeletonTextHeight(MaterialTheme.typography.bodyMedium)
    val actionHeight = skeletonTextHeight(MaterialTheme.typography.labelMedium)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false
    ) {
        item(key = "controls") { ExtractionControlsSkeleton(drops = true) }
        items(6, key = { "extraction-drop-skeleton-$it" }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonTextLine(widthFraction = 0.36f, height = titleHeight)
                    SkeletonTextLine(widthFraction = 0.6f, height = metadataHeight)
                    SkeletonTextLine(widthFraction = 0.55f, height = metadataHeight)
                    Column {
                        SkeletonTextLine(widthFraction = 0.92f, height = previewHeight)
                        SkeletonTextLine(widthFraction = 0.72f, height = previewHeight)
                    }
                    SkeletonTextLine(widthFraction = 0.25f, height = actionHeight)
                }
            }
        }
    }
}

@Composable
private fun ExtractionControlsSkeleton(drops: Boolean) {
    val labelHeight = skeletonTextHeight(MaterialTheme.typography.labelMedium)
    val summaryHeight = skeletonTextHeight(MaterialTheme.typography.bodySmall)
    val buttonHeight = maxOf(40.dp, skeletonTextHeight(MaterialTheme.typography.labelLarge) + 16.dp)
    val fontScale = LocalDensity.current.fontScale
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonTextLine(widthFraction = 0.36f, height = labelHeight)
        // Drop navigation stays interactive above this skeleton, including on errors.
        if (drops) SkeletonTextLine(widthFraction = 0.55f, height = skeletonTextHeight(MaterialTheme.typography.titleLarge))
        ShimmerBox(
            Modifier.fillMaxWidth().height(maxOf(AppSizes.searchMinHeight, skeletonTextHeight(MaterialTheme.typography.bodyLarge) + 32.dp)),
            shape = smoothCornerShape(AppShapes.sheetRadius)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(130.dp * fontScale).height(maxOf(48.dp, buttonHeight)), contentAlignment = Alignment.Center) {
                ShimmerBox(Modifier.fillMaxWidth().height(buttonHeight), shape = smoothCornerShape(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            ShimmerBox(Modifier.weight(1f).height(labelHeight))
        }
        SkeletonTextLine(widthFraction = 0.52f, height = summaryHeight)
    }
}

@Composable
private fun skeletonTextHeight(style: TextStyle): Dp = with(LocalDensity.current) { style.lineHeight.toDp() }
