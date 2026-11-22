package com.nekolaska.calabiyau.feature.wiki.extraction.workshop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.ShimmerGroup
import com.nekolaska.calabiyau.core.ui.SkeletonTextLine

@Composable
internal fun WorkshopSkeleton(tab: WorkshopTab, modifier: Modifier = Modifier) = ShimmerGroup {
    Column(modifier.fillMaxSize().clearAndSetSemantics { contentDescription = "正在加载${tab.title}资料" }) {
        when (tab) {
            WorkshopTab.RECIPES -> ShimmerBox(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth().height(56.dp), AppShapes.capsule)
            WorkshopTab.STAFF, WorkshopTab.DECOR -> FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val widths = if (tab == WorkshopTab.STAFF) listOf(84.dp, 84.dp) else listOf(60.dp, 60.dp, 94.dp)
                widths.forEach { width ->
                    Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                        ShimmerBox(Modifier.size(width, 32.dp), AppShapes.chip)
                    }
                }
            }
            WorkshopTab.ROOMS -> Unit
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
            top = if (tab == WorkshopTab.RECIPES) 0.dp else 8.dp, bottom = 20.dp)) {
            item {
                if (tab == WorkshopTab.ROOMS || tab == WorkshopTab.RECIPES) {
                    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        ShimmerBox(Modifier.size(64.dp, 14.dp)); ShimmerBox(Modifier.size(56.dp, 14.dp))
                    }
                } else Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonTextLine(widthFraction = if (tab == WorkshopTab.STAFF) 0.94f else 0.62f)
                    if (tab == WorkshopTab.STAFF) SkeletonTextLine(widthFraction = 0.68f)
                }
            }
            items(6) { index ->
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (tab == WorkshopTab.RECIPES) ShimmerBox(recipeArtworkSize, AppShapes.chip)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonTextLine(widthFraction = 0.37f + (index % 3) * 0.11f, height = 18.dp)
                        if (tab != WorkshopTab.DECOR) {
                            SkeletonTextLine(widthFraction = if (tab == WorkshopTab.ROOMS) 0.22f else 0.60f)
                            SkeletonTextLine(widthFraction = if (tab == WorkshopTab.RECIPES) 0.35f else 0.82f)
                        }
                        if (tab == WorkshopTab.STAFF && index % 2 == 0) SkeletonTextLine(widthFraction = 0.58f)
                    }
                    if (tab == WorkshopTab.ROOMS || tab == WorkshopTab.RECIPES) Spacer(Modifier.width(24.dp))
                }
                HorizontalDivider()
            }
        }
    }
}
