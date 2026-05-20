package com.nekolaska.calabiyau.feature.wiki.imprint.model

data class ImprintPage(
    val title: String,
    val notice: String,
    val wikiUrl: String,
    val sections: List<ImprintSection>
)

data class ImprintSection(
    val character: String,
    val imprints: List<ImprintItem>
)

data class ImprintItem(
    val name: String,
    val quote: String,
    val obtainMethod: String,
    val level: Int?,
    val imageUrl: String?
)
