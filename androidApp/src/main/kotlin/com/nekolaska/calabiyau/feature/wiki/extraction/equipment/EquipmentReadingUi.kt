package com.nekolaska.calabiyau.feature.wiki.extraction.equipment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSizes
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.SkeletonTextLine
import com.nekolaska.calabiyau.core.ui.catalogQualityColor
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.model.EquipmentQuality

// The equipment gallery uses the main palette, with its own authored names (including Q1 优秀).
// Unknown code/name pairs retain the source label with a neutral marker.
private val equipmentColors = mapOf(1 to "优秀", 2 to "精致", 3 to "卓越", 4 to "完美", 8 to "臻藏", 5 to "传说")

@Composable
internal fun EquipmentQualityLabel(quality: EquipmentQuality) {
    val dot = if (equipmentColors[quality.code] == quality.name) catalogQualityColor(quality.code) else MaterialTheme.colorScheme.outline
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Spacer(Modifier.size(7.dp).background(dot, CircleShape))
        Text(quality.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun EquipmentValues(current: EquipmentQuality, fields: Map<String, String>, peer: EquipmentQuality?, comparison: Map<String, String>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val readingWidth = maxWidth / LocalDensity.current.fontScale.coerceAtLeast(1f)
        val wide = readingWidth >= 420.dp
        val stacked = readingWidth < 245.dp
        Column(Modifier.fillMaxWidth()) {
            if (peer != null && !stacked) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                if (wide) Text("属性", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                ComparisonHeading("当前", current, Modifier.weight(1f))
                ComparisonHeading("对比", peer, Modifier.weight(1f))
            }
            (fields.keys + comparison.keys).forEachIndexed { index, label ->
                if (index > 0 || peer != null && !stacked) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val a = fields[label] ?: "未给出"
                val b = comparison[label] ?: "未给出"
                val row = Modifier.fillMaxWidth().clearAndSetSemantics {
                    contentDescription = if (peer == null) "$label：$a"
                        else "$label，当前 ${current.label}：$a；对比 ${peer.label}：$b"
                }.padding(vertical = 7.dp)
                when {
                    peer == null || wide -> Row(row, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FieldLabel(label, Modifier.weight(1f))
                        FieldValue(a, Modifier.weight(1f), peer != null && a != b)
                        if (peer != null) FieldValue(b, Modifier.weight(1f))
                    }
                    else -> Column(row, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FieldLabel(label)
                        if (stacked) {
                            StackedValue("当前", current, a); StackedValue("对比", peer, b)
                        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Repeat the short grade codes, so values remain identifiable after the header scrolls away.
                            ShortValue(current, a, Modifier.weight(1f)); ShortValue(peer, b, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonHeading(role: String, quality: EquipmentQuality, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        FieldLabel(role); EquipmentQualityLabel(quality)
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) = Text(text, modifier,
    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun FieldValue(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false) = Text(text, modifier,
    textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium,
    fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal)

@Composable
private fun ShortValue(quality: EquipmentQuality, value: String, modifier: Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
        FieldLabel("Q${quality.code}"); FieldValue(value)
    }
}

@Composable
private fun StackedValue(role: String, quality: EquipmentQuality, value: String) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(role); EquipmentQualityLabel(quality); FieldValue(value)
    }
}

@Composable
internal fun EquipmentSkeleton(kind: EquipmentKind, modifier: Modifier) {
    val density = LocalDensity.current
    val title = with(density) { MaterialTheme.typography.titleMedium.lineHeight.toDp() }
    val meta = with(density) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }
    val summary = with(density) { MaterialTheme.typography.bodySmall.lineHeight.toDp() }
    val search = with(density) { MaterialTheme.typography.bodyLarge.lineHeight.toDp() + 32.dp }
    LazyColumn(modifier.fillMaxSize().clearAndSetSemantics {
        contentDescription = "正在加载${kind.title}"; progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), userScrollEnabled = false) {
        item {
            ShimmerBox(Modifier.fillMaxWidth().height(maxOf(AppSizes.searchMinHeight, search)), shape = AppShapes.sheet)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                ShimmerBox(Modifier.width(60.dp).height(meta)); ShimmerBox(Modifier.width(96.dp).height(meta))
            }
        }
        items(if (kind == EquipmentKind.ARMOR) 3 else 6) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerBox(Modifier.size(74.dp, 58.dp), shape = AppShapes.compactCard)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SkeletonTextLine(widthFraction = 0.52f, height = title)
                    SkeletonTextLine(widthFraction = 0.75f, height = meta)
                    SkeletonTextLine(widthFraction = 0.66f, height = summary)
                }
                Spacer(Modifier.width(18.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        }
    }
}
