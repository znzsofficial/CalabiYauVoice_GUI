package com.nekolaska.calabiyau.feature.wiki.extraction.workshop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.model.*
import kotlinx.coroutines.launch

@Composable
internal fun WorkshopRecipes(
    page: WorkshopPage, query: String, acceptsInput: () -> Boolean, onQuery: (String) -> Unit,
    onNotes: () -> Unit, onRecipe: (RecipeMatch) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val results = remember(page.recipes, query) { page.recipes.searchRecipes(query) }
    val colors = MaterialTheme.colorScheme
    fun search(text: String) {
        if (!acceptsInput()) return
        onQuery(text)
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            scope.launch { if (acceptsInput()) listState.scrollToItem(0) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchBar(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            keyword = query,
            onKeywordChange = ::search,
            onClear = { search("") },
            isSearching = false,
            placeholder = "搜索成品或材料"
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item("count") { WorkshopListHeader("${results.size} 个配方", "配方说明", onNotes) }
            if (results.isEmpty()) item("empty") {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = smoothCornerShape(12.dp),
                    color = colors.surfaceContainerLow,
                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
                ) {
                    CatalogEmptyResults(message = "未找到相关配方", onClear = { search("") })
                }
            }
            items(results, key = { "recipe:${it.recipe.id}" }) { match ->
                val recipe = match.recipe
                val hasMaterialMatch = match.materialTiers.isNotEmpty()
                Surface(
                    onClick = { onRecipe(match) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            role = Role.Button
                            onClick(label = "查看配方", action = null)
                        },
                    shape = smoothCornerShape(12.dp),
                    color = colors.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (hasMaterialMatch) colors.primary.copy(alpha = 0.45f)
                        else colors.outlineVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RecipeArtwork(recipe.image)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                workshopMatchText(recipe.name, query),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                WorkshopMicroBadge(recipe.room)
                                if (recipe.category.isNotBlank()) WorkshopMicroBadge(recipe.category)
                            }
                            if (match.materialTiers.isEmpty()) {
                                if (recipe.range.isNotBlank()) WorkshopCaption(recipe.range)
                            } else {
                                Surface(
                                    shape = smoothCornerShape(6.dp),
                                    color = colors.primaryContainer.copy(alpha = 0.55f),
                                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        "使用该材料的品质档：${match.materialTiers.joinToString("、") { it.quality }}",
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.primary
                                    )
                                }
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
}

internal val recipeArtworkSize = Modifier.size(width = 64.dp, height = 52.dp)

@Composable
private fun RecipeArtwork(url: String?) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = recipeArtworkSize,
        shape = smoothCornerShape(10.dp),
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            var loaded by remember(url) { mutableStateOf(false) }
            if (!loaded) Icon(Icons.Outlined.Inventory2, null, modifier = Modifier.size(22.dp), tint = colors.onSurfaceVariant)
            if (url != null) AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                onLoading = { loaded = false },
                onError = { loaded = false },
                onSuccess = { loaded = true }
            )
        }
    }
}

@Composable
internal fun workshopMatchText(text: String, query: String): AnnotatedString {
    val accent = MaterialTheme.colorScheme.primary
    return remember(text, query, accent) {
        buildAnnotatedString {
            append(text)
            val term = query.trim()
            if (term.isNotEmpty()) {
                var offset = text.indexOf(term, ignoreCase = true)
                while (offset >= 0) {
                    addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), offset, offset + term.length)
                    offset = text.indexOf(term, offset + term.length, ignoreCase = true)
                }
            }
        }
    }
}

@Composable
internal fun WorkshopRecipeSheet(
    recipe: WorkshopRecipe, tier: RecipeTier, query: String,
    onQuality: (String) -> Unit, onOriginal: () -> Unit, onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    WikiScrollableSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(12.dp),
                color = colors.surfaceContainer,
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(TacticalStyle.accentBarHeight).background(colors.primary))
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WorkshopTitle(recipe.name)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            WorkshopMicroBadge(
                                text = "需要${recipe.room} ${tier.roomLevel}",
                                highlighted = true
                            )
                            if (recipe.category.isNotBlank()) WorkshopMicroBadge(recipe.category)
                            if (recipe.tiers.size <= 1) WorkshopMicroBadge("品质：${tier.quality}", highlighted = true)
                        }
                    }
                }
            }
            if (recipe.tiers.size > 1) {
                WorkshopChoices(recipe.tiers.map { it.quality }, tier.quality, onSelect = onQuality)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val minTile = 128.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
                if (maxWidth >= minTile * 2 + 8.dp) {
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RecipeMetricTile("制作耗时", tier.duration, Modifier.weight(1f).fillMaxHeight())
                        RecipeMetricTile("加速能量", tier.speedupEnergy.toString(), Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecipeMetricTile("制作耗时", tier.duration, Modifier.fillMaxWidth())
                        RecipeMetricTile("加速能量", tier.speedupEnergy.toString(), Modifier.fillMaxWidth())
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("制作材料", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                when (val cost = tier.cost) {
                    is RecipeCost.Listed -> WorkshopMaterials(cost.materials, query)
                    RecipeCost.Unspecified -> Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = smoothCornerShape(10.dp),
                        color = colors.surfaceContainer,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
                    ) {
                        Text(
                            "材料未列出",
                            Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
            WorkshopSheetActions(onOriginal, onDismiss)
        }
    }
}

@Composable
private fun RecipeMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = smoothCornerShape(10.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.tacticalTabular(),
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
        }
    }
}
