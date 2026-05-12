package com.nekolaska.calabiyau.feature.wiki.tips.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.feature.wiki.tips.model.GAME_TIPS_PAGE_NAME

data class GameTipsPageSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object GameTipsRemoteSource {

    suspend fun fetchPage(forceRefresh: Boolean): GameTipsPageSourceResult? {
        val result = WikiParseSource.fetchHtml(
            pageName = GAME_TIPS_PAGE_NAME,
            cacheType = OfflineCache.Type.GAME_TIPS,
            cacheKey = "game_tips",
            forceRefresh = forceRefresh
        ) ?: return null
        val html = result.html ?: return null

        return GameTipsPageSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
