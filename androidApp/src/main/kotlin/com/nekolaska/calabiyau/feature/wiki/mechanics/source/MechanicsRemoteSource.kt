package com.nekolaska.calabiyau.feature.wiki.mechanics.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.mechanics.model.MechanicsKind
import com.nekolaska.calabiyau.feature.wiki.mechanics.parser.MechanicsParser
import data.SharedJson
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import util.buildParseUrl

internal class MechanicsPayloadException(message: String) : IllegalArgumentException(message)

internal object MechanicsRemoteSource {
    suspend fun fetch(kind: MechanicsKind, force: Boolean): WikiHtmlPageSourceResult? = fetchValidated(kind) { validate ->
        OfflineCache.fetchWithCache(OfflineCache.Type.GAME_TIPS, kind.cacheKey, forceRefresh = force, validatePayload = validate) {
            WikiEngine.safeGet(buildParseUrl("https://wiki.biligame.com/klbq/api.php", kind.pageName, "text"))
        }
    }
    suspend fun readCache(kind: MechanicsKind): WikiHtmlPageSourceResult? {
        val entry = OfflineCache.getEntry(OfflineCache.Type.GAME_TIPS, kind.cacheKey) ?: return null
        return html(kind, entry.content)?.let { WikiHtmlPageSourceResult(it, true, entry.ageMs) }
    }
    internal suspend fun fetchValidated(kind: MechanicsKind, load: suspend ((String) -> Boolean) -> OfflineCache.CacheResult?): WikiHtmlPageSourceResult? {
        var rejected: String? = null
        val result = load { payload ->
            val issue = validationIssue(kind, payload)
            if (issue != null) rejected = issue
            issue == null
        }
        if (result == null) {
            rejected?.let { throw MechanicsPayloadException(it) }
            return null
        }
        return html(kind, result.payload)?.let { WikiHtmlPageSourceResult(it, result.isFromCache, result.ageMs) }
    }
    internal fun validationIssue(kind: MechanicsKind, payload: String): String? = try {
        val html = html(kind, payload)
        if (html == null) "${kind.pageName}响应格式异常"
        else MechanicsParser.parse(kind, html).let { if (it.canCache) null else it.issues.take(2).joinToString("；") }
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { "${kind.pageName}解析失败" }

    internal fun html(kind: MechanicsKind, payload: String): String? {
        val root = runCatching { SharedJson.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        if (root["error"] != null) return null
        val parse = root["parse"] as? JsonObject ?: return null
        if ((parse["title"] as? JsonPrimitive)?.content != kind.pageName) return null
        val text = parse["text"]
        return ((text as? JsonPrimitive) ?: (text as? JsonObject)?.get("*") as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}
