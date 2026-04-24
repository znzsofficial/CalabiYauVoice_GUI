package com.nekolaska.calabiyau.feature.wiki.gallery.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.SharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

object GalleryRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    suspend fun fetchPageHtml(pageName: String, forceRefresh: Boolean): OfflineCache.CacheResult? {
        val encoded = URLEncoder.encode(pageName, "UTF-8")
        val url = "$API?action=parse&page=$encoded&prop=text&format=json"
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.GALLERY,
            key = "gallery_$pageName",
            forceRefresh = forceRefresh
        ) { WikiEngine.safeGet(url) } ?: return null

        val root = SharedJson.parseToJsonElement(result.json).jsonObject
        val html = root["parse"]?.jsonObject
            ?.get("text")?.jsonObject
            ?.get("*")?.jsonPrimitive?.content
            ?: return null
        return result.copy(json = html)
    }

    suspend fun fetchImageUrls(fileNames: List<String>): Map<String, String> {
        return WikiEngine.fetchImageUrls(fileNames)
    }
}
