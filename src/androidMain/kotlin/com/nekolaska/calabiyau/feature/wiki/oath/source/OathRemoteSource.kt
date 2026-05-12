package com.nekolaska.calabiyau.feature.wiki.oath.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

data class OathSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object OathRemoteSource {

    private const val PAGE_NAME = "誓约"
    private const val CACHE_KEY = "oath_page"
    private const val API = "https://wiki.biligame.com/klbq/api.php"

    suspend fun fetchPage(forceRefresh: Boolean = false): OathSourceResult? {
        val encoded = URLEncoder.encode(PAGE_NAME, "UTF-8")
        val url = "$API?action=parse&page=$encoded&prop=text&format=json"
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.OATH,
            key = CACHE_KEY,
            forceRefresh = forceRefresh
        ) { WikiEngine.safeGet(url) } ?: return null

        val root = SharedJson.parseToJsonElement(result.payload).jsonObject
        val html = root["parse"]?.jsonObject
            ?.get("text")?.jsonObject
            ?.get("*")?.jsonPrimitive?.content ?: return null

        return OathSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
