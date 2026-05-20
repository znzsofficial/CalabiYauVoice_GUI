package com.nekolaska.calabiyau.core.wiki

import com.nekolaska.calabiyau.core.cache.OfflineCache
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

data class WikiParseSourceResult(
    val html: String? = null,
    val wikitext: String? = null,
    val isFromCache: Boolean,
    val ageMs: Long
)

object WikiParseSource {
    private const val API = "https://wiki.biligame.com/klbq/api.php"

    suspend fun fetchHtml(
        pageName: String,
        cacheType: OfflineCache.Type,
        cacheKey: String,
        forceRefresh: Boolean
    ): WikiParseSourceResult? = fetch(pageName, cacheType, cacheKey, "text", forceRefresh)

    suspend fun fetchWikitext(
        pageName: String,
        cacheType: OfflineCache.Type,
        cacheKey: String,
        forceRefresh: Boolean
    ): WikiParseSourceResult? = fetch(pageName, cacheType, cacheKey, "wikitext", forceRefresh)

    suspend fun fetchHtmlAndWikitext(
        pageName: String,
        cacheType: OfflineCache.Type,
        cacheKey: String,
        forceRefresh: Boolean
    ): WikiParseSourceResult? = fetch(pageName, cacheType, cacheKey, "wikitext|text", forceRefresh)

    private suspend fun fetch(
        pageName: String,
        cacheType: OfflineCache.Type,
        cacheKey: String,
        prop: String,
        forceRefresh: Boolean
    ): WikiParseSourceResult? {
        val encoded = URLEncoder.encode(pageName, "UTF-8")
        val url = "$API?action=parse&page=$encoded&prop=$prop&format=json"
        val result = OfflineCache.fetchWithCache(
            type = cacheType,
            key = cacheKey,
            forceRefresh = forceRefresh
        ) { WikiEngine.safeGet(url) } ?: return null

        val parse = SharedJson.parseToJsonElement(result.payload).jsonObject["parse"]?.jsonObject ?: return null
        val html = parse["text"]?.jsonObject?.get("*")?.jsonPrimitive?.content
        val wikitext = parse["wikitext"]?.jsonObject?.get("*")?.jsonPrimitive?.content

        return WikiParseSourceResult(
            html = html,
            wikitext = wikitext,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
