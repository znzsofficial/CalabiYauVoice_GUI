package com.nekolaska.calabiyau.feature.wiki.map

import android.text.Html
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * 地图详情 API（Android）。
 *
 * 通过 MediaWiki parse API 同时获取地图页面的 wikitext 与渲染 HTML。
 *
 * - `{{地图|...}}` 模板参数继续从 wikitext 读取，适合简介/支持模式/上线平台等稳定文本字段
 * - 地形图、概览图、更新改动历史改从 HTML 章节读取，避免依赖脆弱的文件名正则或 `#lst` 源码结构
 */
object MapDetailApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    /** 地图详情 */
    data class MapDetail(
        val name: String,
        val description: String,    // 简介
        val supportedModes: String, // 支持模式
        val platforms: String,      // 上线平台
        val terrainMapUrl: String?, // 地形图 URL
        val galleryUrls: List<String>, // 概览图 URL 列表
        val updateHistory: List<UpdateEntry> // 更新改动历史
    )

    /** 更新改动历史条目 */
    data class UpdateEntry(
        val date: String,
        val changes: List<String>
    )


    /**
     * 获取地图详情。
     * @param mapName 地图名（如"风曳镇"）
     */
    suspend fun fetchMapDetail(
        mapName: String,
        forceRefresh: Boolean = false
    ): ApiResult<MapDetail> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(mapName, "UTF-8")
                val url = "$API?action=parse&page=$encoded&prop=wikitext|text&format=json"

                val result = OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.MAP_DETAIL,
                    key = mapName,
                    forceRefresh = forceRefresh
                ) { WikiEngine.safeGet(url) }
                    ?: return@withContext ApiResult.Error(
                        "请求失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )
                val body = result.json

                val json = SharedJson.parseToJsonElement(body).jsonObject
                val parseObj = json["parse"]?.jsonObject
                    ?: return@withContext ApiResult.Error(
                        "无法获取页面内容",
                        kind = ErrorKind.PARSE
                    )
                val wikitext = parseObj["wikitext"]?.jsonObject?.get("*")
                    ?.jsonPrimitive?.content
                    ?: return@withContext ApiResult.Error(
                        "无法获取页面内容",
                        kind = ErrorKind.PARSE
                    )
                val html = parseObj["text"]?.jsonObject?.get("*")
                    ?.jsonPrimitive?.content
                    .orEmpty()

                val detail = parseMapWikitext(mapName, wikitext, html)
                    ?: return@withContext ApiResult.Error(
                        "未找到地图信息模板",
                        kind = ErrorKind.NOT_FOUND
                    )

                ApiResult.Success(
                    detail,
                    isOffline = result.isFromCache,
                    cacheAgeMs = result.ageMs
                )
            } catch (e: Exception) {
                ApiResult.Error("获取地图详情失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    private fun parseMapWikitext(
        name: String,
        wikitext: String,
        html: String
    ): MapDetail? {
        // 解析 {{地图|...}} 模板
        val mapContent = extractTemplate(wikitext, "地图") ?: return null
        val params = parseTemplateParams(mapContent)

        val document = Jsoup.parse(html)
        val terrainMapUrl = parseTerrainMapUrl(document)
        val galleryUrls = parseGalleryUrls(document)
        val updateHistory = parseUpdateHistoryFromHtml(document)

        return MapDetail(
            name = name,
            description = params["简介"] ?: "",
            supportedModes = params["支持模式"] ?: "",
            platforms = params["上线平台"] ?: "",
            terrainMapUrl = terrainMapUrl,
            galleryUrls = galleryUrls,
            updateHistory = updateHistory
        )
    }

    /** 提取指定名称的模板内容（处理嵌套大括号） */
    private fun extractTemplate(wikitext: String, templateName: String): String? {
        val startMarker = "{{$templateName"
        val startIdx = wikitext.indexOf(startMarker)
        if (startIdx == -1) return null

        var depth = 0
        var i = startIdx
        while (i < wikitext.length - 1) {
            if (wikitext[i] == '{' && wikitext[i + 1] == '{') {
                depth++; i += 2
            } else if (wikitext[i] == '}' && wikitext[i + 1] == '}') {
                depth--
                if (depth == 0) {
                    return wikitext.substring(startIdx + startMarker.length, i).trimStart()
                }
                i += 2
            } else {
                i++
            }
        }
        return null
    }

    /** 解析模板参数 */
    private fun parseTemplateParams(content: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        content.split("|").forEach { part ->
            val eqIdx = part.indexOf('=')
            if (eqIdx > 0) {
                val key = part.substring(0, eqIdx).trim()
                val value = part.substring(eqIdx + 1).trim()
                if (key.isNotEmpty()) params[key] = value
            }
        }
        return params
    }

    private fun parseTerrainMapUrl(document: org.jsoup.nodes.Document): String? {
        val sectionNodes = collectSectionNodes(document, "地形图")
        return sectionNodes
            .flatMap { node -> node.select("img[src]") }
            .mapNotNull { image -> image.absUrl("src").ifBlank { image.attr("src").trim() } }
            .firstOrNull { it.isNotBlank() }
    }

    private fun parseGalleryUrls(document: org.jsoup.nodes.Document): List<String> {
        val sectionTitles = listOf("地图概览", "旧版地图概览")
        val urls = linkedSetOf<String>()

        sectionTitles.forEach { title ->
            val sectionNodes = collectSectionNodes(document, title)
            sectionNodes
                .flatMap { node -> node.select("img[src]") }
                .mapNotNull { image -> image.absUrl("src").ifBlank { image.attr("src").trim() } }
                .filter { it.isNotBlank() }
                .forEach { urls += it }
        }

        return urls.toList()
    }

    private fun parseUpdateHistoryFromHtml(document: org.jsoup.nodes.Document): List<UpdateEntry> {
        val sectionNodes = collectSectionNodes(document, "更新改动历史")
        if (sectionNodes.isEmpty()) return emptyList()

        val wrapper = Jsoup.parse("<div id='map-update-history-wrapper'></div>")
        val container = wrapper.getElementById("map-update-history-wrapper") ?: return emptyList()
        sectionNodes.forEach { container.appendChild(it.clone()) }

        val entries = mutableListOf<UpdateEntry>()
        var currentDate = ""
        var currentChanges = mutableListOf<String>()

        fun flushEntry() {
            if (currentDate.isNotBlank() && currentChanges.isNotEmpty()) {
                entries += UpdateEntry(date = currentDate, changes = currentChanges.toList())
            }
            currentDate = ""
            currentChanges = mutableListOf()
        }

        container.getAllElements()
            .drop(1)
            .forEach { element ->
                when {
                    element.tagName().equals("div", ignoreCase = true) &&
                        element.hasClass("alert") &&
                        element.hasClass("alert-warning") -> {
                        flushEntry()
                        currentDate = cleanHtml(element.html())
                    }

                    element.tagName().equals("li", ignoreCase = true) && currentDate.isNotBlank() -> {
                        cleanHtml(element.html())
                            .takeIf { it.isNotBlank() }
                            ?.let { currentChanges += it }
                    }
                }
            }

        flushEntry()
        if (entries.isNotEmpty()) return entries

        val fallbackParagraphs = sectionNodes
            .filter { it.tagName().equals("p", ignoreCase = true) }
            .mapNotNull { paragraph -> cleanHtml(paragraph.html()).takeIf { it.isNotBlank() } }

        return if (fallbackParagraphs.isNotEmpty()) {
            listOf(UpdateEntry(date = "历史记录", changes = fallbackParagraphs))
        } else {
            emptyList()
        }
    }

    private fun cleanHtml(raw: String): String {
        if (raw.isBlank()) return ""
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

    private fun collectSectionNodes(
        document: org.jsoup.nodes.Document,
        headingText: String
    ): List<Element> {
        val headline = document.select("span.mw-headline")
            .firstOrNull { it.text().trim() == headingText }
            ?: return emptyList()

        val sectionRoot = headline.parent() ?: return emptyList()
        val nodes = mutableListOf<Element>()
        var cursor: Element? = sectionRoot.nextElementSibling()
        while (cursor != null) {
            if (cursor.tagName().matches(Regex("h[1-6]", RegexOption.IGNORE_CASE))) break
            nodes.add(cursor)
            cursor = cursor.nextElementSibling()
        }
        return nodes
    }

}
