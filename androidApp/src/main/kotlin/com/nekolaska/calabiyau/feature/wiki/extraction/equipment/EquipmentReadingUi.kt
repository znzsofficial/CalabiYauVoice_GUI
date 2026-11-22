package com.nekolaska.calabiyau.feature.wiki.extraction.equipment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSizes
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.ShimmerGroup
import com.nekolaska.calabiyau.core.ui.SkeletonTextLine
import com.nekolaska.calabiyau.core.ui.catalogQualityColor
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.model.EquipmentQuality

// The equipment gallery uses the main palette, with its own authored names (including Q1 优秀).
// Unknown code/name pairs retain the source label with a neutral marker.
private val equipmentColors = mapOf(1 to "优秀", 2 to "精致", 3 to "卓越", 4 to "完美", 8 to "臻藏", 5 to "传说")

@Composable
internal fun equipmentQualityColor(quality: EquipmentQuality): Color =
    if (equipmentColors[quality.code] == quality.name) catalogQualityColor(quality.code)
    else MaterialTheme.colorScheme.outline

@Composable
internal fun EquipmentQualityLabel(quality: EquipmentQuality, asBadge: Boolean = false) {
    val dot = equipmentQualityColor(quality)
    if (asBadge) {
        Surface(
            shape = smoothCornerShape(6.dp),
            color = dot.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, dot.copy(alpha = 0.30f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Spacer(Modifier.size(6.dp).background(dot, CircleShape))
                Text(
                    quality.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Spacer(Modifier.size(7.dp).background(dot, CircleShape))
            Text(
                quality.label,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun EquipmentValues(current: EquipmentQuality, fields: Map<String, String>, peer: EquipmentQuality?, comparison: Map<String, String>) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(12.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val readingWidth = maxWidth / LocalDensity.current.fontScale.coerceAtLeast(1f)
            val wide = readingWidth >= 420.dp
            val stacked = readingWidth < 245.dp
            Column(Modifier.fillMaxWidth()) {
                if (peer == null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceContainerHigh.copy(alpha = 0.65f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "属性",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "数值",
                            Modifier.weight(1f),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.50f))
                } else if (!stacked) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceContainerHigh.copy(alpha = 0.65f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (wide) Text(
                            "属性",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        ComparisonHeading("当前", current, Modifier.weight(1f))
                        ComparisonHeading("对比", peer, Modifier.weight(1f))
                    }
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.50f))
                }
                (fields.keys + comparison.keys).forEachIndexed { index, label ->
                    if (index > 0) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.40f))
                    val a = fields[label] ?: "未给出"
                    val b = comparison[label] ?: "未给出"
                    val changed = peer != null && a != b
                    val row = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics {
                            contentDescription = if (peer == null) "$label：$a"
                            else "$label，当前 ${current.label}：$a；对比 ${peer.label}：$b"
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                    when {
                        peer == null || wide -> Row(
                            row,
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FieldLabel(label, Modifier.weight(1f))
                            FieldValue(a, Modifier.weight(1f), emphasized = changed)
                            if (peer != null) FieldValue(b, Modifier.weight(1f), emphasized = false, muted = !changed)
                        }
                        else -> Column(row, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FieldLabel(label)
                            if (stacked) {
                                StackedValue("当前", current, a, emphasized = changed)
                                StackedValue("对比", peer, b, emphasized = false, muted = !changed)
                            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Repeat the short grade codes, so values remain identifiable after the header scrolls away.
                                ShortValue(current, a, Modifier.weight(1f), emphasized = changed)
                                ShortValue(peer, b, Modifier.weight(1f), emphasized = false, muted = !changed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonHeading(role: String, quality: EquipmentQuality, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FieldLabel(role)
        EquipmentQualityLabel(quality)
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) = Text(
    text,
    modifier,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun FieldValue(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    muted: Boolean = false
) = Text(
    text,
    modifier,
    textAlign = TextAlign.End,
    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
    fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
    color = when {
        emphasized -> MaterialTheme.colorScheme.primary
        muted -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
)

@Composable
private fun ShortValue(
    quality: EquipmentQuality,
    value: String,
    modifier: Modifier,
    emphasized: Boolean = false,
    muted: Boolean = false
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
        FieldLabel("Q${quality.code}")
        FieldValue(value, emphasized = emphasized, muted = muted)
    }
}

@Composable
private fun StackedValue(
    role: String,
    quality: EquipmentQuality,
    value: String,
    emphasized: Boolean = false,
    muted: Boolean = false
) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(role)
        EquipmentQualityLabel(quality)
        FieldValue(value, emphasized = emphasized, muted = muted)
    }
}

@Composable
internal fun EquipmentSkeleton(kind: EquipmentKind, modifier: Modifier) = ShimmerGroup {
    val density = LocalDensity.current
    val title = with(density) { MaterialTheme.typography.titleMedium.lineHeight.toDp() }
    val meta = with(density) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }
    val summary = with(density) { MaterialTheme.typography.bodySmall.lineHeight.toDp() }
    val search = with(density) { MaterialTheme.typography.bodyLarge.lineHeight.toDp() + 32.dp }
    LazyColumn(
        modifier.fillMaxSize().clearAndSetSemantics {
            contentDescription = "正在加载${kind.title}"
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(Modifier.fillMaxWidth().height(maxOf(AppSizes.searchMinHeight, search)), shape = AppShapes.sheet)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ShimmerBox(Modifier.width(64.dp).height(meta), shape = smoothCornerShape(6.dp))
                    ShimmerBox(Modifier.width(104.dp).height(36.dp), shape = smoothCornerShape(10.dp))
                }
            }
        }
        items(if (kind == EquipmentKind.ARMOR) 3 else 6) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShimmerBox(Modifier.size(80.dp, 62.dp), shape = smoothCornerShape(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonTextLine(widthFraction = 0.52f, height = title)
                        SkeletonTextLine(widthFraction = 0.72f, height = meta)
                        SkeletonTextLine(widthFraction = 0.60f, height = summary)
                    }
                    Spacer(Modifier.width(18.dp))
                }
            }
        }
    }
}
