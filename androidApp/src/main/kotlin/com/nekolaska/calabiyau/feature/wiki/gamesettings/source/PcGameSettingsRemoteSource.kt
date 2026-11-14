package com.nekolaska.calabiyau.feature.wiki.gamesettings.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PC_GAME_SETTINGS_PAGE_NAME
import com.nekolaska.calabiyau.feature.wiki.gamesettings.parser.PcGameSettingsParser
import data.SharedJson
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import util.buildParseUrl

internal class PcSettingsPayloadException(message: String) : IllegalArgumentException(message)

object PcGameSettingsRemoteSource {
    internal const val CACHE_KEY = "pc_game_settings_v1"
    // A separate key in the existing guide cache avoids a global cache enum change.
    private val cacheType = OfflineCache.Type.GAME_TIPS

    suspend fun fetch(forceRefresh: Boolean): WikiHtmlPageSourceResult? = fetchValidated { validate ->
        OfflineCache.fetchWithCache(
            type = cacheType, key = CACHE_KEY, forceRefresh = forceRefresh,
            validatePayload = validate
        ) {
            WikiEngine.safeGet(buildParseUrl("https://wiki.biligame.com/klbq/api.php", PC_GAME_SETTINGS_PAGE_NAME, "text"))
        }
    }

    /** Keep validation errors request-local while still allowing the cache layer to return a good fallback. */
    internal suspend fun fetchValidated(
        load: suspend ((String) -> Boolean) -> OfflineCache.CacheResult?
    ): WikiHtmlPageSourceResult? {
        var rejected: String? = null
        val result = load { payload ->
            val issue = validationIssue(payload)
            if (issue != null) rejected = issue
            issue == null
        }
        if (result == null) {
            rejected?.let { throw PcSettingsPayloadException(it) }
            return null
        }
        return htmlFromPayload(result.payload)?.let { WikiHtmlPageSourceResult(it, result.isFromCache, result.ageMs) }
    }

    suspend fun readCache(): WikiHtmlPageSourceResult? {
        val entry = OfflineCache.getEntry(cacheType, CACHE_KEY) ?: return null
        return htmlFromPayload(entry.content)?.let { WikiHtmlPageSourceResult(it, true, entry.ageMs) }
    }

    internal fun isValidPayload(payload: String): Boolean = validationIssue(payload) == null

    private fun validationIssue(payload: String): String? = try {
        val html = htmlFromPayload(payload)
        if (html == null) "PC游戏设置响应格式异常"
        else PcGameSettingsParser.parse(html).let { parsed ->
            if (parsed.canCache) null else "PC游戏设置页面结构不完整：${parsed.issues.take(2).joinToString("；")}"
        }
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { "PC游戏设置页面解析失败" }

    internal fun htmlFromPayload(payload: String): String? {
        val root = runCatching { SharedJson.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        if (root["error"] != null) return null
        val parse = root["parse"] as? JsonObject ?: return null
        val title = (parse["title"] as? JsonPrimitive)?.content
        if (title != null && title != PC_GAME_SETTINGS_PAGE_NAME) return null
        return ((parse["text"] as? JsonObject)?.get("*") as? JsonPrimitive)
            ?.takeIf { it.isString }?.content
    }
}
