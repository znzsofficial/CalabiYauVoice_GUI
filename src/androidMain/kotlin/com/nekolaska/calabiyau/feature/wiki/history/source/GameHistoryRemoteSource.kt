package com.nekolaska.calabiyau.feature.wiki.history.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.feature.wiki.history.model.GAME_HISTORY_PAGE_NAME
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

data class GameHistoryPageSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object GameHistoryRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    suspend fun fetchGameHistoryPage(forceRefresh: Boolean): GameHistoryPageSourceResult? {
        val encoded = URLEncoder.encode(GAME_HISTORY_PAGE_NAME, "UTF-8")
        val url = "$API?action=parse&page=$encoded&prop=text&format=json"
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.GAME_HISTORY,
            key = "game_history",
            forceRefresh = forceRefresh
        ) { WikiEngine.safeGet(url) } ?: return null

        val root = SharedJson.parseToJsonElement(result.payload).jsonObject
        val html = root["parse"]?.jsonObject
            ?.get("text")?.jsonObject
            ?.get("*")?.jsonPrimitive?.content
            ?: return null

        return GameHistoryPageSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}