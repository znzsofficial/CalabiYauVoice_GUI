package com.nekolaska.calabiyau.feature.wiki.playerlevel.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.feature.wiki.playerlevel.model.PLAYER_LEVEL_PAGE_NAME

data class PlayerLevelSourceResult(
    val html: String,
    val wikitext: String?,
    val isFromCache: Boolean,
    val ageMs: Long
)

object PlayerLevelRemoteSource {

    suspend fun fetchPage(forceRefresh: Boolean = false): PlayerLevelSourceResult? {
        val result = WikiParseSource.fetchHtmlAndWikitext(
            pageName = PLAYER_LEVEL_PAGE_NAME,
            cacheType = OfflineCache.Type.PLAYER_LEVELS,
            cacheKey = "player_level_page_v2",
            forceRefresh = forceRefresh
        ) ?: return null
        val html = result.html ?: return null
        return PlayerLevelSourceResult(
            html = html,
            wikitext = result.wikitext,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
