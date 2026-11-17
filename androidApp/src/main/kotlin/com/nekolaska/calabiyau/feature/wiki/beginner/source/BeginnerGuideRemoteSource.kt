package com.nekolaska.calabiyau.feature.wiki.beginner.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.beginner.model.BEGINNER_GUIDE_PAGE
import com.nekolaska.calabiyau.feature.wiki.beginner.parser.BeginnerGuideParser
import data.SharedJson
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import util.buildParseUrl

internal class GuidePayloadException(message: String) : IllegalArgumentException(message)

internal object BeginnerGuideRemoteSource {
    const val CACHE_KEY = "beginner_guide_v1"
    private val cacheType = OfflineCache.Type.GAME_TIPS

    suspend fun fetch(forceRefresh: Boolean): WikiHtmlPageSourceResult? = fetchValidated { validate ->
        OfflineCache.fetchWithCache(cacheType, CACHE_KEY, forceRefresh = forceRefresh, validatePayload = validate) {
            WikiEngine.safeGet(buildParseUrl("https://wiki.biligame.com/klbq/api.php", BEGINNER_GUIDE_PAGE, "text"))
        }
    }

    internal suspend fun fetchValidated(load: suspend ((String) -> Boolean) -> OfflineCache.CacheResult?): WikiHtmlPageSourceResult? {
        var rejected: String? = null
        val result = load { payload ->
            val issue = validationIssue(payload)
            if (issue != null) rejected = issue
            issue == null
        }
        if (result == null) {
            rejected?.let { throw GuidePayloadException(it) }
            return null
        }
        return htmlFromPayload(result.payload)?.let { WikiHtmlPageSourceResult(it, result.isFromCache, result.ageMs) }
    }

    suspend fun readCache(): WikiHtmlPageSourceResult? {
        val entry = OfflineCache.getEntry(cacheType, CACHE_KEY) ?: return null
        // API repeats structural validation on reads; old/broken snapshots never become successful pages.
        return htmlFromPayload(entry.content)?.let { WikiHtmlPageSourceResult(it, true, entry.ageMs) }
    }

    internal fun validationIssue(payload: String): String? = try {
        val html = htmlFromPayload(payload)
        if (html == null) "新人指南响应格式异常"
        else BeginnerGuideParser.parse(html).let {
            if (it.canCache) null else "新人指南资料不完整：${it.issues.take(2).joinToString("；")}"
        }
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { "新人指南解析失败" }

    internal fun htmlFromPayload(payload: String): String? {
        val root = runCatching { SharedJson.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        if (root["error"] != null) return null
        val parse = root["parse"] as? JsonObject ?: return null
        if ((parse["title"] as? JsonPrimitive)?.content != BEGINNER_GUIDE_PAGE) return null
        val text = parse["text"]
        return ((text as? JsonPrimitive) ?: (text as? JsonObject)?.get("*") as? JsonPrimitive)
            ?.takeIf { it.isString }?.content
    }
}
