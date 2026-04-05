package com.nekolaska.calabiyau.data

import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request
import java.net.URLEncoder

/**
 * 玩家装饰通用 API（Android）。
 *
 * 解析 Wiki 玩家装饰页面（基板/封装/聊天气泡/头套/超弦体动作/头像框）的渲染 HTML，
 * 提取每个条目的图片文件名和名称，按页面 section 标题分组，
 * 然后通过 MediaWiki imageinfo API 批量解析图片真实 URL。
 *
 * 所有页面共享相同的 gallerygrid HTML 结构。
 */
object PlayerDecorationApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    /** 单个装饰条目 */
    data class DecorationItem(
        val id: Int,
        val name: String,
        val quality: Int,
        val description: String,
        val source: String,
        val iconUrl: String,
        val imageUrl: String
    )

    /** 按分类分组 */
    data class DecorationSection(
        val title: String,
        val items: List<DecorationItem>
    )

    // ── 内存缓存（按页面名分别缓存）──
    private val cacheMap = mutableMapOf<String, List<DecorationSection>>()

    /**
     * 获取装饰数据（带缓存）。
     * @param pageName Wiki 页面名，如 "基板"、"封装"、"聊天气泡"、"头套"、"超弦体动作"、"头像框"
     */
    suspend fun fetch(
        pageName: String,
        forceRefresh: Boolean = false
    ): GalleryApi.ApiResult<List<DecorationSection>> {
        if (!forceRefresh) {
            cacheMap[pageName]?.let { return GalleryApi.ApiResult.Success(it) }
        }
        return fetchFromNetwork(pageName).also {
            if (it is GalleryApi.ApiResult.Success) cacheMap[pageName] = it.value
        }
    }

    private val json = SharedJson

    private suspend fun fetchFromNetwork(pageName: String): GalleryApi.ApiResult<List<DecorationSection>> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(pageName, "UTF-8")
                val url = "$API?action=parse&page=$encoded&prop=text&format=json"
                val response = WikiEngine.client.newCall(Request.Builder().url(url).build()).execute()
                val body = response.use { if (it.isSuccessful) it.body.string() else null }
                    ?: return@withContext GalleryApi.ApiResult.Error("获取页面失败")

                val root = json.parseToJsonElement(body).jsonObject
                val html = root["parse"]?.jsonObject
                    ?.get("text")?.jsonObject
                    ?.get("*")?.jsonPrimitive?.content
                    ?: return@withContext GalleryApi.ApiResult.Error("解析 HTML 失败")

                val rawSections = parseHtml(html)
                if (rawSections.isEmpty()) {
                    return@withContext GalleryApi.ApiResult.Error("未找到${pageName}数据")
                }

                // 收集所有文件名，批量获取真实 URL
                val allIconFiles = rawSections.flatMap { s -> s.second.map { it.iconFile } }
                    .filter { it.isNotEmpty() }.distinct()
                val allImgFiles = rawSections.flatMap { s -> s.second.map { it.imgFile } }
                    .filter { it.isNotEmpty() }.distinct()
                val urlMap = WikiEngine.fetchImageUrls((allIconFiles + allImgFiles).distinct())

                val sections = rawSections.mapNotNull { (title, rawItems) ->
                    val items = rawItems.mapNotNull { raw ->
                        val iconUrl = urlMap[raw.iconFile] ?: ""
                        val imgUrl = urlMap[raw.imgFile] ?: ""
                        if (iconUrl.isEmpty() && imgUrl.isEmpty()) return@mapNotNull null
                        DecorationItem(
                            id = raw.id,
                            name = raw.name,
                            quality = raw.quality,
                            description = raw.desc,
                            source = raw.source,
                            iconUrl = iconUrl,
                            imageUrl = imgUrl
                        )
                    }
                    if (items.isEmpty()) null else DecorationSection(title, items)
                }

                if (sections.isEmpty()) {
                    GalleryApi.ApiResult.Error("未找到${pageName}内容")
                } else {
                    GalleryApi.ApiResult.Success(sections)
                }
            } catch (e: Exception) {
                GalleryApi.ApiResult.Error("网络异常: ${e.message}")
            }
        }

    // ════════════════════════════════════════════
    //  HTML 解析（通用，适用于所有 gallerygrid 页面）
    // ════════════════════════════════════════════

    private data class RawItem(
        val id: Int,
        val name: String,
        val quality: Int,
        val iconFile: String,
        val imgFile: String,
        val desc: String,
        val source: String
    )

    private val sectionRegex = Regex(
        """<span\s+class="mw-headline"[^>]*>\s*(.*?)\s*</span>""",
        RegexOption.DOT_MATCHES_ALL
    )
    private val itemStartRegex = Regex("""<div\s+class="gallerygrid-item">""")
    private val imgAltRegex = Regex("""<img[^>]+alt="([^"]+\.\w+)"[^>]*>""", RegexOption.DOT_MATCHES_ALL)
    private val qualityRegex = Regex("""data-quality="(\d+)"[^>]*>([^<]*)</span>""")
    private val idFromFileRegex = Regex("""[\s_](\d+)""")
    private val iconToImgRegex = Regex("""(.+[\s_]\d+)[\s_]icon\w*(\.\w+)""")
    private val smallRegex = Regex("""<small>(.*?)</small>""", RegexOption.DOT_MATCHES_ALL)
    private val liRegex = Regex("""<li>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)

    private fun parseHtml(html: String): List<Pair<String, List<RawItem>>> {
        data class Marker(val pos: Int, val type: String, val value: String)
        val markers = mutableListOf<Marker>()

        sectionRegex.findAll(html).forEach { match ->
            val title = match.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
            if (title.isNotEmpty()) markers.add(Marker(match.range.first, "section", title))
        }

        val itemStarts = itemStartRegex.findAll(html).map { it.range.first }.toList()
        for (i in itemStarts.indices) {
            val start = itemStarts[i]
            val end = if (i + 1 < itemStarts.size) itemStarts[i + 1] else html.length
            markers.add(Marker(start, "item", html.substring(start, end)))
        }

        markers.sortBy { it.pos }

        val sections = mutableListOf<Pair<String, MutableList<RawItem>>>()
        var currentTitle = "默认"
        var currentItems = mutableListOf<RawItem>()

        for (marker in markers) {
            when (marker.type) {
                "section" -> {
                    if (currentItems.isNotEmpty()) {
                        sections.add(currentTitle to currentItems)
                        currentItems = mutableListOf()
                    }
                    currentTitle = marker.value
                }
                "item" -> parseGridItem(marker.value)?.let { currentItems.add(it) }
            }
        }
        if (currentItems.isNotEmpty()) sections.add(currentTitle to currentItems)
        return sections
    }

    private fun parseGridItem(itemHtml: String): RawItem? {
        val imgMatch = imgAltRegex.find(itemHtml) ?: return null
        val iconFile = imgMatch.groupValues[1].trim()

        val id = idFromFileRegex.find(iconFile)?.groupValues?.get(1)?.toIntOrNull() ?: return null

        val imgFile = iconToImgRegex.find(iconFile)?.let {
            "${it.groupValues[1]}${it.groupValues[2]}"
        } ?: iconFile

        val qualityMatch = qualityRegex.find(itemHtml)
        val quality = qualityMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val name = qualityMatch?.groupValues?.get(2)?.trim() ?: "未知"

        val desc = smallRegex.find(itemHtml)?.groupValues?.get(1)
            ?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""

        val source = liRegex.findAll(itemHtml)
            .map { it.groupValues[1].replace(Regex("<[^>]*>"), "").trim() }
            .filter { it.isNotEmpty() }.joinToString("、")

        return RawItem(id, name, quality, iconFile, imgFile, desc, source)
    }
}

// ── 兼容别名，BaseplateScreen 等旧代码可继续使用 ──
typealias BaseplateApi = PlayerDecorationApi
