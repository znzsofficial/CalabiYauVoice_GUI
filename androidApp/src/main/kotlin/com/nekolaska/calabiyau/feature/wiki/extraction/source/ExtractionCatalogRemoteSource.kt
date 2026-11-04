package com.nekolaska.calabiyau.feature.wiki.extraction.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.loadCachedWikiHtmlPage
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionCatalogKind
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionCatalogParsers
import data.SharedJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import util.buildParseUrl

object ExtractionCatalogRemoteSource {
    suspend fun fetch(kind: ExtractionCatalogKind, forceRefresh: Boolean): WikiHtmlPageSourceResult? {
        val url = buildParseUrl("https://wiki.biligame.com/klbq/api.php", kind.pageName, "text")
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.ITEMS, key = kind.cacheKey, forceRefresh = forceRefresh,
            validatePayload = { isValidPayload(it, kind) }
        ) { WikiEngine.safeGet(url) } ?: return null
        val html = htmlFromPayload(result.payload) ?: return null
        return WikiHtmlPageSourceResult(html, result.isFromCache, result.ageMs)
    }

    suspend fun readCache(kind: ExtractionCatalogKind) = loadCachedWikiHtmlPage(OfflineCache.Type.ITEMS, kind.cacheKey)

    internal fun isValidPayload(payload: String, kind: ExtractionCatalogKind): Boolean = runCatching {
        val html = htmlFromPayload(payload) ?: return@runCatching false
        ExtractionCatalogParsers.parseResult(html, kind).canCache
    }.getOrDefault(false)

    private fun htmlFromPayload(payload: String): String? {
        val root = runCatching { SharedJson.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        if (root["error"] != null) return null
        val parse = root["parse"] as? JsonObject ?: return null
        val html = (parse["text"] as? JsonObject)?.get("*") as? JsonPrimitive
        return html?.takeIf { it.isString }?.content
    }
}
