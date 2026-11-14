package com.nekolaska.calabiyau.feature.wiki.gamesettings

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.DetailField
import com.nekolaska.calabiyau.core.ui.SkeletonTextLine
import com.nekolaska.calabiyau.core.ui.WikiContentSheet
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsBlock
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsSection
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsView

@Composable
internal fun PcSettingsListHeader(view: PcSettingsView, category: String, countLabel: String, onReset: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = AppShapes.chip,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    "$category · $countLabel",
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.weight(1f))
            if (onReset != null) TextButton(onClick = onReset) { Text("重置") }
        }
        Surface(
            shape = AppShapes.compactCard,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    when (view) {
                        PcSettingsView.SETTINGS -> Icons.Outlined.Tune
                        PcSettingsView.RECOMMENDATIONS -> Icons.Outlined.AutoAwesome
                        PcSettingsView.KNOWLEDGE -> Icons.Outlined.Lightbulb
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    when (view) {
                        PcSettingsView.SETTINGS -> "默认值来自 Wiki · 点按条目查看完整选项与说明"
                        PcSettingsView.RECOMMENDATIONS -> "Wiki 画质建议 · 请结合设备性能与游玩习惯调整"
                        PcSettingsView.KNOWLEDGE -> "技术背景与渲染比例参考 · 以原文说明为准"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal fun LazyListScope.pcSettingsItems(
    sections: List<PcSettingsSection>, query: String, onSelect: (PcSettingsBlock.Setting) -> Unit
) {
    sections.forEach { section ->
        item(key = "heading:${section.id}", contentType = "section") {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.width(4.dp).height(20.dp)
                        .background(MaterialTheme.colorScheme.primary, smoothCornerShape(2.dp))
                )
                Text(
                    section.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                val count = if (section.view == PcSettingsView.KNOWLEDGE) section.blocks.size
                    else section.blocks.count { it is PcSettingsBlock.Setting }
                if (count > 0) {
                    Surface(
                        shape = AppShapes.chip,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            "$count",
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        var previousGroup = ""
        section.blocks.forEach { block ->
            val group = block.subsection.ifBlank {
                (block as? PcSettingsBlock.Setting)?.recommendationCategory.orEmpty()
            }
            if (group.isNotEmpty() && group != previousGroup) {
                item(key = "group:${block.id}", contentType = "subsection") {
                    Text(
                        group,
                        Modifier.padding(top = 6.dp, bottom = 2.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            previousGroup = group
            item(key = block.id, contentType = when (block) {
                is PcSettingsBlock.Setting -> "setting"
                is PcSettingsBlock.Reference -> "reference"
                is PcSettingsBlock.Text -> "text"
            }) {
                val motion = Modifier.animateItem(
                    fadeInSpec = tween(150), placementSpec = tween(180), fadeOutSpec = tween(100)
                )
                when (block) {
                    is PcSettingsBlock.Setting -> PcSettingRow(motion, block, query) { onSelect(block) }
                    is PcSettingsBlock.Text -> PcSettingsParagraph(motion, block)
                    is PcSettingsBlock.Reference -> PcSettingsReferenceRow(motion, block)
                }
            }
        }
    }
}

@Composable
private fun PcSettingsReferenceRow(modifier: Modifier, block: PcSettingsBlock.Reference) {
    Surface(
        modifier.fillMaxWidth(),
        shape = AppShapes.compactCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        SelectionContainer {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        block.nameLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        block.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (block.fields.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        block.fields.chunked(2).forEach { pair ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { field ->
                                    Surface(
                                        Modifier.weight(1f),
                                        shape = smoothCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer
                                    ) {
                                        Column(
                                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                field.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                field.value.ifBlank { "原文未注明" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PcSettingRow(modifier: Modifier, setting: PcSettingsBlock.Setting, query: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.compactCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (setting.isQuickReference) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    setting.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "查看设置详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val primaryLabel = if (setting.isQuickReference) "推荐" else "默认"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = smoothCornerShape(8.dp),
                    color = if (setting.isQuickReference) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        if (setting.isQuickReference) "Wiki 建议" else "默认",
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (setting.isQuickReference) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    setting.field(primaryLabel)?.takeIf(String::isNotBlank) ?: "原文未注明",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (setting.isQuickReference) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            val evidence = setting.fields.firstOrNull { field ->
                field.label != primaryLabel && terms.isNotEmpty() && terms.any { field.value.contains(it, ignoreCase = true) }
            } ?: setting.fields.firstOrNull { it.label in setOf("作用", "说明", "可选值或说明", "可选值") }
            if (evidence != null && evidence.value.isNotBlank()) {
                Text(
                    "${fieldLabel(evidence.label)}：${evidence.value}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PcSettingsParagraph(modifier: Modifier = Modifier, block: PcSettingsBlock.Text) {
    SelectionContainer {
        if (block.listDepth > 0) {
            Row(
                modifier = modifier.fillMaxWidth()
                    .padding(start = ((block.listDepth - 1).coerceIn(0, 3) * 14).dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    Modifier.padding(top = 8.dp).size(6.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.78f), CircleShape)
                )
                Text(
                    text = block.text,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
            }
        } else {
            Text(
                text = block.text,
                modifier = modifier.fillMaxWidth().padding(bottom = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp
            )
        }
    }
}

internal fun fieldLabel(label: String): String = when (label) {
    "默认" -> "原厂默认（Wiki记录）"
    "推荐" -> "Wiki 建议"
    else -> label
}

@Composable
internal fun PcSettingsDetailSheet(setting: PcSettingsBlock.Setting, section: PcSettingsSection, onDismiss: () -> Unit) {
    val tags = remember(section.title, setting.subsection, setting.recommendationCategory) {
        listOf(section.title, setting.subsection, setting.recommendationCategory)
            .filter(String::isNotBlank)
            .distinct()
    }
    val primaryLabel = if (setting.isQuickReference) "推荐" else "默认"
    val extraFields = remember(setting.fields, primaryLabel) {
        setting.fields.filterNot { it.label == primaryLabel }
    }
    val notes = remember(section.blocks, setting.subsection) {
        section.blocks.filterIsInstance<PcSettingsBlock.Text>()
            .filter { it.subsection.isBlank() || it.subsection == setting.subsection }
    }

    WikiContentSheet(onDismiss = onDismiss) {
        item(key = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(setting.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { tag ->
                            Surface(
                                shape = AppShapes.chip,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    tag,
                                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        setting.field(primaryLabel)?.let { value ->
            item(key = "primary-value") {
                Surface(
                    shape = AppShapes.compactCard,
                    color = if (setting.isQuickReference) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        if (setting.isQuickReference) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    SelectionContainer {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    if (setting.isQuickReference) Icons.Outlined.AutoAwesome else Icons.Outlined.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (setting.isQuickReference) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    fieldLabel(primaryLabel),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (setting.isQuickReference) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                value.ifBlank { "原文未注明" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
        if (setting.isQuickReference || setting.field("推荐") != null) item(key = "advice-note") {
            Surface(
                shape = AppShapes.compactCard,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Wiki 建议，请按设备性能与个人习惯调整。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (extraFields.isNotEmpty()) {
            item(key = "fields-card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    SelectionContainer {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            extraFields.forEachIndexed { index, field ->
                                if (index > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                }
                                DetailField(
                                    label = fieldLabel(field.label),
                                    value = field.value.ifBlank { "原文未注明" }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (notes.isNotEmpty()) {
            item(key = "notes-title") {
                Text(
                    "本节说明",
                    Modifier.padding(top = 4.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            item(key = "notes-card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        notes.forEach { PcSettingsParagraph(block = it) }
                    }
                }
            }
        }
    }
}

/** Same geometry as the actual rows; search and filters are already mounted above it. */
@Composable
internal fun PcSettingsSkeleton(modifier: Modifier = Modifier, knowledge: Boolean, header: @Composable () -> Unit) {
    val typography = MaterialTheme.typography
    val headingHeight = with(LocalDensity.current) { typography.titleLarge.lineHeight.toDp() }
    val titleHeight = with(LocalDensity.current) { typography.titleMedium.lineHeight.toDp() }
    val valueHeight = with(LocalDensity.current) { typography.bodyLarge.lineHeight.toDp() }
    val bodyHeight = with(LocalDensity.current) { typography.bodySmall.lineHeight.toDp() }
    LazyColumn(
        modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false
    ) {
        item("source-note") { header() }
        item("heading") {
            SkeletonTextLine(Modifier.padding(top = 12.dp, bottom = 4.dp), widthFraction = .4f, height = headingHeight)
        }
        if (!knowledge) item("subheading") {
            SkeletonTextLine(Modifier.padding(top = 6.dp, bottom = 2.dp), widthFraction = .25f, height = bodyHeight)
        }
        items(5, key = { "skeleton:$it" }) {
            if (knowledge) {
                Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonTextLine(widthFraction = .96f)
                    SkeletonTextLine(widthFraction = .9f)
                    SkeletonTextLine(widthFraction = .65f)
                }
            } else Surface(
                shape = AppShapes.compactCard,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
