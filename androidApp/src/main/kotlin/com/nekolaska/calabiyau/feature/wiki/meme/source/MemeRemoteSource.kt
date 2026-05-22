package com.nekolaska.calabiyau.feature.wiki.meme.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.fetchWikiHtmlPage
import com.nekolaska.calabiyau.feature.wiki.meme.model.MEME_PAGE_NAME

typealias MemePageSourceResult = WikiHtmlPageSourceResult

object MemeRemoteSource {
    suspend fun fetchPage(forceRefresh: Boolean): MemePageSourceResult? {
        return fetchWikiHtmlPage(
            pageName = MEME_PAGE_NAME,
            cacheType = OfflineCache.Type.MEMES,
            cacheKey = "meme_encyclopedia",
            forceRefresh = forceRefresh
        )
    }
}
