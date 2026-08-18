package com.nekolaska.calabiyau.core.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun CatalogGridCard(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    qualityLabel: String? = null,
    qualityLevel: Int? = null,
    fallbackIcon: ImageVector? = null,
    contentScale: ContentScale = ContentScale.Crop,
    imagePadding: Dp = 0.dp
) {
    val qualityColor = qualityLevel?.let { catalogQualityColor(it) }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = smoothCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = 1.dp,
            color = qualityColor?.copy(alpha = 0.4f)
                ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(smoothCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = name,
                        contentScale = contentScale,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(imagePadding)
                    )
                } else if (fallbackIcon != null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                fallbackIcon,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
                CatalogQualityBadge(label = qualityLabel, level = qualityLevel)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun CatalogGridSkeleton(
    modifier: Modifier = Modifier,
    showSelector: Boolean = true,
    chipCount: Int = 6
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .height(52.dp),
            shape = smoothCornerShape(28.dp)
        )
        if (showSelector) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .width(72.dp)
                        .height(14.dp),
                    shape = smoothCornerShape(6.dp)
                )
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = smoothCornerShape(20.dp)
                )
                ShimmerBox(
                    modifier = Modifier
                        .width(72.dp)
                        .height(14.dp),
                    shape = smoothCornerShape(6.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(chipCount) {
                        ShimmerBox(
                            modifier = Modifier
                                .width(if (it == 0) 72.dp else 60.dp)
                                .height(32.dp),
                            shape = AppShapes.capsule
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(chipCount) {
                    ShimmerBox(
                        modifier = Modifier
                            .width(if (it == 0) 88.dp else 64.dp)
                            .height(32.dp),
                        shape = AppShapes.capsule
                    )
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            userScrollEnabled = false
        ) {
            items(12) {
                Card(
                    shape = smoothCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            shape = smoothCornerShape(14.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        ShimmerBox(Modifier.fillMaxWidth(0.75f).height(12.dp))
                        Spacer(Modifier.height(6.dp))
                        ShimmerBox(Modifier.fillMaxWidth(0.5f).height(10.dp))
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CatalogDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    imageUrl: String? = null,
    fallbackImageUrl: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (imageUrl != null) {
                    var useFallback by remember(imageUrl, fallbackImageUrl) { mutableStateOf(false) }
                    AsyncImage(
                        model = if (useFallback) fallbackImageUrl ?: imageUrl else imageUrl,
                        contentDescription = label,
                        contentScale = ContentScale.Fit,
                        onError = {
                            if (!useFallback && !fallbackImageUrl.isNullOrBlank()) {
                                useFallback = true
                            }
                        },
                        modifier = Modifier.size(22.dp)
                    )
                } else if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            value.ifBlank { "未知" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
fun ExpandableCatalogDescription(
    text: String,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 4,
    expandedMaxHeight: Dp = 240.dp
) {
    if (text.isBlank()) return
    var expanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expanded) Modifier.verticalScroll(scrollState) else Modifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded }
                .animateContentSize()
                .padding(20.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (expanded) expandedMaxHeight else Dp.Unspecified)
            )
            Text(
                if (expanded) "收起" else "展开",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
