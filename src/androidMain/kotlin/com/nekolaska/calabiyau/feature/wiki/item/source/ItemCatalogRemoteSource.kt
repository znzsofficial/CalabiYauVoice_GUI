package com.nekolaska.calabiyau.feature.wiki.item.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ItemCatalogSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object ItemCatalogRemoteSource {

    private const val PAGE_NAME = "功能道具筛选表"
    private const val CACHE_KEY = "item_catalog"

    suspend fun fetchItems(forceRefresh: Boolean = false): ItemCatalogSourceResult? {
        val result = WikiParseSource.fetchHtml(
            pageName = PAGE_NAME,
            cacheType = OfflineCache.Type.ITEMS,
            cacheKey = CACHE_KEY,
            forceRefresh = forceRefresh
        ) ?: return null
        val html = result.html ?: return null
        return ItemCatalogSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }

    suspend fun loadFromCache(): ItemCatalogSourceResult? {
        val entry = OfflineCache.getEntry(OfflineCache.Type.ITEMS, CACHE_KEY) ?: return null
        return extractResult(entry.content, isFromCache = true, ageMs = entry.ageMs)
    }

    private fun extractResult(
        jsonBody: String,
        isFromCache: Boolean,
        ageMs: Long
    ): ItemCatalogSourceResult? {
        val json = SharedJson.parseToJsonElement(jsonBody).jsonObject
        val html = json["parse"]?.jsonObject?.get("text")
            ?.jsonObject?.get("*")?.jsonPrimitive?.content
            ?: return null

        return ItemCatalogSourceResult(
            html = html,
            isFromCache = isFromCache,
            ageMs = ageMs
        )
    }
}
