package com.nekolaska.calabiyau.feature.wiki.item.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.fetchWikiHtmlPage
import com.nekolaska.calabiyau.core.wiki.loadCachedWikiHtmlPage
import com.nekolaska.calabiyau.feature.wiki.item.model.ItemCatalogPlatform

object ItemCatalogRemoteSource {
    suspend fun fetchItems(platform: ItemCatalogPlatform, forceRefresh: Boolean = false) =
        fetchWikiHtmlPage(platform.pageName, OfflineCache.Type.ITEMS, platform.cacheKey, forceRefresh)

    suspend fun loadFromCache(platform: ItemCatalogPlatform) =
        loadCachedWikiHtmlPage(OfflineCache.Type.ITEMS, platform.cacheKey)
}
