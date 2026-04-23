package com.nekolaska.calabiyau.feature.wiki.gallery

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 画廊 API（Android）。
 *
 * 解析 Wiki 页面的渲染 HTML，提取分 section 的图片列表，
 * 然后批量获取图片 URL。支持壁纸、表情包、四格漫画等页面。
 */
object GalleryApi {

    init {
        MemoryCacheRegistry.register("GalleryApi", ::clearMemoryCache)
    }

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    /** 单张图片 */
    data class GalleryImage(
        val fileName: String,   // 不含 "文件:" 前缀
        val caption: String,    // 图片说明文字
        val imageUrl: String    // 实际图片 URL
    )

    /** 一个分区（== 标题 ==） */
    data class GallerySection(
        val title: String,
        val images: List<GalleryImage>
    )


    // ── 内存缓存 ──
    private val cache = mutableMapOf<String, List<GallerySection>>()

    fun clearMemoryCache() { cache.clear() }

    /**
     * 获取画廊数据（带缓存）。
     * @param pageName Wiki 页面名（如 "壁纸"、"表情包"、"官方四格漫画"）
     */
    suspend fun fetchGallery(
        pageName: String,
        forceRefresh: Boolean = false
    ): ApiResult<List<GallerySection>> {
        if (!forceRefresh) {
            cache[pageName]?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(pageName, forceRefresh).also {
            if (it is ApiResult.Success) cache[pageName] = it.value
        }
    }

    private val jsonParser = SharedJson

    private suspend fun fetchFromNetwork(
        pageName: String,
        forceRefresh: Boolean
    ): ApiResult<List<GallerySection>> =
        withContext(Dispatchers.IO) {
            try {
                // 1. 获取渲染 HTML
                val encoded = URLEncoder.encode(pageName, "UTF-8")
                val url = "$API?action=parse&page=$encoded&prop=text&format=json"
                val result = OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.GALLERY,
                    key = "gallery_$pageName",
                    forceRefresh = forceRefresh
                ) { WikiEngine.safeGet(url) } ?: return@withContext ApiResult.Error(
                    "获取页面失败，且无离线缓存",
                    kind = ErrorKind.NETWORK
                )
                val body = result.json

                val root = jsonParser.parseToJsonElement(body).jsonObject
                val html = root["parse"]?.jsonObject
                    ?.get("text")?.jsonObject
                    ?.get("*")?.jsonPrimitive?.content
                    ?: return@withContext ApiResult.Error(
                        "解析页面 HTML 失败",
                        kind = ErrorKind.PARSE
                    )

                // 2. 解析 HTML → sections
                val rawSections = parseHtml(pageName, html)
                if (rawSections.isEmpty()) {
                    return@withContext ApiResult.Error(
                        "未找到图片内容",
                        kind = ErrorKind.NOT_FOUND
                    )
                }

                // 3. 收集所有文件名，批量获取 URL
                val allFileNames = rawSections.flatMap { s -> s.second.map { it.first } }.distinct()
                val urlMap = WikiEngine.fetchImageUrls(allFileNames)

                // 4. 组装结果
                val sections = rawSections.mapNotNull { (title, files) ->
                    val images = files.mapNotNull { (fileName, caption) ->
                        val imageUrl = urlMap[fileName] ?: return@mapNotNull null
                        GalleryImage(fileName, caption, imageUrl)
                    }
                    if (images.isEmpty()) null else GallerySection(title, images)
                }

                if (sections.isEmpty()) {
                    ApiResult.Error("未能解析到图片 URL", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(
                        sections,
                        isOffline = result.isFromCache,
                        cacheAgeMs = result.ageMs
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error("网络异常: ${e.message}", kind = e.toErrorKind())
            }
        }

    private fun parseHtml(pageName: String, html: String): List<Pair<String, List<Pair<String, String>>>> {
        val document = Jsoup.parse(html)
        val rootChildren = document.select(".mw-parser-output").firstOrNull()?.children().orEmpty()
        if (rootChildren.isEmpty()) return emptyList()

        val sections = mutableListOf<Pair<String, List<Pair<String, String>>>>()
        var currentTitle = pageName
        var currentElements = mutableListOf<Element>()

        fun flushSection() {
            val images = currentElements.flatMap { extractImagesFromElement(it) }
                .distinctBy { it.first }
            if (images.isNotEmpty()) {
                sections += currentTitle to images
            }
            currentElements = mutableListOf()
        }

        rootChildren.forEach { element ->
            if (element.tagName().matches(Regex("h[1-6]"))) {
                flushSection()
                currentTitle = element.selectFirst("span.mw-headline")?.text()?.trim().orEmpty().ifBlank { pageName }
            } else {
                currentElements.add(element)
            }
        }
        flushSection()

        return WikiParseLogger.finishList("GalleryApi.parseHtml", sections, html, "page=$pageName")
    }

    private fun extractImagesFromElement(element: Element): List<Pair<String, String>> {
        return when {
            element.hasClass("resp-tabs") -> extractRespTabsImages(element)
            element.hasClass("tab") -> extractTabImages(element)
            else -> extractPlainImages(element)
        }
    }

    private fun extractRespTabsImages(container: Element): List<Pair<String, String>> {
        val labels = container.select(".resp-tabs-list .tab-panel").map { it.text().trim() }
        return container.select(".resp-tabs-container > .resp-tab-content").mapNotNull { pane ->
            val link = pane.selectFirst("a.image") ?: return@mapNotNull null
            val fileName = extractFileName(link) ?: return@mapNotNull null
            val index = pane.elementSiblingIndex()
            val caption = labels.getOrNull(index)?.takeIf { it.isNotBlank() }
                ?: defaultCaption(fileName)
            fileName to caption
        }
    }

    private fun extractTabImages(container: Element): List<Pair<String, String>> {
        val labels = container.select(".tab-nav > li > a").map { it.text().trim() }
        return container.select(".tab-content > .tab-pane").flatMap { pane ->
            val paneIndex = pane.elementSiblingIndex()
            val paneLabel = labels.getOrNull(paneIndex).orEmpty()
            val galleryBoxes = pane.select("li.gallerybox")
            when {
                galleryBoxes.isNotEmpty() -> galleryBoxes.mapNotNull { box ->
                    val link = box.selectFirst("a.image") ?: return@mapNotNull null
                    val fileName = extractFileName(link) ?: return@mapNotNull null
                    val caption = box.selectFirst(".gallerytext")?.text()?.trim().takeUnless { it.isNullOrBlank() }
                        ?: paneLabel.takeIf { it.isNotBlank() }
                        ?: defaultCaption(fileName)
                    fileName to caption
                }
                else -> {
                    val link = pane.selectFirst("a.image")
                    val fileName = link?.let(::extractFileName)
                    if (fileName != null) {
                        listOf(fileName to (paneLabel.takeIf { it.isNotBlank() } ?: defaultCaption(fileName)))
                    } else {
                        emptyList()
                    }
                }
            }
        }
    }

    private fun extractPlainImages(element: Element): List<Pair<String, String>> {
        val galleryBoxes = element.select("li.gallerybox")
        if (galleryBoxes.isNotEmpty()) {
            return galleryBoxes.mapNotNull { box ->
                val link = box.selectFirst("a.image") ?: return@mapNotNull null
                val fileName = extractFileName(link) ?: return@mapNotNull null
                val caption = box.selectFirst(".gallerytext")?.text()?.trim().takeUnless { it.isNullOrBlank() }
                    ?: defaultCaption(fileName)
                fileName to caption
            }
        }

        return element.select("> a.image, a.image")
            .mapNotNull { link ->
                val fileName = extractFileName(link) ?: return@mapNotNull null
                fileName to defaultCaption(fileName)
            }
    }

    private fun extractFileName(link: Element): String? {
        val href = link.attr("href")
        if (href.isNotBlank()) {
            val pageTitle = extractPageTitleFromHref(href)
            if (!pageTitle.isNullOrBlank()) {
                return pageTitle.removePrefix("文件:").removePrefix("File:")
            }
        }

        return link.selectFirst("img")?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
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

    private fun defaultCaption(fileName: String): String {
        return fileName.substringBeforeLast('.')
    }
}
