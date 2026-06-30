package com.nekolaska.calabiyau.feature.wiki.bgm.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.core.wiki.loadCachedWikiHtmlPage
import com.nekolaska.calabiyau.feature.wiki.bgm.model.BGM_PAGE_NAME

data class BgmPageSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object BgmRemoteSource {
    suspend fun fetchPage(forceRefresh: Boolean): BgmPageSourceResult? {
        val result = WikiParseSource.fetchHtml(
            pageName = BGM_PAGE_NAME,
            cacheType = OfflineCache.Type.BGM,
            cacheKey = "bgm_page",
            forceRefresh = forceRefresh
        ) ?: return null
        val html = result.html ?: return null

        return BgmPageSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }

    suspend fun loadCachedPage(): BgmPageSourceResult? {
        val result = loadCachedWikiHtmlPage(
            cacheType = OfflineCache.Type.BGM,
            cacheKey = "bgm_page"
        ) ?: return null

        return BgmPageSourceResult(
            html = result.html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
