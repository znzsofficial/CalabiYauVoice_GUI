package com.nekolaska.calabiyau.feature.wiki.bio.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

data class BioCardPageSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object BioCardRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"

    suspend fun fetchPageHtml(
        pageName: String,
        cacheKey: String,
        forceRefresh: Boolean
    ): BioCardPageSourceResult? {
        val encoded = URLEncoder.encode(pageName, "UTF-8")
        val url = "$API?action=parse&page=$encoded&prop=text&format=json"
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.BIO_CARDS,
            key = cacheKey,
            forceRefresh = forceRefresh
        ) { WikiEngine.safeGet(url) } ?: return null

        val root = SharedJson.parseToJsonElement(result.payload).jsonObject
        val html = root["parse"]?.jsonObject?.get("text")
            ?.jsonObject?.get("*")?.jsonPrimitive?.content
            ?: return null
        return BioCardPageSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }

    fun pageUrl(pageName: String): String {
        return WIKI_BASE + URLEncoder.encode(pageName, "UTF-8").replace("+", "%20")
    }
}
