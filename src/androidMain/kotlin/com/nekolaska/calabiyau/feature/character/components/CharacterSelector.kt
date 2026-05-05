package com.nekolaska.calabiyau.feature.character.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.feature.character.detail.CharacterDetailApi
import com.nekolaska.calabiyau.feature.character.list.CharacterListApi
import data.ApiResult
import kotlinx.coroutines.async
import java.text.Collator
import java.util.Locale

data class CharacterSelectorOption(
    val name: String,
    val avatarUrl: String? = null,
    val faction: String = "",
    val positionName: String = ""
)

enum class CharacterSelectorGroupMode(val label: String) {
    FACTION("阵营"),
    POSITION("定位")
}

private val characterNameCollator: Collator = Collator.getInstance(Locale.CHINA)
private val positionOrder = listOf("决斗", "守护", "支援", "先锋", "控场")

private fun normalizeSelectorCharacterName(name: String): String {
    return name
        .replace("·", "")
        .replace("・", "")
        .replace(" ", "")
        .replace("　", "")
        .trim()
}

private fun findCompatibleCharacterMeta(
    name: String,
    metaByName: Map<String, CharacterSelectorOption>,
    metaByNormalizedName: Map<String, CharacterSelectorOption>
): CharacterSelectorOption? {
    metaByName[name]?.let { return it }
    val normalizedName = normalizeSelectorCharacterName(name)
    metaByNormalizedName[normalizedName]?.let { return it }
    return metaByNormalizedName.entries
        .filter { (normalizedMetaName, _) ->
            normalizedMetaName.contains(normalizedName) || normalizedName.contains(normalizedMetaName)
        }
        .minByOrNull { (normalizedMetaName, _) -> kotlin.math.abs(normalizedMetaName.length - normalizedName.length) }
        ?.value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterSelector(
    options: List<CharacterSelectorOption>,
    selectedName: String?,
    onSelectedNameChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "角色",
    allLabel: String = "全部角色",
    groupMode: CharacterSelectorGroupMode = CharacterSelectorGroupMode.FACTION,
    showAllOption: Boolean = true
) {
    var showSheet by remember { mutableStateOf(false) }
    val selectedOption = remember(options, selectedName) {
        options.firstOrNull { it.name == selectedName }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = { showSheet = true },
        shape = smoothCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CharacterAvatar(option = selectedOption, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    selectedOption?.name ?: allLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = selectedOption?.let { option ->
                    listOf(option.faction, option.positionName).filter { it.isNotBlank() }.joinToString(" · ")
                }.orEmpty()
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showSheet) {
        CharacterSelectorSheet(
            options = options,
            selectedName = selectedName,
            onSelectedNameChange = {
                onSelectedNameChange(it)
                showSheet = false
            },
            onDismiss = { showSheet = false },
            title = label,
            allLabel = allLabel,
            initialGroupMode = groupMode,
            showAllOption = showAllOption
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterSelectorSheet(
    options: List<CharacterSelectorOption>,
    selectedName: String?,
    onSelectedNameChange: (String?) -> Unit,
    onDismiss: () -> Unit,
    title: String,
    allLabel: String,
    initialGroupMode: CharacterSelectorGroupMode,
    showAllOption: Boolean
) {
    var query by remember { mutableStateOf("") }
    var groupMode by remember(initialGroupMode) { mutableStateOf(initialGroupMode) }

    val filtered = remember(options, query) {
        val trimmed = query.trim()
        options
            .filter { option ->
                trimmed.isBlank() ||
                    option.name.contains(trimmed, ignoreCase = true) ||
                    option.faction.contains(trimmed, ignoreCase = true) ||
                    option.positionName.contains(trimmed, ignoreCase = true)
            }
            .sortedWith { left, right -> characterNameCollator.compare(left.name, right.name) }
    }
    val grouped = remember(filtered, groupMode) { groupCharacterOptions(filtered, groupMode) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = smoothCornerShape(28.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = smoothCornerShape(20.dp),
                placeholder = { Text("搜索角色") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = MaterialTheme.colorScheme.outline,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CharacterSelectorGroupMode.entries.forEach { mode ->
                    FilterChip(
                        selected = groupMode == mode,
                        onClick = { groupMode = mode },
                        label = { Text("按${mode.label}") },
                        leadingIcon = if (groupMode == mode) {
                            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        shape = smoothCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showAllOption) {
                    item(key = "__all") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(smoothCornerShape(16.dp))
                                .clickable { onSelectedNameChange(null) },
                            shape = smoothCornerShape(16.dp),
                            color = if (selectedName == null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = if (selectedName == null) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Groups,
                                    contentDescription = null,
                                    tint = if (selectedName == null) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    allLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedName == null) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.weight(1f))
                                if (selectedName == null) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                    item(key = "__divider") { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
                }

                grouped.forEach { (groupName, groupOptions) ->
                    item(key = "group-$groupName") {
                        Text(
                            groupName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                        )
                    }
                    item(key = "role-${groupName}-grid") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            groupOptions.forEach { option ->
                                CharacterOptionCard(
                                    option = option,
                                    label = option.name,
                                    selected = option.name == selectedName,
                                    onClick = { onSelectedNameChange(option.name) }
                                )
                            }
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("没有匹配的角色", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterOptionCard(
    option: CharacterSelectorOption?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(smoothCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Surface(
            shape = smoothCornerShape(18.dp),
            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier.size(58.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CharacterAvatar(option = option, modifier = Modifier.fillMaxSize())
                if (selected) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 6.dp, bottom = 6.dp)
                                .size(18.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CharacterAvatar(option: CharacterSelectorOption?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!option?.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = option.avatarUrl,
                    contentDescription = option.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    if (option == null) Icons.Outlined.Groups else Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun groupCharacterOptions(
    options: List<CharacterSelectorOption>,
    groupMode: CharacterSelectorGroupMode
): List<Pair<String, List<CharacterSelectorOption>>> {
    val groupOrder = when (groupMode) {
        CharacterSelectorGroupMode.FACTION -> CharacterListApi.FACTIONS
        CharacterSelectorGroupMode.POSITION -> positionOrder
    }
    val grouped = options.groupBy { option ->
        when (groupMode) {
            CharacterSelectorGroupMode.FACTION -> option.faction.ifBlank { "其他" }
            CharacterSelectorGroupMode.POSITION -> option.positionName.ifBlank { "其他" }
        }
    }
    val knownGroups = groupOrder.mapNotNull { group -> grouped[group]?.let { group to it } }
    val otherGroups = grouped
        .filterKeys { it !in groupOrder }
        .toSortedMap(compareBy { it })
        .map { it.key to it.value }
    return knownGroups + otherGroups
}

@Composable
fun rememberCharacterSelectorOptions(
    characterNames: Collection<String>,
    forceRefresh: Boolean = false
): List<CharacterSelectorOption> {
    val normalizedNames = remember(characterNames) { characterNames.filter { it.isNotBlank() }.distinct().sorted() }
    var options by remember(normalizedNames) {
        mutableStateOf(normalizedNames.map { CharacterSelectorOption(name = it) })
    }

    LaunchedEffect(normalizedNames, forceRefresh) {
        val resultDef = async { CharacterListApi.fetchAllFactions(forceRefresh) }
        
        val result = resultDef.await()
        val metaOptions = when (result) {
            is ApiResult.Success -> result.value.flatMap { faction ->
                faction.characters.map { character ->
                    CharacterSelectorOption(
                        name = character.name,
                        avatarUrl = character.imageUrl,
                        faction = faction.faction
                    )
                }
            }
            is ApiResult.Error -> emptyList()
        }
        val metaByName = metaOptions.associateBy { it.name }
        val metaByNormalizedName = metaOptions.associateBy { normalizeSelectorCharacterName(it.name) }
        val matchedOfficialNames = normalizedNames
            .mapNotNull { name -> findCompatibleCharacterMeta(name, metaByName, metaByNormalizedName)?.name }
            .distinct()
        val metaNames = (normalizedNames + matchedOfficialNames).distinct()
        val avatarsDef = async { WikiEngine.fetchCharacterAvatars(metaNames) }
        val positionsDef = async { CharacterDetailApi.fetchCharacterPositions(metaNames, forceRefresh) }
        val allAvatars = avatarsDef.await()
        val positions = positionsDef.await()
        val allAvatarsByNormalizedName = allAvatars.entries.associate { (name, url) -> normalizeSelectorCharacterName(name) to url }
        val positionsByNormalizedName = positions.entries.associate { (name, position) -> normalizeSelectorCharacterName(name) to position }

        options = when (result) {
            is ApiResult.Success -> {
                val enrichedMetaOptions = metaOptions.map { option ->
                    val normalizedMetaName = normalizeSelectorCharacterName(option.name)
                    option.copy(
                        avatarUrl = allAvatars[option.name]
                            ?: allAvatarsByNormalizedName[normalizedMetaName]
                            ?: option.avatarUrl,
                        positionName = positions[option.name]
                            ?: positionsByNormalizedName[normalizedMetaName].orEmpty()
                    )
                }
                val enrichedMetaByName = enrichedMetaOptions.associateBy { it.name }
                val enrichedMetaByNormalizedName = enrichedMetaOptions.associateBy { normalizeSelectorCharacterName(it.name) }
                normalizedNames.map { name ->
                    val normalizedName = normalizeSelectorCharacterName(name)
                    val meta = findCompatibleCharacterMeta(name, enrichedMetaByName, enrichedMetaByNormalizedName)
                    val directAvatar = allAvatars[name] ?: allAvatarsByNormalizedName[normalizedName]
                    meta?.copy(
                        name = name,
                        avatarUrl = if (meta.name == name) directAvatar ?: meta.avatarUrl else meta.avatarUrl ?: directAvatar,
                        positionName = positions[name]
                            ?: positionsByNormalizedName[normalizedName]
                            ?: meta.positionName
                    ) ?: CharacterSelectorOption(
                        name = name,
                        avatarUrl = directAvatar,
                        positionName = positions[name] ?: positionsByNormalizedName[normalizedName].orEmpty()
                    )
                }
            }
            is ApiResult.Error -> normalizedNames.map {
                val normalizedName = normalizeSelectorCharacterName(it)
                CharacterSelectorOption(
                    name = it,
                    avatarUrl = allAvatars[it] ?: allAvatarsByNormalizedName[normalizedName],
                    positionName = positions[it] ?: positionsByNormalizedName[normalizedName].orEmpty()
                )
            }
        }
    }

    return options
}
