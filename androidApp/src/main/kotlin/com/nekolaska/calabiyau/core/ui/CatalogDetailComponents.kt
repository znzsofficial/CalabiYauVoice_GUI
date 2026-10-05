package com.nekolaska.calabiyau.core.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.clearAndSetSemantics
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
    minCellSize: Dp = 110.dp,
    selectorLabel: String,
    qualityLabel: String = "按品质筛选",
    chipLabels: List<String>,
    weaponSelector: Boolean = false
) {
    // 与内容页一样：搜索和筛选占满一行，卡片随同一个网格滚动/测量。
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCellSize),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.clearAndSetSemantics { }) {
                // TextField 默认最小高度 56dp；底部间距与各页 SearchBar 相同。
                Box(Modifier.padding(bottom = 12.dp)) {
                    ShimmerBox(
                        Modifier.fillMaxWidth().height(56.dp),
                        shape = smoothCornerShape(AppShapes.sheetRadius)
                    )
                }
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CatalogSkeletonText(selectorLabel, MaterialTheme.typography.labelMedium)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = smoothCornerShape(if (weaponSelector) 24.dp else 20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = if (weaponSelector) Modifier.padding(20.dp)
                                else Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!weaponSelector) {
                                ShimmerBox(Modifier.size(40.dp), shape = smoothCornerShape(14.dp))
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                CatalogSkeletonText("分类", MaterialTheme.typography.labelMedium)
                                CatalogSkeletonText("全部分类", MaterialTheme.typography.bodyLarge)
                            }
                            Spacer(Modifier.width(12.dp))
                            ShimmerBox(Modifier.size(24.dp))
                        }
                    }
                    CatalogSkeletonText(qualityLabel, MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
                    ) {
                        chipLabels.forEach { label ->
                            // 使用真实 FilterChip 测量文字、内边距和最小触控高度。
                            FilterChip(
                                selected = false,
                                enabled = false,
                                onClick = {},
                                shape = smoothCornerShape(AppShapes.chipRadius),
                                label = { CatalogSkeletonText(label, MaterialTheme.typography.labelLarge) }
                            )
                        }
                    }
                }
            }
        }
        items(12) {
            Card(
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
                shape = smoothCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ShimmerBox(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        shape = smoothCornerShape(14.dp)
                    )
                    // 与 CatalogGridCard 的固定 48dp 标题区一致，避免加载完成时跳高。
                    Box(
                        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ShimmerBox(Modifier.fillMaxWidth(0.75f).height(12.dp))
                            ShimmerBox(Modifier.fillMaxWidth(0.5f).height(10.dp))
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = AppShapes.sheet,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .background(
                        selectedImage.value?.background
                            ?: MaterialTheme.colorScheme.surfaceContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                val preview = selectedImage.value
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
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainerLow)
                            )
                        )
                )
                CatalogQualityBadge(
                    label = qualityLabel,
                    level = qualityLevel,
                    compact = false
                )
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                extraHeader()
                if (images.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        images.forEach { image ->
                            FilterChip(
                                selected = selectedImage.value?.label == image.label,
                                onClick = { selectedImage.value = image },
                                label = { Text(image.label) },
                                shape = smoothCornerShape(12.dp)
                            )
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
                shape = AppShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(Modifier.padding(20.dp)) {
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
