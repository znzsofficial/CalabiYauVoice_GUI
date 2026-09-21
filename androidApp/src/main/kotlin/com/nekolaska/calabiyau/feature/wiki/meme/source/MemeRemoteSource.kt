package com.nekolaska.calabiyau.feature.wiki.meme.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.fetchWikiHtmlPage
import com.nekolaska.calabiyau.core.wiki.loadCachedWikiHtmlPage
import com.nekolaska.calabiyau.feature.wiki.meme.model.MEME_PAGE_NAME


object MemeRemoteSource {
    suspend fun fetchPage(forceRefresh: Boolean): WikiHtmlPageSourceResult? {
        return fetchWikiHtmlPage(
            pageName = MEME_PAGE_NAME,
            cacheType = OfflineCache.Type.MEMES,
            cacheKey = "meme_encyclopedia",
            forceRefresh = forceRefresh
        )
    }

    suspend fun loadCachedPage(): WikiHtmlPageSourceResult? {
        return loadCachedWikiHtmlPage(
            cacheType = OfflineCache.Type.MEMES,
            cacheKey = "meme_encyclopedia"
        )
    }
}
