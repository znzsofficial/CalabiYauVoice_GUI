package com.nekolaska.calabiyau.feature.wiki.map.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiArticleLoader
import com.nekolaska.calabiyau.core.wiki.WikiArticleRequest
import com.nekolaska.calabiyau.core.wiki.WikiArticleSnapshot
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.feature.wiki.map.model.MapDetail
import com.nekolaska.calabiyau.feature.wiki.map.model.RIFT_MAP
import com.nekolaska.calabiyau.feature.wiki.map.parser.MapDetailParsers
import com.nekolaska.calabiyau.feature.wiki.map.parser.MapListParsers
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import util.buildWikiUrl

data class MapListSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object MapRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    suspend fun fetchModeHtml(templateName: String, forceRefresh: Boolean): MapListSourceResult? {
        val wikitext = "{{游戏地图|$templateName}}"
        val url = buildWikiUrl(API, "action" to "parse", "text" to wikitext, "prop" to "text", "contentmodel" to "wikitext", "format" to "json")

        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.MAP_LIST,
            key = "mode_card_$templateName",
            forceRefresh = forceRefresh,
            validatePayload = MapListParsers::isValidPayload
        ) { WikiEngine.safeGet(url) } ?: return null

        val json = SharedJson.parseToJsonElement(result.payload).jsonObject
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

    suspend fun loadCachedModeHtml(templateName: String): MapListSourceResult? {
        val entry = OfflineCache.getEntry(
            type = OfflineCache.Type.MAP_LIST,
            key = "mode_card_$templateName"
        ) ?: return null

        if (!MapListParsers.isValidPayload(entry.content)) return null
        val json = SharedJson.parseToJsonElement(entry.content).jsonObject
        val html = json["parse"]
            ?.jsonObject?.get("text")
            ?.jsonObject?.get("*")
            ?.jsonPrimitive?.content ?: return null

        return MapListSourceResult(
            html = html,
            isFromCache = true,
            ageMs = entry.ageMs
        )
    }

    internal suspend fun readMapDetail(mapName: String, forceRefresh: Boolean, cacheOnly: Boolean,
        load: WikiArticleLoader): WikiArticleSnapshot<out MapDetail?>? {
        val type = OfflineCache.Type.MAP_DETAIL
        if (mapName == RIFT_MAP) {
            val request = WikiArticleRequest(mapName, type, mapName, forceRefresh, cacheOnly)
            return WikiParseSource.readArticle(request, load, MapDetailParsers::validatedRift)
        }
        val result = (if (cacheOnly) WikiParseSource.loadCachedHtmlAndWikitext(type, mapName)
            else WikiParseSource.fetchHtmlAndWikitext(mapName, type, mapName, forceRefresh)) ?: return null
        val wikitext = result.wikitext ?: return null
        val detail = MapDetailParsers.parseMapWikitext(mapName, wikitext, result.html.orEmpty())
        return WikiArticleSnapshot(detail, result.isFromCache, result.ageMs)
    }
}
