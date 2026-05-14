package com.nekolaska.calabiyau.feature.wiki.achievement.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.feature.wiki.achievement.model.ACHIEVEMENT_PAGE_NAME

data class AchievementSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object AchievementRemoteSource {

    suspend fun fetchPage(forceRefresh: Boolean = false): AchievementSourceResult? {
        val result = WikiParseSource.fetchHtml(
            pageName = ACHIEVEMENT_PAGE_NAME,
            cacheType = OfflineCache.Type.ACHIEVEMENTS,
            cacheKey = "achievement_page",
            forceRefresh = forceRefresh
        ) ?: return null
        val html = result.html ?: return null
        return AchievementSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
