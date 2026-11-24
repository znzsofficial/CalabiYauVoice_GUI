package com.nekolaska.calabiyau.core.wiki

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.cache.fetchValidatedCachePayload
import com.nekolaska.calabiyau.core.network.NetworkMonitor
import data.SharedJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import util.buildParseUrl

data class WikiParseSourceResult(
    val html: String? = null,
    val wikitext: String? = null,
    val isFromCache: Boolean,
    val ageMs: Long
)

internal class UnusableWikiHtml(page: String) : IllegalStateException("${page}页面内容不完整")

internal data class WikiArticleRequest(
    val page: String, val type: OfflineCache.Type, val key: String, val force: Boolean, val cacheOnly: Boolean
)
internal data class WikiArticleSnapshot<T>(val value: T, val isFromCache: Boolean, val ageMs: Long)
internal typealias WikiArticleLoader = suspend (WikiArticleRequest, (raw: String, fromDisk: Boolean) -> Boolean) -> OfflineCache.CacheResult?

object WikiParseSource {
    private const val API = "https://wiki.biligame.com/klbq/api.php"

    /** These readers need two views of one revision. Only disk may contain their older single-view format. */
    internal suspend fun <T : Any> readArticle(
        request: WikiArticleRequest, load: WikiArticleLoader,
        parse: (wikitext: String?, html: String?) -> T?
    ): WikiArticleSnapshot<T>? {
        var memo: Pair<String, T>? = null
        fun decode(raw: String, disk: Boolean): T? {
            val source = parsePayload(raw, disk, 0, expectedTitle = request.page) ?: return null
            if (!disk && (source.html.isNullOrBlank() || source.wikitext.isNullOrBlank())) return null
            return try { parse(source.wikitext, source.html) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
        }
        val result = load(request) { raw, disk ->
            decode(raw, disk)?.also { memo = raw to it } != null
        } ?: return null
        // A single-flight follower has not necessarily seen the winning response.
        val value = memo?.takeIf { it.first == result.payload }?.second ?: decode(result.payload, result.isFromCache)
            ?: throw UnusableWikiHtml(request.page)
        return WikiArticleSnapshot(value, result.isFromCache, result.ageMs)
    }

    internal suspend fun loadArticle(request: WikiArticleRequest, valid: (String, Boolean) -> Boolean): OfflineCache.CacheResult? {
        val (page, type, key, force, cacheOnly) = request
        suspend fun disk() = OfflineCache.getEntry(type, key)?.let { OfflineCache.CacheResult(it.content, true, it.ageMs) }
        if (cacheOnly) return disk()?.also { if (!valid(it.payload, true)) throw UnusableWikiHtml(page) }
        return OfflineCache.fetchWithPolicy(type, key, force, { valid(it, true) }) { ticket ->
            val route = OfflineCache.defaultRoute()
            fetchArticlePayload(page, valid, route == NetworkMonitor.Route.VALIDATED, route == NetworkMonitor.Route.UNVALIDATED,
                network = { WikiEngine.safeGet(buildParseUrl(API, page, "wikitext|text")) }, read = ::disk,
                publish = { OfflineCache.put(ticket, it) })
        }
    }

    internal suspend fun fetchArticlePayload(
        page: String, valid: (String, Boolean) -> Boolean, online: Boolean, tryNetworkOnMiss: Boolean,
        network: suspend () -> String?, read: suspend () -> OfflineCache.CacheResult?, publish: suspend (String) -> Unit
    ): OfflineCache.CacheResult? {
        var rejected = false
        fun accepted(raw: String, disk: Boolean) = valid(raw, disk).also { if (!it) rejected = true }
        return fetchValidatedCachePayload(online,
            network = { network()?.takeIf { accepted(it, false) } },
            read = { read()?.takeIf { accepted(it.payload, true) } },
            publish = { currentCoroutineContext().ensureActive(); publish(it) }, validate = { true }, tryNetworkOnMiss = tryNetworkOnMiss
        ) ?: if (rejected) throw UnusableWikiHtml(page) else null
    }

    suspend fun fetchHtml(
        pageName: String,
        cacheType: OfflineCache.Type,
        cacheKey: String,
        forceRefresh: Boolean,
        acceptHtml: ((String) -> Boolean)? = null
    ): WikiParseSourceResult? = fetch(pageName, cacheType, cacheKey, "text", forceRefresh, acceptHtml)

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
        forceRefresh: Boolean,
        acceptHtml: ((String) -> Boolean)? = null
    ): WikiParseSourceResult? {
        val url = buildParseUrl(API, pageName, prop)
        var rejected = false
        val result = OfflineCache.fetchWithCache(
            type = cacheType,
            key = cacheKey,
            forceRefresh = forceRefresh,
            validatePayload = { payload ->
                if (acceptHtml == null) isValidParsePayload(payload, prop)
                else (parsePayload(payload, false, 0)?.html?.let(acceptHtml) == true).also { if (!it) rejected = true }
            }
        ) { WikiEngine.safeGet(url) } ?: if (rejected) throw UnusableWikiHtml(pageName) else return null

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

    internal fun validDetail(payload: String, usable: (String, String) -> Boolean): Boolean {
        val parsed = parsePayload(payload, false, 0) ?: return false
        return usable(parsed.wikitext ?: return false, parsed.html ?: return false)
    }

    private fun parsePayload(
        payload: String,
        isFromCache: Boolean,
        ageMs: Long,
        expectedTitle: String? = null
    ): WikiParseSourceResult? {
        val root = runCatching { SharedJson.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        if (root["error"] != null) return null
        val parse = root["parse"] as? JsonObject ?: return null
        if (expectedTitle != null && (parse["title"] as? JsonPrimitive)?.content != expectedTitle) return null
        fun text(key: String): String? = ((parse[key] as? JsonObject)?.get("*") as? JsonPrimitive)
            ?.takeIf { it.isString }?.content
        val html = text("text")
        val wikitext = text("wikitext")
        // A present but malformed property is never a legacy single-view response.
        if (expectedTitle != null && (("text" in parse && html == null) || ("wikitext" in parse && wikitext == null))) return null
        if (html == null && wikitext == null) return null

        return WikiParseSourceResult(
            html = html,
            wikitext = wikitext,
            isFromCache = isFromCache,
            ageMs = ageMs
        )
    }
}
