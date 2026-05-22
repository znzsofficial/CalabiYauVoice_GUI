package com.nekolaska.calabiyau.feature.wiki.achievement.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.fetchWikiHtmlPage
import com.nekolaska.calabiyau.core.wiki.loadCachedWikiHtmlPage
import com.nekolaska.calabiyau.feature.wiki.achievement.model.ACHIEVEMENT_PAGE_NAME

typealias AchievementSourceResult = WikiHtmlPageSourceResult

object AchievementRemoteSource {

    private const val CACHE_KEY = "achievement_page"

    suspend fun fetchPage(forceRefresh: Boolean = false): AchievementSourceResult? {
        return fetchWikiHtmlPage(
            pageName = ACHIEVEMENT_PAGE_NAME,
            cacheType = OfflineCache.Type.ACHIEVEMENTS,
            cacheKey = CACHE_KEY,
            forceRefresh = forceRefresh
        )
    }

    suspend fun loadCachedPage(): AchievementSourceResult? = loadCachedWikiHtmlPage(
        cacheType = OfflineCache.Type.ACHIEVEMENTS,
        cacheKey = CACHE_KEY
    )
}
