package com.nekolaska.calabiyau.data

import data.ApiResult
import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request
import java.net.URLEncoder

/**
 * 画廊 API（Android）。
 *
 * 解析 Wiki 页面的 wikitext，提取分 section 的图片列表，
 * 然后批量获取图片 URL。支持壁纸、表情包、四格漫画等页面。
 */
object GalleryApi {

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
        return fetchFromNetwork(pageName).also {
            if (it is ApiResult.Success) cache[pageName] = it.value
        }
    }

    private val jsonParser = SharedJson

    private suspend fun fetchFromNetwork(pageName: String): ApiResult<List<GallerySection>> =
        withContext(Dispatchers.IO) {
            try {
                // 1. 获取 wikitext
                val encoded = URLEncoder.encode(pageName, "UTF-8")
                val url = "$API?action=parse&page=$encoded&prop=wikitext&format=json"
                val response = WikiEngine.client.newCall(Request.Builder().url(url).build()).execute()
                val body = response.use { if (it.isSuccessful) it.body.string() else null }
                    ?: return@withContext ApiResult.Error("获取页面失败")

                val root = jsonParser.parseToJsonElement(body).jsonObject
                val wikitext = root["parse"]?.jsonObject
                    ?.get("wikitext")?.jsonObject
                    ?.get("*")?.jsonPrimitive?.content
                    ?: return@withContext ApiResult.Error("解析 wikitext 失败")

                // 2. 解析 wikitext → sections
                val rawSections = parseWikitext(wikitext)
                if (rawSections.isEmpty()) {
                    return@withContext ApiResult.Error("未找到图片内容")
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
                    ApiResult.Error("未能解析到图片 URL")
                } else {
                    ApiResult.Success(sections)
                }
            } catch (e: Exception) {
                ApiResult.Error("网络异常: ${e.message}")
            }
        }

    // ════════════════════════════════════════════
    //  Wikitext 解析
    // ════════════════════════════════════════════

    /** 文件名正则：匹配 gallery 标签内的 "文件:xxx.ext" 或 "File:xxx.ext" */
    private val galleryFileRegex = Regex(
        """^(?:文件|File):(.+?)(?:\|.*)?$""", RegexOption.IGNORE_CASE
    )

    /** 单图正则：匹配 [[文件:xxx.ext|...]] */
    private val singleImageRegex = Regex(
        """\[\[(?:文件|File):([^|\]]+?)(?:\|[^\]]*)?]]""", RegexOption.IGNORE_CASE
    )

    /** gallery 标签正则 */
    private val galleryOpenRegex = Regex("""<gallery[^>]*>""", RegexOption.IGNORE_CASE)
    private val galleryCloseRegex = Regex("""</gallery>""", RegexOption.IGNORE_CASE)

    /**
     * 解析 wikitext，返回 List<Pair<sectionTitle, List<Pair<fileName, caption>>>>
     *
     * 支持的结构：
     * - == 标题 == / === 子标题 ===
     * - <gallery>...</gallery> 块
     * - [[文件:xxx.png|...]] 单图
     * - {{Tabs|tab1=...|tab2=...}} 标签页（展平为同一 section）
     */
    private fun parseWikitext(wikitext: String): List<Pair<String, List<Pair<String, String>>>> {
        val lines = wikitext.lines()
        val sections = mutableListOf<Pair<String, MutableList<Pair<String, String>>>>()
        var currentTitle = "默认"
        var currentFiles = mutableListOf<Pair<String, String>>()
        var inGallery = false

        for (line in lines) {
            val trimmed = line.trim()

            // == 标题 == 或 === 子标题 ===
            val headerMatch = Regex("""^(={2,3})\s*(.+?)\s*\1\s*$""").find(trimmed)
            if (headerMatch != null) {
                // 保存上一个 section
                if (currentFiles.isNotEmpty()) {
                    sections.add(currentTitle to currentFiles)
                    currentFiles = mutableListOf()
                }
                currentTitle = headerMatch.groupValues[2].trim()
                continue
            }

            // <gallery> 开始
            if (galleryOpenRegex.containsMatchIn(trimmed)) {
                inGallery = true
                continue
            }

            // </gallery> 结束
            if (galleryCloseRegex.containsMatchIn(trimmed)) {
                inGallery = false
                continue
            }

            // gallery 内的文件行
            if (inGallery) {
                val match = galleryFileRegex.find(trimmed)
                if (match != null) {
                    val fullMatch = trimmed
                    val fileName = match.groupValues[1].trim()
                    // caption 在 | 后面
                    val caption = if (fullMatch.contains('|')) {
                        fullMatch.substringAfter('|').trim()
                    } else {
                        fileName.substringBeforeLast('.') // 去掉扩展名作为默认 caption
                    }
                    currentFiles.add(fileName to caption)
                }
                continue
            }

            // 非 gallery 区域的单图 [[文件:xxx.png|...]]
            singleImageRegex.findAll(trimmed).forEach { match ->
                val fileName = match.groupValues[1].trim()
                currentFiles.add(fileName to fileName.substringBeforeLast('.'))
            }
        }

        // 最后一个 section
        if (currentFiles.isNotEmpty()) {
            sections.add(currentTitle to currentFiles)
        }

        return sections
    }
}
