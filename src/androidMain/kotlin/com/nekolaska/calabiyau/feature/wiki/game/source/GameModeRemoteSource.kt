package com.nekolaska.calabiyau.feature.wiki.game.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

data class GameModeSourceResult(
    val wikitext: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object GameModeRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    suspend fun fetchModeMapMappingWikitext(forceRefresh: Boolean): String? {
        val encoded = URLEncoder.encode("模板:卡拉彼丘", "UTF-8")
        val url = "$API?action=parse&page=$encoded&prop=wikitext&format=json"
        val cacheResult = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.GAME_MODES,
            key = "mode_map_mapping",
            forceRefresh = forceRefresh
        ) { WikiEngine.safeGet(url) } ?: return null

        val json = SharedJson.parseToJsonElement(cacheResult.json).jsonObject
        return json["parse"]?.jsonObject?.get("wikitext")
            ?.jsonObject?.get("*")?.jsonPrimitive?.content
    }

    suspend fun fetchModeWikitext(pageName: String, forceRefresh: Boolean): GameModeSourceResult? {
        val encoded = URLEncoder.encode(pageName, "UTF-8")
        val url = "$API?action=parse&page=$encoded&prop=wikitext&format=json"
        val cacheResult = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.GAME_MODES,
            key = "mode_$pageName",
            forceRefresh = forceRefresh
        ) { WikiEngine.safeGet(url) } ?: return null

        val json = SharedJson.parseToJsonElement(cacheResult.json).jsonObject
        val wikitext = json["parse"]?.jsonObject?.get("wikitext")
            ?.jsonObject?.get("*")?.jsonPrimitive?.content ?: return null

        return GameModeSourceResult(
            wikitext = wikitext,
            isFromCache = cacheResult.isFromCache,
            ageMs = cacheResult.ageMs
        )
    }
}
