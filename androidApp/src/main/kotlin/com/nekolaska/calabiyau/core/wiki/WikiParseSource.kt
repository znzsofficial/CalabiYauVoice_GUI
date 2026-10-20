package com.nekolaska.calabiyau.core.wiki

import com.nekolaska.calabiyau.core.cache.OfflineCache
import data.SharedJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import util.buildParseUrl

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

    suspend fun loadCachedWikitext(
        cacheType: OfflineCache.Type,
        cacheKey: String
    ): WikiParseSourceResult? = loadCached(cacheType, cacheKey)?.takeIf { it.wikitext != null }

    suspend fun loadCachedHtml(
        cacheType: OfflineCache.Type,
        cacheKey: String
    ): WikiParseSourceResult? = loadCached(cacheType, cacheKey)?.takeIf { it.html != null }

    suspend fun loadCachedHtmlAndWikitext(
        cacheType: OfflineCache.Type,
        cacheKey: String
    ): WikiParseSourceResult? = loadCached(cacheType, cacheKey)?.takeIf { it.html != null && it.wikitext != null }

    private suspend fun fetch(
        pageName: String,
        cacheType: OfflineCache.Type,
        cacheKey: String,
        prop: String,
        forceRefresh: Boolean
    ): WikiParseSourceResult? {
        val url = buildParseUrl(API, pageName, prop)
        val result = OfflineCache.fetchWithCache(
            type = cacheType,
            key = cacheKey,
            forceRefresh = forceRefresh,
            validatePayload = { isValidParsePayload(it, prop) }
        ) { WikiEngine.safeGet(url) } ?: return null

        return parsePayload(result.payload, result.isFromCache, result.ageMs)
    }

    private suspend fun loadCached(
        cacheType: OfflineCache.Type,
        cacheKey: String
    ): WikiParseSourceResult? {
        val entry = OfflineCache.getEntry(cacheType, cacheKey) ?: return null
        return parsePayload(entry.content, isFromCache = true, ageMs = entry.ageMs)
    }

    internal fun isValidParsePayload(payload: String, prop: String): Boolean = runCatching {
        val result = parsePayload(payload, false, 0) ?: return@runCatching false
        ("text" !in prop.split('|') || result.html != null) &&
            ("wikitext" !in prop.split('|') || result.wikitext != null)
    }.getOrDefault(false)

    private fun parsePayload(
        payload: String,
        isFromCache: Boolean,
        ageMs: Long
    ): WikiParseSourceResult? {
        val root = runCatching { SharedJson.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        if (root["error"] != null) return null
        val parse = root["parse"] as? JsonObject ?: return null
        fun text(key: String): String? = ((parse[key] as? JsonObject)?.get("*") as? JsonPrimitive)
            ?.takeIf { it.isString }?.content
        val html = text("text")
        val wikitext = text("wikitext")
        if (html == null && wikitext == null) return null

        return WikiParseSourceResult(
            html = html,
            wikitext = wikitext,
            isFromCache = isFromCache,
            ageMs = ageMs
        )
    }
}
