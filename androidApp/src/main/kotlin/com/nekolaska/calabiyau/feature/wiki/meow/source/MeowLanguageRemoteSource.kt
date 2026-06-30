package com.nekolaska.calabiyau.feature.wiki.meow.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.core.wiki.loadCachedWikiHtmlPage
import com.nekolaska.calabiyau.feature.wiki.meow.model.MEOW_LANGUAGE_PAGE_NAME

data class MeowLanguagePageSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object MeowLanguageRemoteSource {

    suspend fun fetchPage(forceRefresh: Boolean): MeowLanguagePageSourceResult? {
        val result = WikiParseSource.fetchHtml(
            pageName = MEOW_LANGUAGE_PAGE_NAME,
            cacheType = OfflineCache.Type.MEOW_LANGUAGE,
            cacheKey = "meow_language",
            forceRefresh = forceRefresh
        ) ?: return null
        val html = result.html ?: return null

        return MeowLanguagePageSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }

    suspend fun loadCachedPage(): MeowLanguagePageSourceResult? {
        val result = loadCachedWikiHtmlPage(
            cacheType = OfflineCache.Type.MEOW_LANGUAGE,
            cacheKey = "meow_language"
        ) ?: return null

        return MeowLanguagePageSourceResult(
            html = result.html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
