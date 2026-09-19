package com.nekolaska.calabiyau.feature.wiki.announcement.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.SharedJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import util.buildWikiUrl

data class AnnouncementSourceResult(
    val results: JsonObject,
    val isFromCache: Boolean,
    val ageMs: Long
)

object AnnouncementRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    suspend fun fetchAnnouncements(limit: Int, forceRefresh: Boolean): AnnouncementSourceResult? {
        // 属性已改名：时间/b站/官网 → 公告发布时间/公告B站发布链接/公告官网发布链接。
        // 旧 sort=时间 在属性缺失时会使 ask 返回空数组，导致解析端类型崩溃。
        val query = "[[分类:公告资讯]]|?公告发布时间|?公告B站发布链接|?公告官网发布链接" +
            "|sort=公告发布时间|order=desc|limit=$limit"
        val url = buildWikiUrl(API, "action" to "ask", "query" to query, "format" to "json")
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.ANNOUNCEMENTS,
            key = "announcements_v2_$limit",
            forceRefresh = forceRefresh
        ) { WikiEngine.safeGet(url) } ?: return null

        val json = SharedJson.parseToJsonElement(result.payload).jsonObject
        return AnnouncementSourceResult(
            results = extractResults(json),
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }

    suspend fun loadCachedAnnouncements(limit: Int): AnnouncementSourceResult? {
        // 兼容 v1 缓存：沿用旧键读取，解析端对旧属性名有回退。
        for (key in listOf("announcements_v2_$limit", "announcements_$limit")) {
            val entry = OfflineCache.getEntry(
                type = OfflineCache.Type.ANNOUNCEMENTS,
                key = key
            ) ?: continue
            val json = SharedJson.parseToJsonElement(entry.content).jsonObject
            return AnnouncementSourceResult(
                results = extractResults(json),
                isFromCache = true,
                ageMs = entry.ageMs
            )
        }
        return null
    }

    /**
     * SMW 无结果时 results 是 JSON 数组（如 []），有结果时是对象。
     * 统一收敛为对象：数组按空结果处理，避免下游 JsonObject 转换崩溃。
     */
    private fun extractResults(json: JsonObject): JsonObject {
        val element = json["query"]?.jsonObject?.get("results") ?: return JsonObject(emptyMap())
        return when (element) {
            is JsonObject -> element
            else -> JsonObject(emptyMap())
        }
    }
}
