package com.nekolaska.calabiyau.feature.wiki.decoration.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.net.URLEncoder

data class DecorationHtmlSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object PlayerDecorationRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    suspend fun fetchPageHtml(pageName: String, forceRefresh: Boolean): DecorationHtmlSourceResult? {
        val encoded = URLEncoder.encode(pageName, "UTF-8")
        val url = "$API?action=parse&page=$encoded&prop=text&format=json"
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.DECORATIONS,
            key = "decoration_$pageName",
            forceRefresh = forceRefresh
        ) {
            val response = WikiEngine.client.newCall(Request.Builder().url(url).build()).execute()
            response.use { if (it.isSuccessful) it.body.string() else null }
        } ?: return null

        val root = SharedJson.parseToJsonElement(result.payload).jsonObject
        val html = root["parse"]?.jsonObject
            ?.get("text")?.jsonObject
            ?.get("*")?.jsonPrimitive?.content
            ?: return null

        return DecorationHtmlSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }

    fun fetchModuleWikitext(modulePage: String): String? {
        val encoded = URLEncoder.encode(modulePage, "UTF-8")
        val url = "$API?action=parse&page=$encoded&prop=wikitext&format=json"
        val body = WikiEngine.safeGet(url) ?: return null
        return SharedJson.parseToJsonElement(body).jsonObject["parse"]?.jsonObject
            ?.get("wikitext")?.jsonObject
            ?.get("*")?.jsonPrimitive?.content
    }
}
