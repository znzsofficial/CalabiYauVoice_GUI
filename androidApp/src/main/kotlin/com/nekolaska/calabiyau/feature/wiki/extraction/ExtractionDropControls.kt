package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.SkeletonTextLine

/** One predictable action row below search, shared by both tasks and pool details. */
@Composable
internal fun DropFilterBar(
    summary: String, label: String, onClick: () -> Unit,
    enabled: Boolean = true, activeFilter: Boolean = false
) {
    FlowRow(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            summary,
            Modifier.align(Alignment.CenterVertically).padding(end = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            onClick = onClick, enabled = enabled, modifier = Modifier.align(Alignment.CenterVertically).semantics {
                role = Role.Button
                this.onClick(label = "查看筛选选项", action = null)
            }, shape = AppShapes.chip,
            color = if (activeFilter) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Tune, null, Modifier.size(16.dp),
                    tint = if (activeFilter) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary)
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium,
                    color = if (activeFilter) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Each action clears only what its label names. In particular, a quality change preserves the search. */
@Composable
internal fun ExtractionEmptyResults(
    message: String, onClearQuery: (() -> Unit)? = null, onClearFilters: (() -> Unit)? = null,
    onShowZero: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (onShowZero != null) TextButton(onClick = onShowZero) { Text("显示零掉落来源") }
            if (onClearQuery != null) TextButton(onClick = onClearQuery) { Text("清除搜索") }
            if (onClearFilters != null) TextButton(onClick = onClearFilters) { Text("清除筛选") }
        }
    }
}

internal object DropMotion {
    const val page = 360
    const val move = 340
    const val reveal = 300
    const val exit = 200
}

/** Finite native Compose animations respect the system animator duration scale, including zero. */
internal fun Modifier.dropItemMotion(scope: LazyItemScope): Modifier = with(scope) {
    this@dropItemMotion.animateItem(
        fadeInSpec = tween(DropMotion.reveal), placementSpec = tween(DropMotion.move, easing = FastOutSlowInEasing), fadeOutSpec = tween(DropMotion.exit)
    )
}

/** Mirrors the artwork, title and metadata line(s) of an item or source result. */
@Composable
internal fun DropLookupRowSkeleton(modifier: Modifier = Modifier, source: Boolean = false) {
    val typography = MaterialTheme.typography
    val titleHeight = with(LocalDensity.current) { typography.titleMedium.lineHeight.toDp() }
    val bodyHeight = with(LocalDensity.current) { typography.bodySmall.lineHeight.toDp() }
    val badgeHeight = with(LocalDensity.current) { typography.labelSmall.lineHeight.toDp() } + 6.dp
    Surface(
        modifier.clearAndSetSemantics {},
        shape = AppShapes.compactCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerBox(
                Modifier.size(if (source) 40.dp else 56.dp),
                shape = if (source) AppShapes.sectionIcon else AppShapes.chip
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SkeletonTextLine(widthFraction = 0.65f, height = titleHeight)
                if (source) {
                    SkeletonTextLine(widthFraction = 0.8f, height = bodyHeight)
                    SkeletonTextLine(widthFraction = 0.65f, height = with(LocalDensity.current) { typography.labelMedium.lineHeight.toDp() } + 6.dp)
                } else ShimmerBox(Modifier.width(128.dp).height(badgeHeight))
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

/** Source heading, metadata and a single candidate preview; the tab and search remain live. */
@Composable
internal fun DropSourceCardSkeleton(modifier: Modifier = Modifier) {
    val typography = MaterialTheme.typography
    val titleHeight = with(LocalDensity.current) { typography.titleMedium.lineHeight.toDp() }
    val bodyHeight = with(LocalDensity.current) { typography.bodySmall.lineHeight.toDp() }
    Surface(
        modifier.clearAndSetSemantics {},
        shape = AppShapes.compactCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerBox(Modifier.size(40.dp), shape = AppShapes.sectionIcon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkeletonTextLine(widthFraction = 0.65f, height = titleHeight)
                    SkeletonTextLine(widthFraction = 0.4f, height = bodyHeight)
                }
                Spacer(Modifier.size(24.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(Modifier.size(36.dp), shape = AppShapes.chip)
                SkeletonTextLine(widthFraction = 0.6f, height = with(LocalDensity.current) { typography.bodyMedium.lineHeight.toDp() })
            }
        }
    }
}
