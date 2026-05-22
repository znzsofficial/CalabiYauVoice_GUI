package com.nekolaska.calabiyau.core.wiki

import com.nekolaska.calabiyau.core.cache.OfflineCache
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class WikiHtmlPageSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

suspend fun fetchWikiHtmlPage(
    pageName: String,
    cacheType: OfflineCache.Type,
    cacheKey: String,
    forceRefresh: Boolean
): WikiHtmlPageSourceResult? {
    val result = WikiParseSource.fetchHtml(
        pageName = pageName,
        cacheType = cacheType,
        cacheKey = cacheKey,
        forceRefresh = forceRefresh
    ) ?: return null
    val html = result.html ?: return null

    return WikiHtmlPageSourceResult(
        html = html,
        isFromCache = result.isFromCache,
        ageMs = result.ageMs
    )
}

suspend fun loadCachedWikiHtmlPage(
    cacheType: OfflineCache.Type,
    cacheKey: String
): WikiHtmlPageSourceResult? {
    val entry = OfflineCache.getEntry(cacheType, cacheKey) ?: return null
    val parse = SharedJson.parseToJsonElement(entry.content).jsonObject["parse"]?.jsonObject ?: return null
    val html = parse["text"]?.jsonObject?.get("*")?.jsonPrimitive?.content ?: return null

    return WikiHtmlPageSourceResult(
        html = html,
        isFromCache = true,
        ageMs = entry.ageMs
    )
}
