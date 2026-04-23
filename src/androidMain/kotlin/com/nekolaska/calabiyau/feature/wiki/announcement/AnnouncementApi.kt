package com.nekolaska.calabiyau.feature.wiki.announcement

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URLEncoder

/**
 * 公告资讯 API（Android）。
 *
 * 通过 Semantic MediaWiki ask API 获取公告列表，
 * 包含标题、时间、B站链接和官网链接。
 */
object AnnouncementApi {

    init {
        MemoryCacheRegistry.register("AnnouncementApi", ::clearMemoryCache)
    }

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"

    /** 公告信息 */
    data class Announcement(
        val title: String,
        val date: String,       // 时间（如"2026年03月26日"）
        val biliUrl: String,    // B站链接
        val officialUrl: String,// 官网链接
        val wikiUrl: String     // Wiki 页面链接
    )


    // ── 内存缓存 ──
    @Volatile
    private var cachedData: List<Announcement>? = null

    fun clearMemoryCache() { cachedData = null }

    /**
     * 获取公告列表（带缓存）。
     * @param limit 获取数量
     */
    suspend fun fetchAnnouncements(
        limit: Int = 50,
        forceRefresh: Boolean = false
    ): ApiResult<List<Announcement>> {
        if (!forceRefresh) {
            cachedData?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(limit, forceRefresh).also {
            if (it is ApiResult.Success) cachedData = it.value
        }
    }

    private suspend fun fetchFromNetwork(
        limit: Int,
        forceRefresh: Boolean
    ): ApiResult<List<Announcement>> =
        withContext(Dispatchers.IO) {
            try {
                val query = "[[分类:公告资讯]]|?时间|?b站|?官网|sort=时间|order=desc|limit=$limit"
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "$API?action=ask&query=$encoded&format=json"
                val result = OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.ANNOUNCEMENTS,
                    key = "announcements_$limit",
                    forceRefresh = forceRefresh
                ) { WikiEngine.safeGet(url) }
                    ?: return@withContext ApiResult.Error(
                        "请求失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )
                val body = result.json

                val json = SharedJson.parseToJsonElement(body).jsonObject
                val results = json["query"]?.jsonObject?.get("results")?.jsonObject
                    ?: return@withContext ApiResult.Error(
                        "无法获取公告数据",
                        kind = ErrorKind.PARSE
                    )

                val announcements = results.entries.map { (title, value) ->
                    val obj = value.jsonObject
                    val printouts = obj["printouts"]?.jsonObject
                    val fullUrl = obj["fullurl"]?.jsonPrimitive?.content
                        ?: "${WIKI_BASE}${URLEncoder.encode(title, "UTF-8").replace("+", "%20")}"

                    // 时间字段可能是字符串或对象
                    val dateStr = printouts?.get("时间")?.jsonArray?.firstOrNull()?.let { elem ->
                        when (elem) {
                            is JsonPrimitive -> elem.content
                            is JsonObject -> elem["raw"]?.jsonPrimitive?.content
                                ?: elem["timestamp"]?.jsonPrimitive?.content ?: ""

                            else -> ""
                        }
                    } ?: ""

                    val biliUrl = printouts?.get("b站")?.jsonArray
                        ?.firstOrNull()?.jsonPrimitive?.content ?: ""
                    val officialUrl = printouts?.get("官网")?.jsonArray
                        ?.firstOrNull()?.jsonPrimitive?.content ?: ""

                    Announcement(
                        title = title,
                        date = dateStr,
                        biliUrl = biliUrl,
                        officialUrl = officialUrl,
                        wikiUrl = fullUrl
                    )
                }.sortedByDescending { it.date }

                if (announcements.isEmpty()) {
                    ApiResult.Error("未找到公告数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(
                        announcements,
                        isOffline = result.isFromCache,
                        cacheAgeMs = result.ageMs
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error("获取公告失败: ${e.message}", kind = e.toErrorKind())
            }
        }

}
