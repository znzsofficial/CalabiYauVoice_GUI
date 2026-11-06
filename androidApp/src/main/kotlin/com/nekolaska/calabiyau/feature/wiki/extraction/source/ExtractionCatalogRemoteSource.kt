package com.nekolaska.calabiyau.feature.wiki.extraction.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.loadCachedWikiHtmlPage
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionCatalogKind
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionCatalogParsers
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionContainerParser
import com.nekolaska.calabiyau.feature.wiki.extraction.model.EXTRACTION_MODE_PAGE
import data.SharedJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import util.buildParseUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.os.SystemClock

object ExtractionCatalogRemoteSource {
    const val CONTAINERS_PAGE = "$EXTRACTION_MODE_PAGE/掉落表格"
    internal const val CONTAINERS_CACHE_KEY = "extraction_drop_containers_v1"
    private val requestMutex = Mutex()
    private var nextRequestAt = 0L

    // Serialize this feature's secondary requests and respect EdgeOne's cooling interval on failures too.
    private suspend fun get(url: String): String? = requestMutex.withLock {
        delay((nextRequestAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        try { WikiEngine.safeGet(url) }
        finally { nextRequestAt = SystemClock.elapsedRealtime() + 6_500L }
    }

    suspend fun fetch(kind: ExtractionCatalogKind, forceRefresh: Boolean): WikiHtmlPageSourceResult? {
        val url = buildParseUrl("https://wiki.biligame.com/klbq/api.php", kind.pageName, "text")
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.ITEMS, key = kind.cacheKey, forceRefresh = forceRefresh,
            validatePayload = { isValidPayload(it, kind) }
        ) { get(url) } ?: return null
        val html = htmlFromPayload(result.payload) ?: return null
        return WikiHtmlPageSourceResult(html, result.isFromCache, result.ageMs)
    }

    suspend fun readCache(kind: ExtractionCatalogKind) = loadCachedWikiHtmlPage(OfflineCache.Type.ITEMS, kind.cacheKey)

    suspend fun fetchContainers(forceRefresh: Boolean): WikiHtmlPageSourceResult? {
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.ITEMS, key = CONTAINERS_CACHE_KEY, forceRefresh = forceRefresh,
            validatePayload = ::isValidContainersPayload
        ) { get(buildParseUrl("https://wiki.biligame.com/klbq/api.php", CONTAINERS_PAGE, "text")) } ?: return null
        return htmlFromPayload(result.payload)?.let { WikiHtmlPageSourceResult(it, result.isFromCache, result.ageMs) }
    }

    suspend fun readContainersCache() = loadCachedWikiHtmlPage(OfflineCache.Type.ITEMS, CONTAINERS_CACHE_KEY)

    internal fun isValidContainersPayload(payload: String): Boolean = runCatching {
        htmlFromPayload(payload)?.let { ExtractionContainerParser.parseResult(it).canCache } == true
    }.getOrDefault(false)

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
