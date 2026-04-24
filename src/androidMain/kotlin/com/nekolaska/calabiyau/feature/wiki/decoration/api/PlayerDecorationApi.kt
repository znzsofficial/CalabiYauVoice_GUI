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

/** 玩家装饰通用 API（Android）。 */
object PlayerDecorationApi {

    init {
        MemoryCacheRegistry.register("PlayerDecorationApi", ::clearMemoryCache)
    }

    private data class DecorationModuleConfig(
        val modulePage: String,
        val enabled: Boolean = false
    )

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

    fun clearMemoryCache() { cacheMap.clear() }

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

            if (sections.isEmpty()) {
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
        val config = moduleConfigByPage[pageName] ?: return emptyMap()
        if (!config.enabled) return emptyMap()

        return try {
            val wikitext = PlayerDecorationRemoteSource.fetchModuleWikitext(config.modulePage)
                ?: return emptyMap()
            PlayerDecorationParsers.parseModuleData(wikitext)
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
