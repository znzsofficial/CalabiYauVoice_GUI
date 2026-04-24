package com.nekolaska.calabiyau.feature.wiki.bio.model

data class CardPageData(
    val pcCards: List<PcCard>,
    val mobileCards: List<MobileCard>,
    val decks: List<SharedDeck>,
    val pcWikiUrl: String,
    val mobileWikiUrl: String,
    val deckWikiUrl: String,
    val refreshProbabilityWikiUrl: String
)

data class CardRefreshProbability(
    val stage1: String,
    val stage2: String,
    val stage3: String,
    val stage4: String
) {
    val summary: String
        get() = listOf(stage1, stage2, stage3, stage4)
            .mapIndexed { index, value -> "${index + 1}阶段 $value" }
            .joinToString(" · ")
}

data class PcCard(
    val name: String,
    val faction: String,
    val rarity: Int,
    val category: String,
    val defaultTag: String,
    val acquireType: String,
    val releaseDate: String,
    val maxLevel: String,
    val effect: String,
    val roles: List<String>,
    val imageUrl: String?,
    val refreshProbability: CardRefreshProbability?
)

data class MobileCard(
    val name: String,
    val faction: String,
    val category: String,
    val rarity: Int,
    val maxLevel: String,
    val effect: String,
    val imageUrl: String?
)

data class SharedDeck(
    val faction: String,
    val title: String,
    val author: String,
    val shareId: String,
    val intro: String,
    val cardNames: List<String>
)
