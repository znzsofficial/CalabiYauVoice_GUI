package com.nekolaska.calabiyau.feature.wiki.extraction.workshop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BuildCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.WikiContentSheet
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.model.*

private val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)

@Composable
internal fun WorkshopRooms(page: WorkshopPage, onNotes: () -> Unit, onRoom: (WorkshopRoom) -> Unit) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item("notes") { WorkshopListHeader("${page.rooms.size} 个舱室", "舱室说明", onNotes) }
        items(page.rooms, key = { "room:${it.overview.name}" }) { room ->
            val card = room.overview
            Surface(
                onClick = { onRoom(room) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        role = Role.Button
                        onClick(label = "查看升级详情", action = null)
                    },
                shape = smoothCornerShape(12.dp),
                color = colors.surfaceContainerLow,
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = smoothCornerShape(10.dp),
                        color = colors.surfaceContainerHigh,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.BuildCircle, null, Modifier.size(20.dp), tint = colors.primary)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                card.name,
                                modifier = Modifier.align(Alignment.CenterVertically),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (card.badge.isNotBlank()) {
                                Box(Modifier.align(Alignment.CenterVertically)) {
                                    WorkshopMicroBadge(card.badge, highlighted = true)
                                }
                            }
                        }
                        if (card.subtitle.isNotBlank()) {
                            Text(
                                card.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun WorkshopRoomSheet(room: WorkshopRoom, onOriginal: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    WikiContentSheet(onDismiss) {
        item("overview") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(12.dp),
                color = colors.surfaceContainer,
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(colors.primary))
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(Modifier.align(Alignment.CenterVertically)) { WorkshopTitle(room.overview.name) }
                            if (room.overview.badge.isNotBlank()) {
                                Box(Modifier.align(Alignment.CenterVertically)) {
                                    WorkshopMicroBadge(room.overview.badge, highlighted = true)
                                }
                            }
                        }
                        if (room.overview.detail.isNotBlank()) Text(room.overview.detail, style = MaterialTheme.typography.bodyMedium)
                        if (room.overview.subtitle.isNotBlank()) WorkshopCaption(room.overview.subtitle)
                    }
                }
            }
        }
        items(room.upgrades, key = { it.level }) { row ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(12.dp),
                color = colors.surfaceContainer,
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(Modifier.align(Alignment.CenterVertically)) {
                            WorkshopMicroBadge(row.level, highlighted = true)
                        }
                        if (row.stats.isNotEmpty()) {
                            Text(
                                row.stats,
                                modifier = Modifier.align(Alignment.CenterVertically),
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                    Text(row.effect, style = MaterialTheme.typography.bodyMedium)
                    if (row.materials.isEmpty()) {
                        if (row.costNote.isNotBlank()) {
                            Surface(
                                shape = smoothCornerShape(8.dp),
                                color = colors.surfaceContainerLow,
                                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.40f))
                            ) {
                                Text(
                                    row.costNote,
                                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text(
                            "升级材料",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        WorkshopMaterials(row.materials)
                    }
                }
            }
        }
        item("actions") { WorkshopSheetActions(onOriginal, onDismiss) }
    }
}

@Composable
internal fun WorkshopStaff(page: WorkshopPage, acceptsInput: () -> Boolean) {
    val groups = listOf("派驻角色", "派驻增益")
    var group by rememberSaveable { mutableStateOf(groups.first()) }
    val states = rememberSaveableStateHolder()
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        WorkshopChoices(groups, group, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { if (acceptsInput()) group = it }
        states.SaveableStateProvider(group) {
            LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item("notes") { SectionNote(page.staffNotes) }
                if (group == groups.first()) items(page.staff, key = { "role:${it.name}" }) { WorkshopInfoRow(it) }
                else items(page.buffs, key = { "buff:${it.name}" }) { buff ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = smoothCornerShape(12.dp),
                        color = colors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(buff.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                buff.effect,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                            )
                            if (buff.rooms.isNotBlank()) {
                                WorkshopMicroBadge("适用舱室：${buff.rooms}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkshopDecorations(page: WorkshopPage, acceptsInput: () -> Boolean) {
    var selected by rememberSaveable { mutableStateOf("家具") }
    val group = page.decorations.find { it.title == selected } ?: page.decorations.first()
    val states = rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize()) {
        WorkshopChoices(
            page.decorations.map { it.title },
            group.title,
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        ) { if (acceptsInput()) selected = it }
        states.SaveableStateProvider(group.title) {
            LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item("notes") { SectionNote(group.notes) }
                items(group.cards, key = { "card:${it.name}" }) { WorkshopInfoRow(it) }
            }
        }
    }
}

@Composable
internal fun WorkshopTitle(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

@Composable
internal fun WorkshopSheetActions(onOriginal: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onOriginal,
            shape = smoothCornerShape(10.dp),
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.65f)),
            modifier = Modifier.heightIn(min = 44.dp)
        ) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Wiki 原文")
        }
        FilledTonalButton(
            onClick = onDismiss,
            shape = smoothCornerShape(10.dp),
            modifier = Modifier.heightIn(min = 44.dp)
        ) { Text("关闭") }
    }
}

@Composable
internal fun WorkshopCaption(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
internal fun WorkshopMicroBadge(text: String, highlighted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = smoothCornerShape(6.dp),
        color = if (highlighted) colors.primaryContainer.copy(alpha = 0.60f) else colors.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            if (highlighted) colors.primary.copy(alpha = 0.35f) else colors.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
            color = if (highlighted) colors.primary else colors.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionNote(text: String) {
    if (text.isBlank()) return
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(12.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
internal fun WorkshopListHeader(count: String, action: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            count,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = colors.onSurfaceVariant
        )
        Surface(
            onClick = onClick,
            modifier = Modifier.heightIn(min = 36.dp).semantics { role = Role.Button },
            shape = smoothCornerShape(8.dp),
            color = colors.surfaceContainerLow,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
        ) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(
                    action,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
internal fun WorkshopChoices(options: List<String>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = selected == option
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                modifier = Modifier.minimumInteractiveComponentSize(),
                shape = smoothCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) colors.primary.copy(alpha = 0.55f) else colors.outlineVariant.copy(alpha = 0.55f)
                ),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.primaryContainer.copy(alpha = 0.65f),
                    selectedLabelColor = colors.onPrimaryContainer,
                    containerColor = colors.surfaceContainerLow,
                    labelColor = colors.onSurface
                ),
                label = { Text(option, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
}

@Composable
private fun WorkshopInfoRow(card: WorkshopCard) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(12.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    card.name,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (card.badge.isNotBlank()) {
                    Box(Modifier.align(Alignment.CenterVertically)) {
                        WorkshopMicroBadge(card.badge)
                    }
                }
            }
            if (card.subtitle.isNotBlank()) {
                WorkshopMicroBadge(card.subtitle, highlighted = true)
            }
            if (card.detail.isNotBlank()) {
                Text(
                    card.detail,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                )
            }
        }
    }
}

@Composable
internal fun WorkshopMaterials(materials: List<WorkshopMaterial>, query: String = "") {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        materials.forEach { material ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(8.dp),
                color = colors.surfaceContainerLow,
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        workshopMatchText(material.name, query),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = smoothCornerShape(6.dp),
                        color = colors.surfaceContainerHigh,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.40f))
                    ) {
                        Text(
                            "× ${material.quantity}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary
                        )
                    }
                }
            }
        }
    }
}
