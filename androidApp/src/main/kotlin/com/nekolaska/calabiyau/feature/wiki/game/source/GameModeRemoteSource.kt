package com.nekolaska.calabiyau.feature.wiki.game.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.feature.wiki.game.parser.GameModeParsers
import data.SharedJson
import kotlinx.serialization.json.*
import util.buildParseUrl

data class GameModeSourceResult(
    val wikitext: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object GameModeRemoteSource {

    suspend fun fetchModeMapMappingWikitext(forceRefresh: Boolean): String? {
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.GAME_MODES, key = "mode_map_mapping", forceRefresh = forceRefresh,
            validatePayload = { mappingText(it)?.let(GameModeParsers::parseModeMapMapping)?.isNotEmpty() == true }
        ) { WikiEngine.safeGet(buildParseUrl("https://wiki.biligame.com/klbq/api.php", "模板:卡拉彼丘", "wikitext")) }
        return result?.payload?.let(::mappingText)
    }

    private fun mappingText(payload: String): String? = runCatching {
        val root = SharedJson.parseToJsonElement(payload).jsonObject
        if (root["error"] != null) return@runCatching null
        root["parse"]?.jsonObject?.get("wikitext")?.jsonObject?.get("*")?.jsonPrimitive?.content
    }.getOrNull()

    suspend fun loadCachedModeMapMappingWikitext(): String? {
        return WikiParseSource.loadCachedWikitext(
            cacheType = OfflineCache.Type.GAME_MODES,
            cacheKey = "mode_map_mapping"
        )?.wikitext
    }

    suspend fun fetchModeWikitext(pageName: String, forceRefresh: Boolean): GameModeSourceResult? {
        val result = WikiParseSource.fetchWikitext(
            pageName = pageName,
            cacheType = OfflineCache.Type.GAME_MODES,
            cacheKey = "mode_$pageName",
            forceRefresh = forceRefresh
        ) ?: return null
        val wikitext = result.wikitext ?: return null

        return GameModeSourceResult(
            wikitext = wikitext,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }

    suspend fun loadCachedModeWikitext(pageName: String): GameModeSourceResult? {
        val result = WikiParseSource.loadCachedWikitext(
            cacheType = OfflineCache.Type.GAME_MODES,
            cacheKey = "mode_$pageName"
        ) ?: return null
        val wikitext = result.wikitext ?: return null

        return GameModeSourceResult(
            wikitext = wikitext,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
