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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.SkeletonTextLine

/** One predictable action row below search, shared by both tasks and pool details. */
@Composable
internal fun DropFilterBar(
    summary: String, label: String, onClick: () -> Unit,
    enabled: Boolean = true, activeFilter: Boolean = false, onReset: (() -> Unit)? = null
) {
    FlowRow(
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            summary,
            Modifier.weight(1f).align(Alignment.CenterVertically),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterVertically),
            shape = AppShapes.chip,
            color = if (activeFilter) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (activeFilter) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.primary
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (activeFilter) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (onReset != null) {
            TextButton(onClick = onReset, modifier = Modifier.align(Alignment.CenterVertically)) {
                Text("重置")
            }
        }
    }
}

/** Finite native Compose animations respect the system animator duration scale, including zero. */
internal fun Modifier.dropItemMotion(scope: LazyItemScope): Modifier = with(scope) {
    this@dropItemMotion.animateItem(
        fadeInSpec = tween(180), placementSpec = tween(220, easing = FastOutSlowInEasing), fadeOutSpec = tween(120)
    )
}

/** Mirrors the 12dp inset, 56dp artwork / 40dp source badge and three text slots of a lookup result. */
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
                ShimmerBox(Modifier.width(76.dp).height(if (source) bodyHeight else badgeHeight))
                SkeletonTextLine(widthFraction = 0.45f, height = bodyHeight)
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

/** Source cards have a heading, quality chips and footer, rather than item-list geometry. */
@Composable
internal fun DropSourceCardSkeleton(modifier: Modifier = Modifier) {
    val typography = MaterialTheme.typography
    val titleHeight = with(LocalDensity.current) { typography.titleMedium.lineHeight.toDp() }
    val bodyHeight = with(LocalDensity.current) { typography.bodySmall.lineHeight.toDp() }
    val labelHeight = with(LocalDensity.current) { typography.labelLarge.lineHeight.toDp() }
    val badgeHeight = with(LocalDensity.current) { typography.labelSmall.lineHeight.toDp() } + 6.dp
    Surface(
        modifier.clearAndSetSemantics {},
        shape = AppShapes.compactCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerBox(Modifier.size(40.dp), shape = AppShapes.sectionIcon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkeletonTextLine(widthFraction = 0.65f, height = titleHeight)
                    SkeletonTextLine(widthFraction = 0.4f, height = bodyHeight)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { ShimmerBox(Modifier.width(64.dp).height(badgeHeight)) }
            }
            SkeletonTextLine(widthFraction = 0.6f, height = bodyHeight)
            SkeletonTextLine(widthFraction = 0.45f, height = labelHeight)
        }
    }
}
