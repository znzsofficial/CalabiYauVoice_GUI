package com.nekolaska.calabiyau.core.wiki

import com.nekolaska.calabiyau.core.cache.OfflineCache

data class WikiHtmlPageSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

suspend fun fetchWikiHtmlPage(
    pageName: String,
    cacheType: OfflineCache.Type,
    cacheKey: String,
    forceRefresh: Boolean
): WikiHtmlPageSourceResult? {
    val result = WikiParseSource.fetchHtml(
        pageName = pageName,
        cacheType = cacheType,
        cacheKey = cacheKey,
        forceRefresh = forceRefresh
    ) ?: return null
    val html = result.html ?: return null

    return WikiHtmlPageSourceResult(
        html = html,
        isFromCache = result.isFromCache,
        ageMs = result.ageMs
    )
}

suspend fun loadCachedWikiHtmlPage(
    cacheType: OfflineCache.Type,
    cacheKey: String
): WikiHtmlPageSourceResult? {
    val result = WikiParseSource.loadCachedHtml(cacheType, cacheKey) ?: return null

    return WikiHtmlPageSourceResult(
        html = result.html ?: return null,
        isFromCache = true,
        ageMs = result.ageMs
    )
}
