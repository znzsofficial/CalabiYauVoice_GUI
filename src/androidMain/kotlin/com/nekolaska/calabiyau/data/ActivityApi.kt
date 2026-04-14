package com.nekolaska.calabiyau.data

import android.text.Html
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * 活动页 API（Android）。
 *
 * 解析 Wiki「活动」页面表格，提取活动标题、起止时间、简介与配图。
 */
object ActivityApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val PAGE_NAME = "活动"
    private const val PAGE_URL = "https://wiki.biligame.com/klbq/%E6%B4%BB%E5%8A%A8"

    data class ActivityEntry(
        val title: String,
        val startTime: String,
        val endTime: String,
        val description: String,
        val imageUrl: String? = null,
        val wikiUrl: String = PAGE_URL
    )

    @Volatile
    private var cachedData: List<ActivityEntry>? = null

    fun clearMemoryCache() {
        cachedData = null
    }

    suspend fun fetchActivities(forceRefresh: Boolean = false): ApiResult<List<ActivityEntry>> {
        if (!forceRefresh) {
            cachedData?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedData = it.value
        }
    }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<ActivityEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(PAGE_NAME, "UTF-8")
                val url = "$API?action=parse&page=$encoded&prop=text&format=json"
                val result = OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.ACTIVITIES,
                    key = "activities",
                    forceRefresh = forceRefresh
                ) { WikiEngine.safeGet(url) }
                    ?: return@withContext ApiResult.Error(
                        "请求失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )

                val root = SharedJson.parseToJsonElement(result.json).jsonObject
                val html = root["parse"]?.jsonObject?.get("text")
                    ?.jsonObject?.get("*")?.jsonPrimitive?.content
                    ?: return@withContext ApiResult.Error(
                        "无法获取活动数据",
                        kind = ErrorKind.PARSE
                    )

                val activities = parseActivities(html)
                if (activities.isEmpty()) {
                    ApiResult.Error("未找到活动数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(
                        activities,
                        isOffline = result.isFromCache,
                        cacheAgeMs = result.ageMs
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error("获取活动失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    private fun parseActivities(html: String): List<ActivityEntry> {
        val rowRegex = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("""<t[hd][^>]*>([\s\S]*?)</t[hd]>""", RegexOption.DOT_MATCHES_ALL)
        val imageRegex = Regex("""<img[^>]*src=\"([^\"]+)\"[^>]*>""")

        return rowRegex.findAll(html).mapNotNull { rowMatch ->
            val cells = cellRegex.findAll(rowMatch.groupValues[1])
                .map { it.groupValues[1] }
                .toList()
            if (cells.size < 4) return@mapNotNull null

            val titleCellText = cleanHtml(cells[0])
            val title = titleCellText.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            val startTime = cleanHtml(cells[1])
            val endTime = cleanHtml(cells[2])
            val description = cleanHtml(cells[3])

            val isHeaderRow = title in setOf("活动", "活动名称", "名称") ||
                startTime.contains("开始", ignoreCase = true) ||
                endTime.contains("结束", ignoreCase = true) ||
                description.contains("简介", ignoreCase = true) ||
                description.contains("说明", ignoreCase = true)

            if (title.isBlank() || isHeaderRow) return@mapNotNull null

            ActivityEntry(
                title = title,
                startTime = startTime,
                endTime = endTime,
                description = description,
                imageUrl = imageRegex.find(cells[0])?.groupValues?.get(1)
            )
        }.toList()
    }

    private fun cleanHtml(raw: String): String {
        val normalized = raw
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace("&nbsp;", " ")
        return Html.fromHtml(normalized, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace("￼", "")
            .replace("\uFFFC", "")
            .replace('\u00A0', ' ')
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }
}
