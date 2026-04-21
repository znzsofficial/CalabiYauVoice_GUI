package com.nekolaska.calabiyau.data

import android.text.Html
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 活动页 API（Android）。
 *
 * 解析 Wiki「活动」页面表格，提取活动标题、起止时间、简介与配图。
 */
object ActivityApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val SITE_BASE = "https://wiki.biligame.com"
    private const val PAGE_BASE = "$SITE_BASE/klbq/"
    private const val PAGE_NAME = "活动"
    private const val PAGE_URL = "https://wiki.biligame.com/klbq/%E6%B4%BB%E5%8A%A8"

    private val thumbToOriginalRegex = Regex(
        """(https?://[^/]+/images/[^/]+)/thumb/([^/]+/[^/]+/[^/]+)/(?:\d+px-)?[^/?#]+"""
    )

    private data class ParsedActivity(
        val entry: ActivityEntry,
        val detailPageTitle: String?
    )

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

                val parsedActivities = parseActivities(html)
                val activities = enrichActivitiesWithHighResImages(parsedActivities, forceRefresh)
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

    private fun parseActivities(html: String): List<ParsedActivity> {
        val document = Jsoup.parse(html)
        val rows = document.select("table.klbqtable tr")

        val parsed = rows.mapNotNull { row ->
            val cells = row.select("> th, > td")
            if (cells.size < 4) return@mapNotNull null

            val titleCell = cells[0]
            val titleCellText = cleanHtml(titleCell.html())
            val title = titleCellText.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            val startTime = cleanHtml(cells[1].html())
            val endTime = cleanHtml(cells[2].html())
            val description = cleanHtml(cells[3].html())

            val detailLink = titleCell.selectFirst("a[title], a[href]")
            val detailPageTitle = detailLink?.attr("title")?.takeIf { it.isNotBlank() }
                ?: detailLink?.attr("href")?.let(::extractPageTitleFromHref)
            val wikiUrl = detailLink?.attr("href")
                ?.let(::toAbsoluteWikiUrl)
                ?: PAGE_URL

            val isHeaderRow = title in setOf("活动", "活动名称", "名称") ||
                startTime.contains("开始", ignoreCase = true) ||
                endTime.contains("结束", ignoreCase = true) ||
                description.contains("简介", ignoreCase = true) ||
                description.contains("说明", ignoreCase = true)

            if (title.isBlank() || isHeaderRow) return@mapNotNull null

            ParsedActivity(
                entry = ActivityEntry(
                    title = title,
                    startTime = startTime,
                    endTime = endTime,
                    description = description,
                    imageUrl = titleCell.selectFirst("img")?.attr("src"),
                    wikiUrl = wikiUrl
                ),
                detailPageTitle = detailPageTitle
            )
        }

        return WikiParseLogger.finishList("ActivityApi.parseActivities", parsed, html, "rows=${rows.size}")
    }

    private suspend fun enrichActivitiesWithHighResImages(
        parsed: List<ParsedActivity>,
        forceRefresh: Boolean
    ): List<ActivityEntry> {
        if (parsed.isEmpty()) return emptyList()
        return coroutineScope {
            parsed.map { item ->
                async {
                    // 活动表格里的 thumb URL 多数可直接还原为原图，优先走本地推导避免额外网络往返。
                    val directOriginal = toOriginalFromThumb(item.entry.imageUrl)
                    val detailImage = if (directOriginal == null) {
                        item.detailPageTitle?.let { resolveHighResImageUrl(it, forceRefresh) }
                    } else {
                        null
                    }
                    item.entry.copy(imageUrl = directOriginal ?: detailImage ?: item.entry.imageUrl)
                }
            }.awaitAll()
        }
    }

    private suspend fun resolveHighResImageUrl(detailPageTitle: String, forceRefresh: Boolean): String? {
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

        val fileTitle = extractFirstFileTitle(detailHtml) ?: return null
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

    private fun extractFirstFileTitle(detailHtml: String): String? {
        val document = Jsoup.parse(detailHtml)
        return document.select("a[href]").firstNotNullOfOrNull { link ->
            val pageTitle = extractPageTitleFromHref(link.attr("href")) ?: return@firstNotNullOfOrNull null
            if (pageTitle.startsWith("文件:") || pageTitle.startsWith("File:")) pageTitle else null
        }
    }

    private fun extractPageTitleFromHref(href: String): String? {
        val encodedPart = when {
            "/klbq/" in href -> href.substringAfter("/klbq/")
            href.startsWith("/") -> href.removePrefix("/")
            else -> href
        }
            .substringBefore('#')
            .substringBefore('?')
            .takeIf { it.isNotBlank() }
            ?: return null

        return runCatching { URLDecoder.decode(encodedPart, "UTF-8") }.getOrNull()
    }

    private fun toAbsoluteWikiUrl(href: String): String = when {
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("/") -> "$SITE_BASE$href"
        else -> "$PAGE_BASE${href.trimStart('/')}"
    }

    private fun toOriginalFromThumb(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val m = thumbToOriginalRegex.find(url) ?: return null
        return "${m.groupValues[1]}/${m.groupValues[2]}"
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
