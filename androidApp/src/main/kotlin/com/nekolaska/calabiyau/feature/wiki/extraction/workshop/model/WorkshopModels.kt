package com.nekolaska.calabiyau.feature.wiki.extraction.workshop.model

import com.nekolaska.calabiyau.feature.wiki.extraction.model.EXTRACTION_MODE_PAGE

internal const val WORKSHOP_PAGE = "$EXTRACTION_MODE_PAGE/工坊"

internal data class WorkshopMaterial(val name: String, val quantity: Int)

internal sealed interface RecipeCost {
    data class Listed(val materials: List<WorkshopMaterial>) : RecipeCost
    data object Unspecified : RecipeCost { const val marker = "#0×0" }
}

internal data class RecipeTier(
    val quality: String, val roomLevel: String, val cost: RecipeCost,
    val duration: String, val speedupEnergy: Int
) {
    val materials get() = (cost as? RecipeCost.Listed)?.materials.orEmpty()
}

internal data class WorkshopRecipe(
    val room: String, val category: String, val name: String, val range: String,
    val image: String?, val tiers: List<RecipeTier>
) {
    val id = "$room/$category/$name"
}

internal data class RecipeMatch(val recipe: WorkshopRecipe, val materialTiers: List<RecipeTier>)

internal fun List<WorkshopRecipe>.searchRecipes(query: String): List<RecipeMatch> {
    val term = query.trim()
    return mapNotNull { recipe ->
        val matches = if (term.isEmpty()) emptyList() else recipe.tiers.filter { tier ->
            tier.materials.any { it.name.contains(term, ignoreCase = true) }
        }
        if (recipe.name.contains(term, ignoreCase = true) || matches.isNotEmpty()) RecipeMatch(recipe, matches) else null
    }
}

internal data class WorkshopCard(val name: String, val badge: String, val subtitle: String, val detail: String)

internal data class RoomUpgrade(
    val level: String, val effect: String, val stats: String,
    val materials: List<WorkshopMaterial>, val costNote: String
)

internal data class WorkshopRoom(val overview: WorkshopCard, val upgrades: List<RoomUpgrade>)
internal data class WorkshopBuff(val name: String, val effect: String, val rooms: String)
internal data class DecorationGroup(val title: String, val notes: String, val cards: List<WorkshopCard>)

internal data class WorkshopPage(
    val intro: String = "", val recipeNotes: String = "", val roomNotes: String = "", val upgradeNotes: String = "",
    val staffNotes: String = "", val recipes: List<WorkshopRecipe> = emptyList(),
    val rooms: List<WorkshopRoom> = emptyList(), val staff: List<WorkshopCard> = emptyList(),
    val buffs: List<WorkshopBuff> = emptyList(), val decorations: List<DecorationGroup> = emptyList()
)
