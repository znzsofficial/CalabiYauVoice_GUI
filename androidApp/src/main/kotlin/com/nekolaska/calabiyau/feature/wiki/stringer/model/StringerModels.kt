package com.nekolaska.calabiyau.feature.wiki.stringer.model

data class CardPage(
    val title: String,
    val summary: String,
    val wikiUrl: String,
    val cards: List<ModeCard>
)

data class ModeCard(
    val name: String,
    val category: String,
    val rarity: Int,
    val effect: String,
    val roles: List<String>,
    val imageUrl: String?
)

data class TalentPage(
    val title: String,
    val wikiUrl: String,
    val sections: List<TalentSection>
)

data class TalentSection(
    val title: String,
    val items: List<TalentItem>
)

data class TalentItem(
    val name: String,
    val unlockLevel: String,
    val maxLevel: String,
    val details: List<String>,
    val imageUrl: String?
)
