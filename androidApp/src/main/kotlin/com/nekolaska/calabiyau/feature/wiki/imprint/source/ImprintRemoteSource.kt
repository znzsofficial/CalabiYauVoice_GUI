package com.nekolaska.calabiyau.feature.wiki.imprint.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.fetchWikiHtmlPage
import com.nekolaska.calabiyau.core.wiki.loadCachedWikiHtmlPage

typealias ImprintSourceResult = WikiHtmlPageSourceResult

object ImprintRemoteSource {

    private const val PAGE_NAME = "印迹"
    private const val CACHE_KEY = "imprint_page"

    suspend fun fetchPage(forceRefresh: Boolean = false): ImprintSourceResult? {
        return fetchWikiHtmlPage(
            pageName = PAGE_NAME,
            cacheType = OfflineCache.Type.IMPRINTS,
            cacheKey = CACHE_KEY,
            forceRefresh = forceRefresh
        )
    }

    suspend fun loadCachedPage(): ImprintSourceResult? = loadCachedWikiHtmlPage(
        cacheType = OfflineCache.Type.IMPRINTS,
        cacheKey = CACHE_KEY
    )
}
