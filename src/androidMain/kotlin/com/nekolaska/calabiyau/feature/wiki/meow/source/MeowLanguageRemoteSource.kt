package com.nekolaska.calabiyau.feature.wiki.meow.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.feature.wiki.meow.model.MEOW_LANGUAGE_PAGE_NAME
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

data class MeowLanguagePageSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object MeowLanguageRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    suspend fun fetchPage(forceRefresh: Boolean): MeowLanguagePageSourceResult? {
        val encoded = URLEncoder.encode(MEOW_LANGUAGE_PAGE_NAME, "UTF-8")
        val url = "$API?action=parse&page=$encoded&prop=text&format=json"
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.MEOW_LANGUAGE,
            key = "meow_language",
            forceRefresh = forceRefresh
        ) { WikiEngine.safeGet(url) } ?: return null

        val root = SharedJson.parseToJsonElement(result.payload).jsonObject
        val html = root["parse"]?.jsonObject
            ?.get("text")?.jsonObject
            ?.get("*")?.jsonPrimitive?.content
            ?: return null

        return MeowLanguagePageSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
