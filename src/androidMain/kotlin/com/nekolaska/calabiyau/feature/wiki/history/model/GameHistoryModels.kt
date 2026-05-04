package com.nekolaska.calabiyau.feature.wiki.history.model

const val GAME_HISTORY_PAGE_NAME = "游戏历史"
const val GAME_HISTORY_PAGE_URL = "https://wiki.biligame.com/klbq/%E6%B8%B8%E6%88%8F%E5%8E%86%E5%8F%B2"

data class GameHistoryEntry(
    val title: String,
    val url: String,
    val imageFileName: String? = null,
    val imageUrl: String? = null
)

data class GameHistorySection(
    val title: String,
    val description: String? = null,
    val entries: List<GameHistoryEntry>
)