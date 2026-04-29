package com.nekolaska.calabiyau.feature.wiki.decoration.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationSection
import com.nekolaska.calabiyau.feature.wiki.decoration.parser.PlayerDecorationParsers
import com.nekolaska.calabiyau.feature.wiki.decoration.source.PlayerDecorationRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    init {
        MemoryCacheRegistry.register("PlayerDecorationApi", ::clearMemoryCache)
    }

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
        "登场特效" to DecorationModuleConfig("模块:玩家装饰/LoginFXData")
    )

    private val cacheMap = mutableMapOf<String, List<DecorationSection>>()

    fun clearMemoryCache() {
        cacheMap.clear()
    }

    private fun getCachedSections(pageName: String, forceRefresh: Boolean): List<DecorationSection>? {
        return if (forceRefresh) null else cacheMap[pageName]
    }

    /**
     * 获取装饰数据（带缓存）。
     * @param pageName Wiki 页面名，如 "基板"、"封装"、"聊天气泡"、"头套"、"超弦体动作"、"头像框"
     */
    suspend fun fetch(
        pageName: String,
        forceRefresh: Boolean = false
    ): ApiResult<List<DecorationSection>> {
        getCachedSections(pageName, forceRefresh)?.let { return ApiResult.Success(it) }
        return fetchFromNetwork(pageName, forceRefresh).also {
            if (it is ApiResult.Success) cacheMap[pageName] = it.value
        }
    }

    private suspend fun fetchFromNetwork(
        pageName: String,
        forceRefresh: Boolean
    ): ApiResult<List<DecorationSection>> = withContext(Dispatchers.IO) {
        try {
            val sourceResult = PlayerDecorationRemoteSource.fetchPageHtml(pageName, forceRefresh)
                ?: return@withContext ApiResult.Error(
                    "获取页面失败，且无离线缓存",
                    kind = ErrorKind.NETWORK
                )

            val rawSections = PlayerDecorationParsers.parseHtml(sourceResult.html)
            if (rawSections.isEmpty()) {
                return@withContext ApiResult.Error(
                    "未找到${pageName}数据",
                    kind = ErrorKind.NOT_FOUND
                )
            }

            val moduleDataMap = fetchModuleData(pageName)
            val fileNames = PlayerDecorationParsers.extractFileNames(rawSections)
            val urlMap = WikiEngine.fetchImageUrls(fileNames.toList())

            val sections = PlayerDecorationParsers.mapSections(
                rawSections = rawSections,
                urlMap = urlMap,
                moduleDataMap = moduleDataMap,
                mapper = { title, items -> DecorationSection(title, items) }
            )

            return@withContext if (sections.isEmpty()) {
                ApiResult.Error("未找到${pageName}内容", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(
                    sections,
                    isOffline = sourceResult.isFromCache,
                    cacheAgeMs = sourceResult.ageMs
                )
            }
        } catch (e: Exception) {
            ApiResult.Error("网络异常: ${e.message}", kind = e.toErrorKind())
        }
    }

    private fun fetchModuleData(pageName: String): Map<Int, PlayerDecorationParsers.DecorationModuleData> {
        val config = moduleConfigByPage[pageName]?.takeIf { it.enabled } ?: return emptyMap()

        return try {
            val wikitext = PlayerDecorationRemoteSource.fetchModuleWikitext(config.modulePage)
                ?: return emptyMap()
            PlayerDecorationParsers.parseModuleData(wikitext)
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
