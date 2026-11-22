package com.nekolaska.calabiyau.feature.wiki.extraction.workshop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.WikiContentSheet
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.model.*

private val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)

@Composable
internal fun WorkshopRooms(page: WorkshopPage, onNotes: () -> Unit, onRoom: (WorkshopRoom) -> Unit) {
    LazyColumn(contentPadding = listPadding) {
        item("notes") { WorkshopListHeader("${page.rooms.size} 个舱室", "舱室说明", onNotes) }
        items(page.rooms, key = { "room:${it.overview.name}" }) { room ->
            val card = room.overview
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "查看升级详情") { onRoom(room) }
                .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    WorkshopCaption(card.badge); Text(card.subtitle, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
            HorizontalDivider()
        }
    }
}

@Composable
internal fun WorkshopRoomSheet(room: WorkshopRoom, onOriginal: () -> Unit, onDismiss: () -> Unit) {
    WikiContentSheet(onDismiss) {
        item("overview") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WorkshopTitle(room.overview.name); WorkshopCaption(room.overview.badge)
                Text(room.overview.detail); WorkshopCaption(room.overview.subtitle)
            }
        }
        items(room.upgrades, key = { it.level }) { row ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(); Text(row.level, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(row.effect, style = MaterialTheme.typography.bodyMedium)
                if (row.stats.isNotEmpty()) WorkshopCaption(row.stats)
                if (row.materials.isEmpty()) WorkshopCaption(row.costNote) else {
                    Text("升级材料", style = MaterialTheme.typography.labelLarge)
                    WorkshopMaterials(row.materials)
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
    Column(Modifier.fillMaxSize()) {
        WorkshopChoices(groups, group, Modifier.padding(horizontal = 16.dp)) { if (acceptsInput()) group = it }
        states.SaveableStateProvider(group) {
            LazyColumn(contentPadding = listPadding) {
                item("notes") { SectionNote(page.staffNotes) }
                if (group == groups.first()) items(page.staff, key = { "role:${it.name}" }) { WorkshopInfoRow(it) }
                else items(page.buffs, key = { "buff:${it.name}" }) { buff ->
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(buff.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(buff.effect, style = MaterialTheme.typography.bodyMedium); WorkshopCaption("适用舱室：${buff.rooms}")
                    }
                    HorizontalDivider()
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
        WorkshopChoices(page.decorations.map { it.title }, group.title, Modifier.padding(horizontal = 16.dp)) { if (acceptsInput()) selected = it }
        states.SaveableStateProvider(group.title) {
            LazyColumn(contentPadding = listPadding) {
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
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onOriginal, modifier = Modifier.heightIn(min = 48.dp)) { Text("Wiki 原文") }
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("关闭") }
    }
}

@Composable
internal fun WorkshopCaption(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun SectionNote(text: String) = Text(text, Modifier.padding(vertical = 8.dp),
    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
internal fun WorkshopListHeader(count: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(count, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

@Composable
internal fun WorkshopChoices(options: List<String>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { FilterChip(selected = selected == it, onClick = { onSelect(it) },
            modifier = Modifier.minimumInteractiveComponentSize(), label = { Text(it) }) }
    }
}

@Composable
private fun WorkshopInfoRow(card: WorkshopCard) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (card.badge.isNotBlank()) WorkshopCaption(card.badge)
        if (card.subtitle.isNotBlank()) Text(card.subtitle, style = MaterialTheme.typography.labelLarge)
        if (card.detail.isNotBlank()) Text(card.detail, style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider()
}

@Composable
internal fun WorkshopMaterials(materials: List<WorkshopMaterial>, query: String = "") {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        materials.forEach { material ->
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(workshopMatchText(material.name, query), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("×${material.quantity}", Modifier.widthIn(min = 36.dp), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
