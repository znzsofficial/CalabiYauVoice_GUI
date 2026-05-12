package com.nekolaska.calabiyau.feature.wiki.history.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.feature.wiki.history.model.GAME_HISTORY_PAGE_NAME

data class GameHistoryPageSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object GameHistoryRemoteSource {

    suspend fun fetchGameHistoryPage(forceRefresh: Boolean): GameHistoryPageSourceResult? {
        val result = WikiParseSource.fetchHtml(
            pageName = GAME_HISTORY_PAGE_NAME,
            cacheType = OfflineCache.Type.GAME_HISTORY,
            cacheKey = "game_history",
            forceRefresh = forceRefresh
        ) ?: return null
        val html = result.html ?: return null

        return GameHistoryPageSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
