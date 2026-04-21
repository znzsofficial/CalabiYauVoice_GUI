package com.nekolaska.calabiyau.data

import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
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

    /**
     * 玩家装饰页可选的模块真值配置。
     *
     * 注意：这里的模块数据只用于“按 id 覆盖条目文本真值”，
     * 不能替代页面 HTML 的 section 分组和图片结构来源。
     *
     * 也就是说，即使 enabled=true，当前实现依然会：
     * 1. 先从 HTML 解析分组、顺序、图片文件名
     * 2. 再用模块里的 name/quality/desc/get/spdesc 覆盖文本字段
     *
     * 这样做的原因是大多数 Lua 数据表不包含分类信息，
     * 如果直接只渲染模块表，很容易丢失页面原本的分类结构。
     */
    private data class DecorationModuleConfig(
        val modulePage: String,
        val enabled: Boolean = false
    )

    /**
     * 页面名 -> 模块数据页 的映射表。
     *
     * 查看哪些页面已经启用模块真值，直接看每项是否设置了 enabled = true 即可：
     * - enabled = true: 已启用“模块真值覆盖 HTML 文本”
     * - 未显式开启: 仍然只使用 HTML 解析结果
     *
     * 当前策略是“逐页开启”，避免一次性切换后把某些页面的结构细节或分类关系弄丢。
        *
        * 什么时候可以开启 enabled = true：
        * 1. 已确认对应模块确实是该页面的真值数据源
        * 2. 模块里的 id 能和 HTML 条目稳定对上
        * 3. 已确认页面分类仍由 HTML 提供，不会因为覆盖文本而打乱 section
        * 4. 已确认模块字段命名与当前解析器兼容（如 id/name/quality/get/desc/spdesc）
        *
        * 什么时候先不要开启：
        * 1. 还没确认模块和页面是否一一对应
        * 2. HTML 条目 id 无法稳定提取
        * 3. 页面高度依赖 HTML 中的结构化说明，而模块只给了残缺字段
        * 4. 模块字段格式和现有页面差异过大，需要单独适配
        *
        * 新增页面时建议检查清单：
        * 1. 先抓真实 HTML，确认页面是否仍是 gallerygrid / roomgrid 结构
        * 2. 再抓模块源码，确认是否存在可用真值字段
        * 3. 确认 id 提取规则是否适用
        * 4. 先把映射登记到这里
        * 5. 如无把握，先 enabled=false，只保留映射
        * 6. 确认展示正常后再开启 enabled=true
     */
    private val moduleConfigByPage = mapOf(
        "基板" to DecorationModuleConfig("模块:玩家装饰/IDCardData"),
        "封装" to DecorationModuleConfig("模块:玩家装饰/FrameData"),
        "聊天气泡" to DecorationModuleConfig("模块:玩家装饰/ChatBubblesData"),
        "喷漆" to DecorationModuleConfig("模块:玩家装饰/DecalData"),
        "头套" to DecorationModuleConfig("模块:玩家装饰/MascotHeadData"),
        "超弦体动作" to DecorationModuleConfig("模块:玩家装饰/RoleActionData"),
        "房间外观" to DecorationModuleConfig("模块:玩家装饰/ChangeBgData", enabled = true),
        "头像框" to DecorationModuleConfig("模块:玩家装饰移动端/HeadFrameData"),
        "极限推进模式载具外观" to DecorationModuleConfig("模块:玩家装饰移动端/VehicleSkinData", enabled = true),
        "勋章" to DecorationModuleConfig("模块:玩家装饰/BadgeData"),
        "登场特效" to DecorationModuleConfig("模块:玩家装饰/LoginFXData") // 暂无网页，未启用
    )

    /** 单个装饰条目 */
    data class DecorationItem(
        val id: Int,
        val name: String,
        val quality: Int,
        val description: String,
        val specialDescription: String,
        val source: String,
        val iconUrl: String,
        val imageUrl: String,
        val extraPreviews: List<DecorationPreview> = emptyList()
    )

    data class DecorationPreview(
        val label: String,
        val imageUrl: String
    )

    /** 按分类分组 */
    data class DecorationSection(
        val title: String,
        val items: List<DecorationItem>
    )

    // ── 内存缓存（按页面名分别缓存）──
    private val cacheMap = mutableMapOf<String, List<DecorationSection>>()

    fun clearMemoryCache() { cacheMap.clear() }

    /**
     * 获取装饰数据（带缓存）。
     * @param pageName Wiki 页面名，如 "基板"、"封装"、"聊天气泡"、"头套"、"超弦体动作"、"头像框"
     */
    suspend fun fetch(
        pageName: String,
        forceRefresh: Boolean = false
    ): ApiResult<List<DecorationSection>> {
        if (!forceRefresh) {
            cacheMap[pageName]?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(pageName, forceRefresh).also {
            if (it is ApiResult.Success) cacheMap[pageName] = it.value
        }
    }

    private val json = SharedJson

    /**
     * 从 Lua 模块表中读取出的文本真值。
     *
     * 这里只保留“适合由模块提供”的字段：
     * - 名称
     * - 品质
     * - 描述
     * - 特殊说明（如载具特性）
     * - 获取方式
     *
     * 不在这里保存 section、图片分组、展示顺序等结构信息，
     * 因为这些仍以页面 HTML 为准。
     */
    private data class DecorationModuleData(
        val name: String,
        val quality: Int,
        val description: String,
        val specialDescription: String,
        val source: String
    )

    private suspend fun fetchFromNetwork(
        pageName: String,
        forceRefresh: Boolean
    ): ApiResult<List<DecorationSection>> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(pageName, "UTF-8")
                val url = "$API?action=parse&page=$encoded&prop=text&format=json"
                val result = OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.DECORATIONS,
                    key = "decoration_$pageName",
                    forceRefresh = forceRefresh
                ) {
                    val response = WikiEngine.client.newCall(Request.Builder().url(url).build()).execute()
                    response.use { if (it.isSuccessful) it.body.string() else null }
                } ?: return@withContext ApiResult.Error(
                    "获取页面失败，且无离线缓存",
                    kind = ErrorKind.NETWORK
                )
                val body = result.json

                val root = json.parseToJsonElement(body).jsonObject
                val html = root["parse"]?.jsonObject
                    ?.get("text")?.jsonObject
                    ?.get("*")?.jsonPrimitive?.content
                    ?: return@withContext ApiResult.Error(
                        "解析 HTML 失败",
                        kind = ErrorKind.PARSE
                    )

                val rawSections = parseHtml(html)
                if (rawSections.isEmpty()) {
                    return@withContext ApiResult.Error(
                        "未找到${pageName}数据",
                        kind = ErrorKind.NOT_FOUND
                    )
                }

                // 这里刻意保留“先 HTML、后模块覆盖”的顺序：
                // - HTML 负责 section 分组、条目归属、图片文件名
                // - 模块只负责按 id 补/覆写真值文本字段
                // 这样即使模块完全不带分类信息，也不会把页面原本的分类解析丢掉。
                val moduleDataMap = fetchModuleData(pageName)

                // 收集所有文件名，批量获取真实 URL
                val allIconFiles = rawSections.flatMap { s -> s.second.map { it.iconFile } }
                    .filter { it.isNotEmpty() }.distinct()
                val allImgFiles = rawSections.flatMap { s -> s.second.map { it.imgFile } }
                    .filter { it.isNotEmpty() }.distinct()
                val extraPreviewFiles = rawSections
                    .flatMap { section -> section.second.flatMap { item -> item.extraPreviewFiles.map { preview -> preview.second } } }
                    .filter { it.isNotEmpty() }
                    .distinct()
                val urlMap = WikiEngine.fetchImageUrls((allIconFiles + allImgFiles + extraPreviewFiles).distinct())

                val sections = rawSections.mapNotNull { (title, rawItems) ->
                    val items = rawItems.mapNotNull { raw ->
                        val iconUrl = urlMap[raw.iconFile] ?: ""
                        val imgUrl = urlMap[raw.imgFile] ?: ""
                        if (iconUrl.isEmpty() && imgUrl.isEmpty()) return@mapNotNull null
                        val extraPreviews = raw.extraPreviewFiles.mapNotNull { (label, fileName) ->
                            val previewUrl = urlMap[fileName].orEmpty()
                            if (previewUrl.isBlank()) null else DecorationPreview(label, previewUrl)
                        }
                        DecorationItem(
                            id = raw.id,
                            name = moduleDataMap[raw.id]?.name ?: raw.name,
                            quality = moduleDataMap[raw.id]?.quality ?: raw.quality,
                            description = moduleDataMap[raw.id]?.description ?: raw.desc,
                            specialDescription = moduleDataMap[raw.id]?.specialDescription.orEmpty(),
                            source = moduleDataMap[raw.id]?.source ?: raw.source,
                            iconUrl = iconUrl,
                            imageUrl = imgUrl,
                            extraPreviews = extraPreviews
                        )
                    }
                    if (items.isEmpty()) null else DecorationSection(title, items)
                }

                if (sections.isEmpty()) {
                    ApiResult.Error("未找到${pageName}内容", kind = ErrorKind.NOT_FOUND)
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
        val source: String,
        val extraPreviewFiles: List<Pair<String, String>> = emptyList()
    )

    private val idFromFileRegex = Regex("""[\s_](\d+)""")
    private val iconToImgRegex = Regex("""(.+[\s_]\d+)[\s_]icon\w*(\.\w+)""")

    /**
     * 从渲染后的 HTML 中解析 section 分组和每个条目的页面结构信息。
     *
     * 这是玩家装饰页最重要的“结构来源”：
     * - section 标题
     * - 每个 section 下有哪些条目
     * - 页面展示顺序
     * - 图片文件名 / 附属预览文件名
     *
     * 后续即便越来越多页面启用模块真值，这里的分组解析也不应轻易删除。
     */
    private fun parseHtml(html: String): List<Pair<String, List<RawItem>>> {
        val document = Jsoup.parse(html)
        val sections = mutableListOf<Pair<String, MutableList<RawItem>>>()
        var currentTitle = "默认"
        var currentItems = mutableListOf<RawItem>()

        document.select(".mw-parser-output").firstOrNull()?.children().orEmpty().forEach { child ->
            when {
                child.tagName().matches(Regex("h[1-6]")) && child.selectFirst("span.mw-headline") != null -> {
                    if (currentItems.isNotEmpty()) {
                        sections.add(currentTitle to currentItems)
                        currentItems = mutableListOf()
                    }
                    currentTitle = child.selectFirst("span.mw-headline")?.text()?.trim().orEmpty().ifEmpty { "默认" }
                }
                child.hasClass("gallerygrid") || child.selectFirst(".gallerygrid-item") != null -> {
                    child.select(".gallerygrid-item").forEach { itemElement ->
                        parseGridItem(itemElement)?.let { currentItems.add(it) }
                    }
                }
            }
        }

        if (currentItems.isNotEmpty()) sections.add(currentTitle to currentItems)
        return WikiParseLogger.finishList("PlayerDecorationApi.parseHtml", sections, html)
    }

    private fun parseGridItem(itemElement: Element): RawItem? {
        // roomgrid-item 与常规 gallerygrid-item 的 HTML 结构不同：
        // - 房间外观需要额外解析背景图和附属 UI 小图
        // - 普通玩家装饰则大多只需要主图/图标 + 文本块
        // 因此这里先按 class 分流，避免把两套结构揉成一套脆弱逻辑。
        if (itemElement.hasClass("roomgrid-item")) {
            return parseRoomGridItem(itemElement)
        }

        val iconFile = itemElement.selectFirst("img[alt]")
            ?.attr("alt")
            ?.trim()
            ?.takeIf { it.contains('.') }
            ?: return null

        val id = idFromFileRegex.find(iconFile)?.groupValues?.get(1)?.toIntOrNull() ?: return null

        val imgFile = iconToImgRegex.find(iconFile)?.let {
            "${it.groupValues[1]}${it.groupValues[2]}"
        } ?: iconFile

        val qualityElement = itemElement.selectFirst("span[data-quality]")
        val quality = qualityElement?.attr("data-quality")?.toIntOrNull() ?: 0
        val name = qualityElement?.text()?.trim().orEmpty().ifEmpty { "未知" }

        val desc = itemElement.selectFirst("small")?.text()?.trim().orEmpty()

        val source = itemElement.select("li")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }.joinToString("、")

        return RawItem(id, name, quality, iconFile, imgFile, desc, source)
    }

    private fun parseRoomGridItem(itemElement: Element): RawItem? {
        // 房间外观页当前仍以 HTML 负责图片结构：
        // - 主背景图来自 .roomgrid-image-bg
        // - 附属小图来自 .roomgrid-ui-item
        // 文本字段虽然也能从 HTML 读到，但在 enabled=true 时会被模块真值覆盖。
        val backgroundFile = itemElement.selectFirst(".roomgrid-image-bg img[alt]")
            ?.attr("alt")
            ?.trim()
            ?.takeIf { it.contains('.') }
            ?: return null

        val id = idFromFileRegex.find(backgroundFile)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val qualityElement = itemElement.selectFirst(".roomgrid-player-decoration [data-quality]")
        val quality = qualityElement?.attr("data-quality")?.toIntOrNull() ?: 0
        val name = qualityElement?.text()?.trim().orEmpty().ifEmpty { "未知" }
        val desc = itemElement.selectFirst(".roomgrid-desc")?.text()?.trim().orEmpty()
        val source = itemElement.select("li")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .joinToString("、")
        val extraPreviewFiles = itemElement.select(".roomgrid-ui-item").mapNotNull { uiItem ->
            val label = uiItem.selectFirst(".ui-label")?.text()?.trim().orEmpty()
            val fileName = uiItem.selectFirst("img[alt]")?.attr("alt")?.trim().orEmpty()
            if (label.isBlank() || !fileName.contains('.')) null else label to fileName
        }

        return RawItem(
            id = id,
            name = name,
            quality = quality,
            iconFile = backgroundFile,
            imgFile = backgroundFile,
            desc = desc,
            source = source,
            extraPreviewFiles = extraPreviewFiles
        )
    }

    private fun fetchModuleData(pageName: String): Map<Int, DecorationModuleData> {
        val config = moduleConfigByPage[pageName] ?: return emptyMap()
        if (!config.enabled) return emptyMap()

        // 注意：这里即使成功拿到模块数据，也只返回“按 id 覆盖的文本字段映射”。
        // 不负责分组，不负责图片，也不负责重新排序。
        // 如果未来有人想把这里扩成完整页面数据源，请先重新评估 section 丢失风险。
        return try {
            val encoded = URLEncoder.encode(config.modulePage, "UTF-8")
            val url = "$API?action=parse&page=$encoded&prop=wikitext&format=json"
            val body = WikiEngine.safeGet(url) ?: return emptyMap()
            val wikitext = json.parseToJsonElement(body).jsonObject["parse"]?.jsonObject
                ?.get("wikitext")?.jsonObject
                ?.get("*")?.jsonPrimitive?.content
                ?: return emptyMap()
            parseModuleData(wikitext)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * 解析模块 Lua 表，提取按 id 可覆盖的文本真值。
     *
     * 注意：这里故意不产出 section/category 信息。
     * 如果未来某个页面想完全脱离 HTML，只靠模块渲染，必须先确认该模块本身真的包含分类字段。
     * 否则会丢失页面分组。
        *
        * 当前解析策略是“尽量宽松，但只认少数字段”：
        * - id
        * - name
        * - quality
        * - get
        * - desc
        * - spdesc
        *
        * 好处：
        * - 同一套解析逻辑可复用于多个玩家装饰模块
        * - 页面侧不需要为每个 Data 模块单独写一套字段映射
        *
        * 限制：
        * - 如果某个模块使用了完全不同的字段命名，不能直接套用
        * - 如果 get 不是数组而是别的结构，也需要额外兼容
     */
    private fun parseModuleData(wikitext: String): Map<Int, DecorationModuleData> {
        val root = LuaTableParser.parseReturnArray(wikitext)
        return root.mapNotNull { value ->
            val fields = (value as? LuaTableParser.LuaValue.LuaObject)?.fields ?: return@mapNotNull null
            val id = (fields["id"] as? LuaTableParser.LuaValue.LuaNumber)?.value ?: return@mapNotNull null
            val name = (fields["name"] as? LuaTableParser.LuaValue.LuaString)?.value.orEmpty().trim()
            val quality = (fields["quality"] as? LuaTableParser.LuaValue.LuaNumber)?.value ?: 0
            val description = ((fields["desc"] as? LuaTableParser.LuaValue.LuaString)?.value)
                .orEmpty()
                .replace("<br />", "\n")
                .replace("<br/>", "\n")
                .replace("<br>", "\n")
                .trim()
            val specialDescription = ((fields["spdesc"] as? LuaTableParser.LuaValue.LuaString)?.value)
                .orEmpty()
                .replace("<br />", "\n")
                .replace("<br/>", "\n")
                .replace("<br>", "\n")
                .trim()
            val source = ((fields["get"] as? LuaTableParser.LuaValue.LuaArray)?.values ?: emptyList())
                .mapNotNull { (it as? LuaTableParser.LuaValue.LuaString)?.value?.trim() }
                .filter { it.isNotBlank() }
                .joinToString("、")

            id to DecorationModuleData(
                name = name,
                quality = quality,
                description = description,
                specialDescription = specialDescription,
                source = source
            )
        }.toMap()
    }
}

