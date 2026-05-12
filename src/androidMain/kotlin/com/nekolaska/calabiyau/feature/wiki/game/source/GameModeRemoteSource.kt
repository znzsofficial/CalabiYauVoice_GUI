package com.nekolaska.calabiyau.feature.wiki.game.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource

data class GameModeSourceResult(
    val wikitext: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object GameModeRemoteSource {

    suspend fun fetchModeMapMappingWikitext(forceRefresh: Boolean): String? {
        return WikiParseSource.fetchWikitext(
            pageName = "模板:卡拉彼丘",
            cacheType = OfflineCache.Type.GAME_MODES,
            cacheKey = "mode_map_mapping",
            forceRefresh = forceRefresh
        )?.wikitext
    }

    suspend fun fetchModeWikitext(pageName: String, forceRefresh: Boolean): GameModeSourceResult? {
        val result = WikiParseSource.fetchWikitext(
            pageName = pageName,
            cacheType = OfflineCache.Type.GAME_MODES,
            cacheKey = "mode_$pageName",
            forceRefresh = forceRefresh
        ) ?: return null
        val wikitext = result.wikitext ?: return null

        return GameModeSourceResult(
            wikitext = wikitext,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
