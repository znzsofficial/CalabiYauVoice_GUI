package com.nekolaska.calabiyau.feature.wiki.extraction.workshop

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.model.*
import kotlinx.coroutines.launch

@Composable
internal fun WorkshopRecipes(page: WorkshopPage, query: String, acceptsInput: () -> Boolean, onQuery: (String) -> Unit,
                            onNotes: () -> Unit, onRecipe: (RecipeMatch) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val results = remember(page.recipes, query) { page.recipes.searchRecipes(query) }
    fun search(text: String) {
        if (!acceptsInput()) return
        onQuery(text)
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            scope.launch { if (acceptsInput()) listState.scrollToItem(0) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchBar(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), keyword = query,
            onKeywordChange = ::search, onClear = { search("") }, isSearching = false, placeholder = "搜索成品或材料")
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            item("count") { WorkshopListHeader("${results.size} 个配方", "配方说明", onNotes) }
            if (results.isEmpty()) item("empty") { CatalogEmptyResults(message = "未找到相关配方", onClear = { search("") }) }
            items(results, key = { "recipe:${it.recipe.id}" }) { match ->
                val recipe = match.recipe
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "查看配方") { onRecipe(match) }
                    .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RecipeArtwork(recipe.image)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(workshopMatchText(recipe.name, query), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        WorkshopCaption("${recipe.room} / ${recipe.category}")
                        if (match.materialTiers.isEmpty()) WorkshopCaption(recipe.range)
                        else {
                            val emphasis = MaterialTheme.colorScheme.primary
                            Text(buildAnnotatedString {
                                append("使用该材料的品质档：")
                                withStyle(SpanStyle(color = emphasis, fontWeight = FontWeight.SemiBold)) {
                                    append(match.materialTiers.joinToString("、") { it.quality })
                                }
                            }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}

internal val recipeArtworkSize = Modifier.size(width = 64.dp, height = 52.dp)

@Composable
private fun RecipeArtwork(url: String?) {
    Surface(modifier = recipeArtworkSize, shape = AppShapes.chip, color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(contentAlignment = Alignment.Center) {
            var loaded by remember(url) { mutableStateOf(false) }
            if (!loaded) Icon(Icons.Outlined.Inventory2, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp), onLoading = { loaded = false },
                onError = { loaded = false }, onSuccess = { loaded = true })
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
internal fun WorkshopRecipeSheet(recipe: WorkshopRecipe, tier: RecipeTier, query: String,
                                onQuality: (String) -> Unit, onOriginal: () -> Unit, onDismiss: () -> Unit) {
    WikiScrollableSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkshopTitle(recipe.name)
            val accent = MaterialTheme.colorScheme.primary
            Text(buildAnnotatedString {
                append("需要${recipe.room} ")
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(tier.roomLevel) }
                append("  /  ${recipe.category}")
            }, style = MaterialTheme.typography.bodyMedium)
            if (recipe.tiers.size > 1) WorkshopChoices(recipe.tiers.map { it.quality }, tier.quality, onSelect = onQuality)
            else Text("品质：${tier.quality}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Surface(Modifier.fillMaxWidth(), shape = AppShapes.chip, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                FlowRow(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RecipeMetric("制作耗时", tier.duration)
                    RecipeMetric("加速能量", tier.speedupEnergy.toString())
                }
            }
            Text("制作材料", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            when (val cost = tier.cost) {
                is RecipeCost.Listed -> WorkshopMaterials(cost.materials, query)
                RecipeCost.Unspecified -> Text("材料未列出", style = MaterialTheme.typography.bodyMedium)
            }
            WorkshopSheetActions(onOriginal, onDismiss)
        }
    }
}

@Composable
private fun RecipeMetric(label: String, value: String) {
    Text(buildAnnotatedString {
        append("$label："); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value) }
    }, style = MaterialTheme.typography.bodyMedium)
}
