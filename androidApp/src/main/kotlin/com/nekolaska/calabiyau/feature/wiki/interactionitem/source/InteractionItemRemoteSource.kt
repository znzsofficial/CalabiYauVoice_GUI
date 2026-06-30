package com.nekolaska.calabiyau.feature.wiki.interactionitem.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.fetchWikiHtmlPage
import com.nekolaska.calabiyau.core.wiki.loadCachedWikiHtmlPage
import com.nekolaska.calabiyau.feature.wiki.interactionitem.model.INTERACTION_ITEM_PAGE_NAME

typealias InteractionItemSourceResult = WikiHtmlPageSourceResult

object InteractionItemRemoteSource {
    private const val CACHE_KEY = "friend_interaction_items"

    suspend fun fetchPage(forceRefresh: Boolean): InteractionItemSourceResult? {
        return fetchWikiHtmlPage(
            pageName = INTERACTION_ITEM_PAGE_NAME,
            cacheType = OfflineCache.Type.ITEMS,
            cacheKey = CACHE_KEY,
            forceRefresh = forceRefresh
        )
    }

    suspend fun loadCachedPage(): InteractionItemSourceResult? {
        return loadCachedWikiHtmlPage(
            cacheType = OfflineCache.Type.ITEMS,
            cacheKey = CACHE_KEY
        )
    }
}
