package com.nekolaska.calabiyau.feature.wiki.achievement.model

const val ACHIEVEMENT_PAGE_NAME = "成就"
const val ACHIEVEMENT_PAGE_URL = "https://wiki.biligame.com/klbq/%E6%88%90%E5%B0%B1"

data class AchievementPage(
    val title: String,
    val wikiUrl: String,
    val sections: List<AchievementSection>
)

data class AchievementSection(
    val category: String,
    val achievements: List<AchievementItem>
)

data class AchievementItem(
    val category: String,
    val level: String?,
    val name: String,
    val flavorText: String,
    val condition: String,
    val imageUrl: String?,
    val fileName: String?
)
