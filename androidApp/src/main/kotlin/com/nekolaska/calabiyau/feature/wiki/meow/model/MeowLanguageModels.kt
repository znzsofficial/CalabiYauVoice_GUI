package com.nekolaska.calabiyau.feature.wiki.meow.model

const val MEOW_LANGUAGE_PAGE_NAME = "喵言喵语"
const val MEOW_LANGUAGE_PAGE_URL = "https://wiki.biligame.com/klbq/%E5%96%B5%E8%A8%80%E5%96%B5%E8%AF%AD"

data class MeowLanguageSection(
    val title: String,
    val intro: List<String> = emptyList(),
    val groups: List<MeowLanguageGroup> = emptyList()
)

data class MeowLanguageGroup(
    val title: String,
    val lines: List<String>
)
