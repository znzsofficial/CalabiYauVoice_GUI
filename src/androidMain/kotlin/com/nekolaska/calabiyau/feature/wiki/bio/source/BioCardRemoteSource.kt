package com.nekolaska.calabiyau.feature.wiki.bio.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import java.net.URLEncoder

data class BioCardPageSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object BioCardRemoteSource {

    private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"

    suspend fun fetchPageHtml(
        pageName: String,
        cacheKey: String,
        forceRefresh: Boolean
    ): BioCardPageSourceResult? {
        val result = WikiParseSource.fetchHtml(
            pageName = pageName,
            cacheType = OfflineCache.Type.BIO_CARDS,
            cacheKey = cacheKey,
            forceRefresh = forceRefresh
        ) ?: return null
        val html = result.html ?: return null
        return BioCardPageSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }

    fun pageUrl(pageName: String): String {
        return WIKI_BASE + URLEncoder.encode(pageName, "UTF-8").replace("+", "%20")
    }
}
