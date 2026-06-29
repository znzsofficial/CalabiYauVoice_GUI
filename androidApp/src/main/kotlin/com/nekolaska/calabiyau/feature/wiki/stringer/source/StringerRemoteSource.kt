package com.nekolaska.calabiyau.feature.wiki.stringer.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.loadCachedWikiHtmlPage

data class StringerPageSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object StringerRemoteSource {

    suspend fun fetchPageHtml(
        pageName: String,
        cacheKey: String,
        forceRefresh: Boolean
    ): StringerPageSourceResult? {
        val result = WikiParseSource.fetchHtml(
            pageName = pageName,
            cacheType = OfflineCache.Type.GAME_MODES,
            cacheKey = cacheKey,
            forceRefresh = forceRefresh
        ) ?: return null
        val html = result.html ?: return null

        return StringerPageSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }

    suspend fun loadCachedPageHtml(pageName: String, cacheKey: String): StringerPageSourceResult? {
        val result: WikiHtmlPageSourceResult = loadCachedWikiHtmlPage(
            cacheType = OfflineCache.Type.GAME_MODES,
            cacheKey = cacheKey
        ) ?: return null

        return StringerPageSourceResult(
            html = result.html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
