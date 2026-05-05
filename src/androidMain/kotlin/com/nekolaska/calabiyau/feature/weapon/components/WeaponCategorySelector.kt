package com.nekolaska.calabiyau.feature.weapon.components

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SportsMartialArts
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.weapon.list.WeaponListApi
import com.nekolaska.calabiyau.feature.weapon.skin.WeaponSkinFilterApi

data class WeaponCategorySelectorOption(
    val category: String,
    val skinCount: Int = 0,
    val subTypes: List<String> = emptyList()
)

data class WeaponSelectorOption(
    val category: String,
    val type: String,
    val weapon: String,
    val skinCount: Int = 0
)

private val weaponCategoryOrder = listOf(
    WeaponListApi.WeaponCategory.PRIMARY.displayName,
    WeaponListApi.WeaponCategory.SECONDARY.displayName,
    WeaponListApi.WeaponCategory.MELEE.displayName,
    WeaponListApi.WeaponCategory.TACTICAL.displayName
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeaponCategorySelector(
    options: List<WeaponCategorySelectorOption>,
    selectedCategory: String?,
    onSelectedCategoryChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "武器大类",
    allLabel: String = "全部大类",
    showSubTypes: Boolean = true
) {
    var showSheet by remember { mutableStateOf(false) }
    val selectedOption = remember(options, selectedCategory) {
        options.firstOrNull { it.category == selectedCategory }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = { showSheet = true },
        shape = smoothCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeaponCategoryIcon(
                category = selectedOption?.category,
                modifier = Modifier.size(42.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    selectedOption?.category ?: allLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = selectedOption?.let { option ->
                    weaponOptionMeta(option, showSubTypes, maxSubTypes = 4)
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
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
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
                Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "__all") {
                        WeaponCategoryRow(
                            option = null,
                            label = allLabel,
                            selected = selectedCategory == null,
                            onClick = {
                                onSelectedCategoryChange(null)
                                showSheet = false
                            }
                        )
                    }
                    item(key = "__divider") {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                    item(key = "__options_grid") {
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            maxItemsInEachRow = 2
                        ) {
                            options.forEach { option ->
                                WeaponCategoryGridItem(
                                    modifier = Modifier.weight(1f),
                                    option = option,
                                    label = option.category,
                                    selected = option.category == selectedCategory,
                                    showSubTypes = showSubTypes,
                                    onClick = {
                                        onSelectedCategoryChange(option.category)
                                        showSheet = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeaponCategoryGridItem(
    modifier: Modifier = Modifier,
    option: WeaponCategorySelectorOption?,
    label: String,
    selected: Boolean,
    showSubTypes: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(smoothCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = smoothCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeaponCategoryIcon(category = option?.category, modifier = Modifier.size(42.dp))
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
            
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = option?.let { value ->
                    weaponOptionMeta(value, showSubTypes, maxSubTypes = 2)
                }.orEmpty()
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun WeaponCategoryRow(
    option: WeaponCategorySelectorOption?,
    label: String,
    selected: Boolean,
    showSubTypes: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(smoothCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = smoothCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Widgets,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

private fun weaponOptionMeta(
    option: WeaponCategorySelectorOption,
    showSubTypes: Boolean,
    maxSubTypes: Int = Int.MAX_VALUE
): String {
    return buildList {
        if (option.skinCount > 0) add("${option.skinCount} 个外观")
        if (showSubTypes && option.subTypes.isNotEmpty()) {
            add(option.subTypes.take(maxSubTypes).joinToString(" · "))
        }
    }.joinToString(" · ")
}

@Composable
private fun WeaponCategoryIcon(category: String?, modifier: Modifier = Modifier) {
    val icon = weaponCategoryIcon(category)
    Surface(
        modifier = modifier,
        shape = smoothCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(23.dp))
        }
    }
}

private fun weaponCategoryIcon(category: String?): ImageVector = when (category) {
    WeaponListApi.WeaponCategory.PRIMARY.displayName -> Icons.Outlined.Tune
    WeaponListApi.WeaponCategory.SECONDARY.displayName -> Icons.Outlined.Security
    WeaponListApi.WeaponCategory.MELEE.displayName -> Icons.Outlined.SportsMartialArts
    WeaponListApi.WeaponCategory.TACTICAL.displayName -> Icons.Outlined.Construction
    else -> Icons.Outlined.Widgets
}

fun buildWeaponCategorySelectorOptions(
    categoryStats: Map<String, Pair<Int, List<String>>>
): List<WeaponCategorySelectorOption> {
    return weaponCategoryOrder.mapNotNull { category ->
        val (count, subTypes) = categoryStats[category] ?: return@mapNotNull null
        WeaponCategorySelectorOption(
            category = category,
            skinCount = count,
            subTypes = subTypes
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeaponSelector(
    weapons: List<WeaponSelectorOption>,
    selectedCategory: String?,
    selectedWeapon: String?,
    onSelected: (category: String?, weapon: String?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "武器",
    allLabel: String = "全部武器"
) {
    var showSheet by remember { mutableStateOf(false) }
    var sheetCategory by remember(selectedCategory) {
        mutableStateOf(selectedCategory ?: weaponCategoryOrder.first())
    }
    val selectedOption = remember(weapons, selectedWeapon) {
        weapons.firstOrNull { it.weapon == selectedWeapon }
    }
    val categories = remember(weapons) {
        weaponCategoryOrder.filter { category -> weapons.any { it.category == category } }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = { showSheet = true },
        shape = smoothCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    selectedOption?.weapon ?: selectedCategory ?: allLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildList {
                    selectedOption?.category?.takeIf { it.isNotBlank() }?.let(::add)
                    selectedOption?.type?.takeIf { it.isNotBlank() }?.let(::add)
                    selectedOption?.skinCount?.takeIf { it > 0 }?.let { add("$it 个外观") }
                }.joinToString(" · ")
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
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
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
                Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(smoothCornerShape(20.dp))
                        .clickable {
                            onSelected(null, null)
                            showSheet = false
                        },
                    shape = smoothCornerShape(20.dp),
                    color = if (selectedCategory == null && selectedWeapon == null) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    border = if (selectedCategory == null && selectedWeapon == null) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                allLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedCategory == null && selectedWeapon == null) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                "显示所有武器外观",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selectedCategory == null && selectedWeapon == null) {
                                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (selectedCategory == null && selectedWeapon == null) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = sheetCategory == category,
                            onClick = { sheetCategory = category },
                            label = { Text(weaponCategoryShortName(category)) },
                            shape = smoothCornerShape(12.dp),
                            colors = weaponFilterChipColors()
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val currentWeapons = weapons.filter { it.category == sheetCategory }
                    if (sheetCategory == WeaponListApi.WeaponCategory.PRIMARY.displayName) {
                        val grouped = currentWeapons
                            .groupBy { it.type.ifBlank { "其他" } }
                            .toSortedMap(compareBy { it })
                        grouped.forEach { (type, typedWeapons) ->
                            item(key = "type-$type") {
                                Text(
                                    type,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                                )
                            }
                            item(key = "grid-$type") {
                                WeaponOptionGrid(
                                    options = typedWeapons,
                                    selectedWeapon = selectedWeapon,
                                    onSelected = { option ->
                                        onSelected(option.category, option.weapon)
                                        showSheet = false
                                    }
                                )
                            }
                        }
                    } else {
                        item(key = "grid-$sheetCategory") {
                            WeaponOptionGrid(
                                options = currentWeapons,
                                selectedWeapon = selectedWeapon,
                                onSelected = { option ->
                                    onSelected(option.category, option.weapon)
                                    showSheet = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeaponOptionGrid(
    options: List<WeaponSelectorOption>,
    selectedWeapon: String?,
    onSelected: (WeaponSelectorOption) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            WeaponOptionCard(
                modifier = Modifier,
                option = option,
                selected = option.weapon == selectedWeapon,
                onClick = { onSelected(option) }
            )
        }
    }
}

@Composable
private fun WeaponOptionCard(
    modifier: Modifier = Modifier,
    option: WeaponSelectorOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(smoothCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = smoothCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).widthIn(min = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = option.weapon,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
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
            if (option.skinCount > 0) {
                Text(
                    text = "${option.skinCount} 款",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun weaponFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    labelColor = MaterialTheme.colorScheme.onSurface
)

private fun weaponCategoryShortName(category: String): String = when (category) {
    WeaponListApi.WeaponCategory.PRIMARY.displayName -> "主武器"
    WeaponListApi.WeaponCategory.SECONDARY.displayName -> "副武器"
    WeaponListApi.WeaponCategory.MELEE.displayName -> "近战"
    WeaponListApi.WeaponCategory.TACTICAL.displayName -> "道具"
    else -> category
}

fun buildWeaponSelectorOptions(
    skins: Collection<WeaponSkinFilterApi.WeaponSkinInfo>
): List<WeaponSelectorOption> {
    val statsByWeapon = skins
        .filter { it.weapon.isNotBlank() }
        .groupBy { it.weapon }

    return statsByWeapon.mapNotNull { (weapon, weaponSkins) ->
        val first = weaponSkins.firstOrNull() ?: return@mapNotNull null
        WeaponSelectorOption(
            category = first.weaponCategory.ifBlank { "其他" },
            type = first.weaponType,
            weapon = weapon,
            skinCount = weaponSkins.size
        )
    }.sortedWith(
        compareBy<WeaponSelectorOption> { weaponCategoryOrder.indexOf(it.category).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
            .thenBy { it.type }
            .thenBy { it.weapon }
    )
}
