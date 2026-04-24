package com.nekolaska.calabiyau.feature.wiki.game.model

data class ModeEntry(val displayName: String, val pageName: String)

data class GameModeDetail(
    val name: String,
    val summary: String,
    val winCondition: String,
    val settings: String,
    val maps: List<String>,
    val wikiUrl: String
)
