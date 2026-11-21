package com.nekolaska.calabiyau.feature.wiki.beginner.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.feature.wiki.beginner.model.BEGINNER_GUIDE_PAGE
import com.nekolaska.calabiyau.feature.wiki.beginner.parser.BeginnerGuideParser
import data.SharedJson
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import util.buildParseUrl

internal class GuidePayloadException(message: String) : IllegalArgumentException(message)

internal object BeginnerGuideRemoteSource {
    const val CACHE_KEY = "beginner_guide_v1"
    private val cacheType = OfflineCache.Type.GAME_TIPS

    suspend fun fetch(forceRefresh: Boolean): OfflineCache.CacheResult? = fetchValidated(network = {
        WikiEngine.safeGet(buildParseUrl("https://wiki.biligame.com/klbq/api.php", BEGINNER_GUIDE_PAGE, "text|revid"))
    }) { validate, network ->
        OfflineCache.fetchWithCache(cacheType, CACHE_KEY, forceRefresh = forceRefresh, validatePayload = validate, networkFetch = network)
    }

    internal suspend fun fetchValidated(
        network: suspend () -> String?,
        load: suspend ((String) -> Boolean, suspend () -> String?) -> OfflineCache.CacheResult?
    ): OfflineCache.CacheResult? {
        var rejected: String? = null
        val result = load({ payload ->
            val issue = validationIssue(payload)
            if (issue != null) rejected = issue
            issue == null
        }, {
            // A new request explicitly asked for revid. Missing metadata cannot downgrade a network
            // response to the compatibility rules for previously persisted text-only envelopes.
            network()?.takeIf { raw ->
                (envelope(raw)?.revision != null).also { if (!it) rejected = "新人指南响应缺少修订信息" }
            }
        })
        if (result == null) {
            rejected?.let { throw GuidePayloadException(it) }
            return null
        }
        return result
    }

    suspend fun readCache(): OfflineCache.CacheResult? {
        val entry = OfflineCache.getEntry(cacheType, CACHE_KEY) ?: return null
        // API repeats structural validation on reads; old/broken snapshots never become successful pages.
        return OfflineCache.CacheResult(entry.content, true, entry.ageMs)
    }

    internal fun validationIssue(payload: String): String? = try {
        val result = parsePayload(payload)
        when { result == null -> "新人指南响应格式异常"; result.canCache -> null
            else -> "新人指南资料不完整：${result.issues.take(2).joinToString("；")}" }
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { "新人指南解析失败" }

    internal fun parsePayload(payload: String) = envelope(payload)?.let { BeginnerGuideParser.parse(it.html, it.revision) }

    private data class Envelope(val html: String, val revision: Long?)

    private fun envelope(payload: String): Envelope? {
        val root = runCatching { SharedJson.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        if (root["error"] != null) return null
        val parse = root["parse"] as? JsonObject ?: return null
        if ((parse["title"] as? JsonPrimitive)?.content != BEGINNER_GUIDE_PAGE) return null
        val text = parse["text"]
        val html = ((text as? JsonPrimitive) ?: (text as? JsonObject)?.get("*") as? JsonPrimitive)
            ?.takeIf { it.isString }?.content ?: return null
        val revision = parse["revid"]?.let { (it as? JsonPrimitive)?.longOrNull?.takeIf { id -> id > 0 } ?: return null }
        return Envelope(html, revision)
    }
}
