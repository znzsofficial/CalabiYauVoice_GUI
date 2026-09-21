package com.nekolaska.calabiyau.feature.wiki.oath.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.fetchWikiHtmlPage
import com.nekolaska.calabiyau.core.wiki.loadCachedWikiHtmlPage


object OathRemoteSource {

    private const val PAGE_NAME = "誓约"
    private const val CACHE_KEY = "oath_page"

    suspend fun fetchPage(forceRefresh: Boolean = false): WikiHtmlPageSourceResult? {
        return fetchWikiHtmlPage(
            pageName = PAGE_NAME,
            cacheType = OfflineCache.Type.OATH,
            cacheKey = CACHE_KEY,
            forceRefresh = forceRefresh
        )
    }

    suspend fun loadCachedPage(): WikiHtmlPageSourceResult? = loadCachedWikiHtmlPage(
        cacheType = OfflineCache.Type.OATH,
        cacheKey = CACHE_KEY
    )
}
