package com.nekolaska.calabiyau.feature.wiki.map.model

import com.nekolaska.calabiyau.feature.wiki.game.model.FieldGuideChapter

internal const val RIFT_MAP = "维里昂裂隙"
internal const val RIFT_WIDGET_PAGE = "用户:5205017/StrinovaMapTest"
internal const val RIFT_WIDGET_LABEL = "互动地图（制作者测试页）"

data class MapInfo(
    val name: String,
    val wikiUrl: String,
    val imageUrl: String
)

data class GameModeData(
    val displayName: String,
    val templateName: String,
    val maps: List<MapInfo>,
    val loadError: String? = null
)

data class MapDetail(
    val name: String,
    val description: String,
    val supportedModes: String,
    val platforms: String,
    val terrainMapUrl: String?,
    val galleryUrls: List<String>,
    val updateHistory: List<UpdateEntry>,
    val overviewImageUrl: String? = null,
    val chapters: List<FieldGuideChapter> = emptyList()
)

data class UpdateEntry(
    val date: String,
    val changes: List<String>
)
