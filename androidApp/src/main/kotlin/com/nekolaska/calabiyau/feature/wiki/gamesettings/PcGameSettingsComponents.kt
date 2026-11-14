package com.nekolaska.calabiyau.feature.wiki.gamesettings

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.DetailField
import com.nekolaska.calabiyau.core.ui.SkeletonTextLine
import com.nekolaska.calabiyau.core.ui.WikiContentSheet
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsBlock
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsSection
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsView

@Composable
internal fun PcSettingsListHeader(view: PcSettingsView, category: String, countLabel: String, onReset: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$category · $countLabel", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onReset != null) TextButton(onClick = onReset) { Text("重置") }
        }
        Text(when (view) {
            PcSettingsView.SETTINGS -> "默认值来自 Wiki · 点按查看完整说明"
            PcSettingsView.RECOMMENDATIONS -> "Wiki 画质建议 · 请按设备与习惯调整"
            PcSettingsView.KNOWLEDGE -> "技术背景与适用范围 · 以原文说明为准"
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun LazyListScope.pcSettingsItems(
    sections: List<PcSettingsSection>, query: String, onSelect: (PcSettingsBlock.Setting) -> Unit
) {
    sections.forEach { section ->
        item(key = "heading:${section.id}", contentType = "section") {
            Text(section.title, Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        var previousGroup = ""
        section.blocks.forEach { block ->
            val group = block.subsection.ifBlank {
                (block as? PcSettingsBlock.Setting)?.recommendationCategory.orEmpty()
            }
            if (group.isNotEmpty() && group != previousGroup) {
                item(key = "group:${block.id}", contentType = "subsection") {
                    Text(group, Modifier.padding(top = 8.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            previousGroup = group
            item(key = block.id, contentType = if (block is PcSettingsBlock.Setting) "setting" else "text") {
                val motion = Modifier.animateItem(
                    fadeInSpec = tween(150), placementSpec = tween(180), fadeOutSpec = tween(100)
                )
                when (block) {
                    is PcSettingsBlock.Setting -> PcSettingRow(motion, block, query) { onSelect(block) }
                    is PcSettingsBlock.Text -> PcSettingsParagraph(motion, block)
                }
            }
        }
    }
}

@Composable
private fun PcSettingRow(modifier: Modifier, setting: PcSettingsBlock.Setting, query: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(setting.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "查看设置详情", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val primaryLabel = if (setting.isQuickReference) "推荐" else "默认"
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (setting.isQuickReference) "Wiki 建议" else "默认", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
                    Text(setting.field(primaryLabel)?.takeIf(String::isNotBlank) ?: "原文未注明",
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                        color = if (setting.isQuickReference) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
                val evidence = setting.fields.firstOrNull { field ->
                    field.label != primaryLabel && terms.isNotEmpty() && terms.any { field.value.contains(it, ignoreCase = true) }
                } ?: setting.fields.firstOrNull { it.label in setOf("作用", "说明", "可选值或说明", "可选值") }
                if (evidence != null && evidence.value.isNotBlank()) {
                    Text("${fieldLabel(evidence.label)}：${evidence.value}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
        }
    }
}

@Composable
private fun PcSettingsParagraph(modifier: Modifier = Modifier, block: PcSettingsBlock.Text) {
    SelectionContainer {
        Text(
            text = if (block.listDepth > 0) "• ${block.text}" else block.text,
            modifier = modifier.fillMaxWidth().padding(start = ((block.listDepth - 1).coerceIn(0, 3) * 12).dp, bottom = 4.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

internal fun fieldLabel(label: String): String = when (label) {
    "默认" -> "原厂默认（Wiki记录）"
    "推荐" -> "Wiki 建议"
    else -> label
}

@Composable
internal fun PcSettingsDetailSheet(setting: PcSettingsBlock.Setting, section: PcSettingsSection, onDismiss: () -> Unit) {
    WikiContentSheet(onDismiss = onDismiss) {
        item(key = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(setting.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(listOf(section.title, setting.subsection, setting.recommendationCategory).filter(String::isNotBlank).joinToString(" / "),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val primaryLabel = if (setting.isQuickReference) "推荐" else "默认"
        setting.field(primaryLabel)?.let { value -> item(key = "primary-value") {
            Surface(shape = MaterialTheme.shapes.medium,
                color = if (setting.isQuickReference) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                SelectionContainer {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(fieldLabel(primaryLabel), style = MaterialTheme.typography.labelLarge)
                        Text(value.ifBlank { "原文未注明" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } }
        if (setting.isQuickReference || setting.field("推荐") != null) item(key = "advice-note") {
            Text("Wiki 建议，请按设备与习惯调整。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(setting.fields.filterNot { it.label == primaryLabel }, key = { "field:${it.label}" }) { field ->
            SelectionContainer {
                DetailField(label = fieldLabel(field.label), value = field.value.ifBlank { "原文未注明" })
            }
        }
        val notes = section.blocks.filterIsInstance<PcSettingsBlock.Text>()
            .filter { it.subsection.isBlank() || it.subsection == setting.subsection }
        if (notes.isNotEmpty()) item(key = "notes-title") {
            Text("本节说明", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        }
        items(notes, key = { "note:${it.id}" }) { PcSettingsParagraph(block = it) }
    }
}

/** Same text-only geometry as the actual rows; search and filters are already mounted above it. */
@Composable
internal fun PcSettingsSkeleton(modifier: Modifier = Modifier, knowledge: Boolean, header: @Composable () -> Unit) {
    val typography = MaterialTheme.typography
    val headingHeight = with(LocalDensity.current) { typography.titleLarge.lineHeight.toDp() }
    val titleHeight = with(LocalDensity.current) { typography.titleMedium.lineHeight.toDp() }
    val valueHeight = with(LocalDensity.current) { typography.bodyLarge.lineHeight.toDp() }
    val bodyHeight = with(LocalDensity.current) { typography.bodySmall.lineHeight.toDp() }
    LazyColumn(
        modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item("source-note") { header() }
        item("heading") {
            SkeletonTextLine(Modifier.padding(top = 12.dp, bottom = 4.dp), widthFraction = .4f, height = headingHeight)
        }
        if (!knowledge) item("subheading") {
            SkeletonTextLine(Modifier.padding(top = 8.dp), widthFraction = .25f, height = bodyHeight)
        }
        items(5, key = { "skeleton:$it" }) {
            if (knowledge) {
                Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonTextLine(widthFraction = .96f)
                    SkeletonTextLine(widthFraction = .9f)
                    SkeletonTextLine(widthFraction = .65f)
                }
            } else Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SkeletonTextLine(Modifier.weight(1f), widthFraction = .65f, height = titleHeight)
                        Spacer(Modifier.size(24.dp))
                    }
                    SkeletonTextLine(widthFraction = .5f, height = valueHeight)
                    SkeletonTextLine(widthFraction = .9f, height = bodyHeight)
                }
            }
        }
    }
}
