package com.nekolaska.calabiyau.feature.wiki.activity.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.SharedJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

data class ActivityPageSourceResult(
    val html: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object ActivityRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val PAGE_NAME = "活动"

    suspend fun fetchActivitiesPage(forceRefresh: Boolean): ActivityPageSourceResult? {
        val encoded = URLEncoder.encode(PAGE_NAME, "UTF-8")
        val url = "$API?action=parse&page=$encoded&prop=text&format=json"
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.ACTIVITIES,
            key = "activities",
            forceRefresh = forceRefresh
        ) { WikiEngine.safeGet(url) } ?: return null

        val root = SharedJson.parseToJsonElement(result.json).jsonObject
        val html = root["parse"]?.jsonObject?.get("text")
            ?.jsonObject?.get("*")?.jsonPrimitive?.content ?: return null

        return ActivityPageSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }

    suspend fun resolveHighResImageUrl(detailPageTitle: String, forceRefresh: Boolean): String? {
        val title = detailPageTitle.trim()
        if (title.isBlank()) return null
        val key = "activity_image:$title"
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.ACTIVITIES,
            key = key,
            forceRefresh = forceRefresh
        ) {
            fetchHighResImageUrlFromDetailPage(title)
        }
        return result?.json?.takeIf { it.isNotBlank() }
    }

    private fun fetchHighResImageUrlFromDetailPage(detailPageTitle: String): String? {
        val detailUrl = "$API?action=parse&page=${URLEncoder.encode(detailPageTitle, "UTF-8")}&prop=text&format=json"
        val detailJson = WikiEngine.safeGet(detailUrl) ?: return null
        val detailHtml = SharedJson.parseToJsonElement(detailJson).jsonObject["parse"]
            ?.jsonObject?.get("text")
            ?.jsonObject?.get("*")?.jsonPrimitive?.content
            ?: return null

        val fileTitle = com.nekolaska.calabiyau.feature.wiki.activity.parser.ActivityParsers.extractFirstFileTitle(detailHtml) ?: return null
        val infoUrl = "$API?action=query&titles=${URLEncoder.encode(fileTitle, "UTF-8")}&prop=imageinfo&iiprop=url&format=json"
        val infoJson = WikiEngine.safeGet(infoUrl) ?: return null
        val pages = SharedJson.parseToJsonElement(infoJson).jsonObject["query"]
            ?.jsonObject?.get("pages")?.jsonObject
            ?: return null

        return pages.values.firstNotNullOfOrNull { page ->
            page.jsonObject["imageinfo"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("url")
                ?.jsonPrimitive
                ?.content
        }
    }
}
