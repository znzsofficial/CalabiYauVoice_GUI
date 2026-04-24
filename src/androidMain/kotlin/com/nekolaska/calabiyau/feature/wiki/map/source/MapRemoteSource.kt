package com.nekolaska.calabiyau.feature.wiki.map.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

data class MapListSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

data class MapDetailSourceResult(
    val wikitext: String,
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object MapRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    suspend fun fetchModeHtml(templateName: String, forceRefresh: Boolean): MapListSourceResult? {
        val wikitext = "{{游戏地图|$templateName}}"
        val encoded = URLEncoder.encode(wikitext, "UTF-8")
        val url = "$API?action=parse&text=$encoded&prop=text&contentmodel=wikitext&format=json"

        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.MAP_LIST,
            key = "mode_$templateName",
            forceRefresh = forceRefresh
        ) { WikiEngine.safeGet(url) } ?: return null

        val json = SharedJson.parseToJsonElement(result.json).jsonObject
        val html = json["parse"]
            ?.jsonObject?.get("text")
            ?.jsonObject?.get("*")
            ?.jsonPrimitive?.content ?: return null

        return MapListSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }

    suspend fun fetchMapDetailPayload(mapName: String, forceRefresh: Boolean): MapDetailSourceResult? {
        val encoded = URLEncoder.encode(mapName, "UTF-8")
        val url = "$API?action=parse&page=$encoded&prop=wikitext|text&format=json"

        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.MAP_DETAIL,
            key = mapName,
            forceRefresh = forceRefresh
        ) { WikiEngine.safeGet(url) } ?: return null

        val json = SharedJson.parseToJsonElement(result.json).jsonObject
        val parseObj = json["parse"]?.jsonObject ?: return null
        val wikitext = parseObj["wikitext"]?.jsonObject?.get("*")
            ?.jsonPrimitive?.content ?: return null
        val html = parseObj["text"]?.jsonObject?.get("*")
            ?.jsonPrimitive?.content
            .orEmpty()

        return MapDetailSourceResult(
            wikitext = wikitext,
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
