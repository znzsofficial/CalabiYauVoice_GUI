package com.nekolaska.calabiyau.feature.wiki.oath.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource

data class OathSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object OathRemoteSource {

    private const val PAGE_NAME = "誓约"
    private const val CACHE_KEY = "oath_page"

    suspend fun fetchPage(forceRefresh: Boolean = false): OathSourceResult? {
        val result = WikiParseSource.fetchHtml(
            pageName = PAGE_NAME,
            cacheType = OfflineCache.Type.OATH,
            cacheKey = CACHE_KEY,
            forceRefresh = forceRefresh
        ) ?: return null
        val html = result.html ?: return null

        return OathSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
