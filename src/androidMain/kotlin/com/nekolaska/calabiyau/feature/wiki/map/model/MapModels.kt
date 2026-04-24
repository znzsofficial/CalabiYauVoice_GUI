package com.nekolaska.calabiyau.feature.wiki.map.model

data class MapInfo(
    val name: String,
    val wikiUrl: String,
    val imageUrl: String
)

data class GameModeData(
    val displayName: String,
    val templateName: String,
    val maps: List<MapInfo>
)

data class MapDetail(
    val name: String,
    val description: String,
    val supportedModes: String,
    val platforms: String,
    val terrainMapUrl: String?,
    val galleryUrls: List<String>,
    val updateHistory: List<UpdateEntry>
)

data class UpdateEntry(
    val date: String,
    val changes: List<String>
)
