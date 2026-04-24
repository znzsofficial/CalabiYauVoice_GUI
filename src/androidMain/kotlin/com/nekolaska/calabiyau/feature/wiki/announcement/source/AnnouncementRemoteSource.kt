package com.nekolaska.calabiyau.feature.wiki.announcement.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.SharedJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

data class AnnouncementSourceResult(
    val results: JsonObject,
    val isFromCache: Boolean,
    val ageMs: Long
)

object AnnouncementRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    suspend fun fetchAnnouncements(limit: Int, forceRefresh: Boolean): AnnouncementSourceResult? {
        val query = "[[分类:公告资讯]]|?时间|?b站|?官网|sort=时间|order=desc|limit=$limit"
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$API?action=ask&query=$encoded&format=json"
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.ANNOUNCEMENTS,
            key = "announcements_$limit",
            forceRefresh = forceRefresh
        ) { WikiEngine.safeGet(url) } ?: return null

        val json = SharedJson.parseToJsonElement(result.json).jsonObject
        val results = json["query"]?.jsonObject?.get("results")?.jsonObject ?: return null
        return AnnouncementSourceResult(
            results = results,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
