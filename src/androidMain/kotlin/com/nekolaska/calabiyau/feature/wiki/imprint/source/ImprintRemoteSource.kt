package com.nekolaska.calabiyau.feature.wiki.imprint.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource

data class ImprintSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object ImprintRemoteSource {

    private const val PAGE_NAME = "印迹"
    private const val CACHE_KEY = "imprint_page"

    suspend fun fetchPage(forceRefresh: Boolean = false): ImprintSourceResult? {
        val result = WikiParseSource.fetchHtml(
            pageName = PAGE_NAME,
            cacheType = OfflineCache.Type.IMPRINTS,
            cacheKey = CACHE_KEY,
            forceRefresh = forceRefresh
        ) ?: return null
        val html = result.html ?: return null

        return ImprintSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
