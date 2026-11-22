package com.nekolaska.calabiyau.core.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val CatalogGridCardRadius = 18.dp
private val CatalogDetailStageRadius = 22.dp
private val CatalogDetailBlockRadius = 20.dp

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
    val cardShape = smoothCornerShape(CatalogGridCardRadius)
    val labelMinHeight = with(LocalDensity.current) {
        MaterialTheme.typography.labelMedium.lineHeight.toDp() * 2
    }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(cardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .then(
                        if (qualityColor != null) {
                            Modifier.background(
                                Brush.verticalGradient(
                                    listOf(qualityColor.copy(alpha = 0.14f), Color.Transparent)
                                )
                            )
                        } else {
                            Modifier
                        }
                    ),
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
                    Icon(
                        fallbackIcon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.36f)
                    )
                }
                CatalogQualityBadge(
                    label = qualityLabel,
                    level = qualityLevel,
                    padding = 6.dp
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .heightIn(min = labelMinHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
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
    minCellSize: Dp = 110.dp,
    selectorLabel: String,
    qualityLabel: String = "按品质筛选",
    chipLabels: List<String>,
    weaponSelector: Boolean = false,
    beforeSearch: @Composable () -> Unit = {}
) = ShimmerGroup {
    val cardShape = smoothCornerShape(CatalogGridCardRadius)
    val labelMinHeight = with(LocalDensity.current) {
        MaterialTheme.typography.labelMedium.lineHeight.toDp() * 2
    }
    // 与内容页一样：搜索和筛选占满一行，卡片随同一个网格滚动/测量。
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCellSize),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .clearAndSetSemantics { },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                beforeSearch()
                ShimmerBox(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = smoothCornerShape(AppShapes.sheetRadius)
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = smoothCornerShape(if (weaponSelector) 24.dp else 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = if (weaponSelector) {
                            Modifier.padding(20.dp)
                        } else {
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!weaponSelector) {
                            ShimmerBox(Modifier.size(40.dp), shape = smoothCornerShape(14.dp))
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CatalogSkeletonText(selectorLabel.ifBlank { "分类" }, MaterialTheme.typography.labelMedium)
                            CatalogSkeletonText(qualityLabel.ifBlank { "全部" }, MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.width(12.dp))
                        ShimmerBox(Modifier.size(24.dp))
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
                ) {
                    chipLabels.forEach { label ->
                        // 使用真实 FilterChip 测量文字、内边距和最小触控高度。
                        FilterChip(
                            selected = false,
                            enabled = false,
                            onClick = {},
                            shape = AppShapes.capsule,
                            label = { CatalogSkeletonText(label, MaterialTheme.typography.labelLarge) }
                        )
                    }
                }
            }
        }
        items(12) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { },
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        shape = cardShape
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .heightIn(min = labelMinHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ShimmerBox(Modifier.fillMaxWidth(0.78f).height(12.dp))
                            ShimmerBox(Modifier.fillMaxWidth(0.52f).height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Measure with the real typography so placeholders also follow system font scaling. */
@Composable
private fun CatalogSkeletonText(text: String, style: TextStyle) {
    Box {
        Text(text, style = style, color = Color.Transparent, maxLines = 1)
        ShimmerBox(Modifier.matchParentSize(), shape = smoothCornerShape(6.dp))
    }
}

data class CatalogPreviewImage(
    val label: String,
    val url: String,
    val contentScale: ContentScale = ContentScale.Crop,
    val background: Color = Color.Transparent
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogDetailSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    images: List<CatalogPreviewImage> = emptyList(),
    qualityLabel: String? = null,
    qualityLevel: Int? = null,
    description: String = "",
    descriptionCollapsedLines: Int = 4,
    descriptionExpandedMaxHeight: Dp = 240.dp,
    heroHeight: Dp = 260.dp,
    imageContent: (@Composable () -> Unit)? = null,
    extraHeader: @Composable () -> Unit = {},
    details: @Composable () -> Unit
) {
    val selectedImage = remember(images) { mutableStateOf(images.firstOrNull()) }
    val qualityColor = qualityLevel?.let { catalogQualityColor(it) }
    val hasVisualStage = imageContent != null || images.isNotEmpty()
    val stageShape = smoothCornerShape(CatalogDetailStageRadius)

    WikiScrollableSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            if (hasVisualStage) {
                val preview = selectedImage.value
                val explicitBackground = preview?.background?.takeIf { it != Color.Transparent }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(heroHeight)
                        .clip(stageShape)
                        .background(explicitBackground ?: MaterialTheme.colorScheme.surfaceContainer)
                        .then(
                            if (explicitBackground == null && qualityColor != null) {
                                Modifier.background(
                                    Brush.verticalGradient(
                                        listOf(
                                            qualityColor.copy(alpha = 0.16f),
                                            qualityColor.copy(alpha = 0.04f),
                                            Color.Transparent
                                        )
                                    )
                                )
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageContent != null) {
                        imageContent()
                    } else if (preview != null) {
                        AsyncImage(
                            model = preview.url,
                            contentDescription = title,
                            contentScale = preview.contentScale,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    CatalogQualityBadge(
                        label = qualityLabel,
                        level = qualityLevel,
                        compact = false,
                        padding = 12.dp
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = if (hasVisualStage) 0.dp else 8.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                val headerQualityLabel = qualityLabel?.takeIf {
                    !hasVisualStage && it.isNotBlank() && qualityColor != null
                }
                if (subtitle.isNotBlank() || headerQualityLabel != null) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (subtitle.isNotBlank()) {
                            Surface(
                                shape = AppShapes.capsule,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                        if (headerQualityLabel != null && qualityColor != null) {
                            Surface(
                                shape = AppShapes.capsule,
                                color = qualityColor.copy(alpha = 0.14f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(Modifier.size(7.dp).background(qualityColor, CircleShape))
                                    Text(
                                        headerQualityLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                extraHeader()
                if (images.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        images.forEach { image ->
                            val isSelected = selectedImage.value?.label == image.label
                            Surface(
                                onClick = { selectedImage.value = image },
                                modifier = Modifier.semantics { this.selected = isSelected },
                                shape = AppShapes.capsule,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            ) {
                                Text(
                                    image.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (description.isNotBlank()) {
                ExpandableCatalogDescription(
                    text = description,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    collapsedLines = descriptionCollapsedLines,
                    expandedMaxHeight = descriptionExpandedMaxHeight
                )
                Spacer(Modifier.height(12.dp))
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = smoothCornerShape(CatalogDetailBlockRadius),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(Modifier.padding(18.dp)) {
                    details()
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
    val displayValue = value.ifBlank { "未知" }
    val isAccentValue = valueColor != MaterialTheme.colorScheme.onSurface && valueColor != Color.Unspecified
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = smoothCornerShape(12.dp),
            color = if (imageUrl != null) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            }
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
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 128.dp)
        )
        if (isAccentValue) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    shape = AppShapes.capsule,
                    color = valueColor.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(7.dp).background(valueColor, CircleShape))
                        Text(
                            displayValue,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            Text(
                displayValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
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
    var expanded by remember(text) { mutableStateOf(false) }
    var hasOverflow by remember(text, collapsedLines) { mutableStateOf(false) }
    val showToggle = expanded || hasOverflow
    val scrollState = rememberScrollState()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = smoothCornerShape(CatalogDetailBlockRadius),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (expanded) {
                        Modifier
                            .heightIn(max = expandedMaxHeight)
                            .verticalScroll(scrollState)
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (showToggle) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { expanded = !expanded }
                    } else {
                        Modifier
                    }
                )
                .animateContentSize()
                .padding(18.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layoutResult ->
                    if (!expanded) {
                        hasOverflow = layoutResult.hasVisualOverflow
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (showToggle) {
                Text(
                    if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
