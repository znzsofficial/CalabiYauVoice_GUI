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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.SkeletonTextLine
import com.nekolaska.calabiyau.core.ui.smoothCornerShape

/** One predictable action row below search, shared by both tasks and pool details. */
@Composable
internal fun DropFilterBar(
    summary: String,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    activeFilter: Boolean = false,
    activeCount: Int = 0
) {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            summary,
            Modifier.align(Alignment.CenterVertically).padding(end = 8.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = colors.onSurfaceVariant
        )
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .heightIn(min = 38.dp)
                .semantics {
                    role = Role.Button
                    this.onClick(label = "查看筛选选项", action = null)
                },
            shape = smoothCornerShape(10.dp),
            color = if (activeFilter) colors.primaryContainer.copy(alpha = 0.55f) else colors.surfaceContainerLow,
            border = BorderStroke(
                1.dp,
                if (activeFilter) colors.primary.copy(alpha = 0.65f) else colors.outlineVariant.copy(alpha = 0.55f)
            )
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Outlined.Tune,
                    null,
                    Modifier.size(16.dp),
                    tint = if (activeFilter) colors.primary else colors.onSurfaceVariant
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (activeFilter) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (activeFilter) colors.primary else colors.onSurface
                )
                if (activeFilter && activeCount > 0) {
                    Surface(
                        shape = smoothCornerShape(5.dp),
                        color = colors.primary,
                        contentColor = colors.onPrimary
                    ) {
                        Text(
                            activeCount.toString(),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** Each action clears only what its label names. In particular, a quality change preserves the search. */
@Composable
internal fun ExtractionEmptyResults(
    message: String,
    onClearQuery: (() -> Unit)? = null,
    onClearFilters: (() -> Unit)? = null,
    onShowZero: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = smoothCornerShape(12.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = colors.onSurfaceVariant
            )
            if (onShowZero != null || onClearQuery != null || onClearFilters != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onShowZero != null) FilledTonalButton(
                        onClick = onShowZero,
                        shape = smoothCornerShape(8.dp)
                    ) { Text("显示零掉落来源") }
                    if (onClearQuery != null) OutlinedButton(
                        onClick = onClearQuery,
                        shape = smoothCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.65f))
                    ) { Text("清除搜索") }
                    if (onClearFilters != null) OutlinedButton(
                        onClick = onClearFilters,
                        shape = smoothCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.65f))
                    ) { Text("清除筛选") }
                }
            }
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
        fadeInSpec = tween(DropMotion.reveal),
        placementSpec = tween(DropMotion.move, easing = FastOutSlowInEasing),
        fadeOutSpec = tween(DropMotion.exit)
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
        shape = smoothCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerBox(
                Modifier.size(if (source) 40.dp else 56.dp),
                shape = smoothCornerShape(10.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SkeletonTextLine(widthFraction = 0.65f, height = titleHeight)
                if (source) {
                    SkeletonTextLine(widthFraction = 0.8f, height = bodyHeight)
                    SkeletonTextLine(
                        widthFraction = 0.65f,
                        height = with(LocalDensity.current) { typography.labelMedium.lineHeight.toDp() } + 6.dp
                    )
                } else ShimmerBox(Modifier.width(128.dp).height(badgeHeight), shape = smoothCornerShape(6.dp))
            }
            Spacer(Modifier.size(20.dp))
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
        shape = smoothCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerBox(Modifier.size(40.dp), shape = smoothCornerShape(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkeletonTextLine(widthFraction = 0.65f, height = titleHeight)
                    SkeletonTextLine(widthFraction = 0.4f, height = bodyHeight)
                }
                Spacer(Modifier.size(20.dp))
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ShimmerBox(Modifier.size(36.dp), shape = smoothCornerShape(8.dp))
                    SkeletonTextLine(
                        widthFraction = 0.6f,
                        height = with(LocalDensity.current) { typography.bodySmall.lineHeight.toDp() }
                    )
                }
            }
        }
    }
}
