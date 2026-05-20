package com.nekolaska.calabiyau.feature.wiki.meme.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.feature.wiki.meme.model.MEME_PAGE_NAME

data class MemePageSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object MemeRemoteSource {
    suspend fun fetchPage(forceRefresh: Boolean): MemePageSourceResult? {
        val result = WikiParseSource.fetchHtml(
            pageName = MEME_PAGE_NAME,
            cacheType = OfflineCache.Type.MEMES,
            cacheKey = "meme_encyclopedia",
            forceRefresh = forceRefresh
        ) ?: return null
        val html = result.html ?: return null

        return MemePageSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
