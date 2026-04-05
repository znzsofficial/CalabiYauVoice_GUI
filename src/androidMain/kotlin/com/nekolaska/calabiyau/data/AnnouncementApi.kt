package com.nekolaska.calabiyau.data

import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request
import java.net.URLEncoder

/**
 * 公告资讯 API（Android）。
 *
 * 通过 Semantic MediaWiki ask API 获取公告列表，
 * 包含标题、时间、B站链接和官网链接。
 */
object AnnouncementApi {

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

    sealed interface ApiResult<out T> {
        data class Success<T>(val value: T) : ApiResult<T>
        data class Error(val message: String) : ApiResult<Nothing>
    }

    // ── 内存缓存 ──
    @Volatile
    private var cachedData: List<Announcement>? = null

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
        return fetchFromNetwork(limit).also {
            if (it is ApiResult.Success) cachedData = it.value
        }
    }

    private suspend fun fetchFromNetwork(limit: Int): ApiResult<List<Announcement>> =
        withContext(Dispatchers.IO) {
            try {
                val query = "[[分类:公告资讯]]|?时间|?b站|?官网|sort=时间|order=desc|limit=$limit"
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "$API?action=ask&query=$encoded&format=json"
                val body = httpGet(url) ?: return@withContext ApiResult.Error("请求失败")

                val json = SharedJson.parseToJsonElement(body).jsonObject
                val results = json["query"]?.jsonObject?.get("results")?.jsonObject
                    ?: return@withContext ApiResult.Error("无法获取公告数据")

                val announcements = results.entries.map { (title, value) ->
                    val obj = value.jsonObject
                    val printouts = obj["printouts"]?.jsonObject
                    val fullUrl = obj["fullurl"]?.jsonPrimitive?.content
                        ?: "${WIKI_BASE}${URLEncoder.encode(title, "UTF-8").replace("+", "%20")}"

                    // 时间字段可能是字符串或对象
                    val dateStr = printouts?.get("时间")?.jsonArray?.firstOrNull()?.let { elem ->
                        when {
                            elem is JsonPrimitive -> elem.content
                            elem is JsonObject -> elem["raw"]?.jsonPrimitive?.content
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
                    ApiResult.Error("未找到公告数据")
                } else {
                    ApiResult.Success(announcements)
                }
            } catch (e: Exception) {
                ApiResult.Error("获取公告失败: ${e.message}")
            }
        }

    private fun httpGet(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            WikiEngine.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body.string()
                if (!body.trimStart().startsWith("{")) return null
                body
            }
        } catch (_: Exception) {
            null
        }
    }
}
