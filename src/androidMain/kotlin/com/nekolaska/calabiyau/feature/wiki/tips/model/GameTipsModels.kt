package com.nekolaska.calabiyau.feature.wiki.tips.model

const val GAME_TIPS_PAGE_NAME = "游戏Tips"
const val GAME_TIPS_PAGE_URL = "https://wiki.biligame.com/klbq/%E6%B8%B8%E6%88%8FTips"

data class GameTipsSection(
    val title: String,
    val tips: List<String>
)
