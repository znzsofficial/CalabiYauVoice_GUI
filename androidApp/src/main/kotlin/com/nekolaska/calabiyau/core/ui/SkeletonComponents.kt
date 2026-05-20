package com.nekolaska.calabiyau.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults

// ════════════════════════════════════════════════════════
//  Shared loading skeletons
// ════════════════════════════════════════════════════════

/**
 * Animated shimmer background used by placeholder blocks.
 */
@Composable
fun Modifier.shimmerEffect(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 300f, 0f)
    )
    return this.background(brush)
}

/**
 * Clipped shimmer placeholder with a configurable shape.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = smoothCornerShape(8.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .shimmerEffect()
    )
}

/**
 * Card shell for feature-specific skeleton layouts.
 */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = smoothCornerShape(24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

/** Placeholder for a section heading. */
@Composable
fun SkeletonSectionTitle(modifier: Modifier = Modifier) {
    ShimmerBox(modifier.width(100.dp).height(18.dp))
}

/** Two-cell placeholder for compact stat rows. */
@Composable
fun SkeletonStatRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        SkeletonStatCell()
        SkeletonStatCell()
    }
}

/** Row of capsule placeholders for filter chips. */
@Composable
fun SkeletonChipRow(
    modifier: Modifier = Modifier,
    count: Int = 3
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            ShimmerBox(
                Modifier
                    .width(skeletonChipWidth(index))
                    .height(28.dp),
                shape = smoothCapsuleShape()
            )
        }
    }
}

/** Single text-line placeholder with configurable width and height. */
@Composable
fun SkeletonTextLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.85f,
    height: Dp = 14.dp
) {
    ShimmerBox(modifier.fillMaxWidth(widthFraction).height(height))
}

@Composable
private fun RowScope.SkeletonStatCell() {
    Column(Modifier.weight(1f)) {
        ShimmerBox(Modifier.width(40.dp).height(10.dp))
        Spacer(Modifier.height(4.dp))
        ShimmerBox(Modifier.width(60.dp).height(14.dp))
    }
}

private fun skeletonChipWidth(index: Int) = if (index == 0) 72.dp else (64 - index * 4).dp

/**
 * Generic skeleton for Wiki list pages: optional search box, filter rows, then repeated list cards.
 */
@Composable
fun WikiListSkeleton(
    modifier: Modifier = Modifier,
    showSearch: Boolean = true,
    chipRows: Int = 1,
    cardCount: Int = 5
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showSearch) {
            item {
                ShimmerBox(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = smoothCornerShape(28.dp)
                )
            }
        }
        repeat(chipRows) { row ->
            item(key = "chips-$row") {
                SkeletonChipRow(count = 4)
            }
        }
        items(cardCount, key = { it }) {
            SkeletonListCard()
        }
    }
}

@Composable
private fun SkeletonListCard() {
    Card(
        shape = smoothCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerBox(
                modifier = Modifier.size(56.dp),
                shape = smoothCornerShape(16.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonTextLine(widthFraction = 0.55f, height = 16.dp)
                SkeletonTextLine(widthFraction = 0.9f)
                SkeletonTextLine(widthFraction = 0.72f)
            }
        }
    }
}
