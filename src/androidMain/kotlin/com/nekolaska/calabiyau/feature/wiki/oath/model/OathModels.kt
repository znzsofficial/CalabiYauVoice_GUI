package com.nekolaska.calabiyau.feature.wiki.oath.model

data class OathPage(
    val title: String,
    val summary: String,
    val wikiUrl: String,
    val levels: List<OathLevel>,
    val birthdayGifts: List<OathBirthdayGift>,
    val favorGifts: List<OathFavorGift>,
    val bondSections: List<OathBondSection>
)

data class OathLevel(
    val level: String,
    val name: String,
    val requiredFavor: String,
    val totalFavor: String
)

data class OathBirthdayGift(
    val name: String,
    val character: String,
    val description: String,
    val effect: String,
    val imageUrl: String?
)

data class OathFavorGift(
    val name: String,
    val description: String,
    val rarity: String,
    val source: String,
    val favorByCharacter: Map<String, String>,
    val imageUrl: String?
)

data class OathBondSection(
    val character: String,
    val items: List<OathBondItem>
)

data class OathBondItem(
    val name: String,
    val description: String,
    val imageUrl: String?
)
