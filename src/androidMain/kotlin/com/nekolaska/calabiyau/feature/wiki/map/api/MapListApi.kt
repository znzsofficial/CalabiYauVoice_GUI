package com.nekolaska.calabiyau.feature.wiki.map.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.feature.wiki.map.model.GameModeData
import com.nekolaska.calabiyau.feature.wiki.map.parser.MapListParsers
import com.nekolaska.calabiyau.feature.wiki.map.source.MapRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * 地图列表 API（Android）。
 *
 * 通过 MediaWiki parse API 渲染 `{{游戏地图|模式名}}` 模板，
 * 从返回的 HTML 中提取地图名、链接和图片 URL。
 */
object MapListApi {

    init {
        MemoryCacheRegistry.register("MapListApi", ::clearMemoryCache)
    }

    @Volatile
    private var cachedModes: List<GameModeData>? = null

    fun clearMemoryCache() { cachedModes = null }

    val GAME_MODES: List<Pair<String, String>> = listOf(
        "爆破/团队乱斗" to "一般爆破",
        "无限团竞" to "无限团竞",
        "极限推进" to "极限推进",
        "大头乱斗" to "大头乱斗",
        "晶源感染" to "晶源感染",
        "极限刀战" to "极限刀战",
        "弦区争夺" to "弦区争夺",
        "枪王乱斗" to "枪王乱斗",
        "晶能冲突" to "晶能冲突",
    )

    suspend fun fetchAllModes(forceRefresh: Boolean = false): ApiResult<List<GameModeData>> = withContext(Dispatchers.IO) {
        if (!forceRefresh) cachedModes?.let { return@withContext ApiResult.Success(it) }
        try {
            val results = GAME_MODES.map { (display, template) ->
                async { fetchMode(display, template, forceRefresh) }
            }.awaitAll()

            val errors = results.filterIsInstance<ApiResult.Error>()
            if (errors.size == results.size) {
                return@withContext ApiResult.Error(
                    "所有模式加载失败: ${errors.first().message}",
                    kind = errors.first().kind
                )
            }

            val successes = results.filterIsInstance<ApiResult.Success<GameModeData>>()
            val isOffline = successes.any { it.isOffline }
            val maxAge = successes.maxOfOrNull { it.cacheAgeMs } ?: 0L

            val modes = results.mapIndexed { index, result ->
                when (result) {
                    is ApiResult.Success -> result.value
                    is ApiResult.Error -> {
                        val (display, template) = GAME_MODES[index]
                        GameModeData(display, template, emptyList())
                    }
                }
            }
            cachedModes = modes
            ApiResult.Success(modes, isOffline = isOffline, cacheAgeMs = maxAge)
        } catch (e: Exception) {
            ApiResult.Error("获取地图列表失败: ${e.message}", kind = e.toErrorKind())
        }
    }

    private suspend fun fetchMode(
        displayName: String,
        templateName: String,
        forceRefresh: Boolean
    ): ApiResult<GameModeData> {
        return try {
            val sourceResult = MapRemoteSource.fetchModeHtml(templateName, forceRefresh)
                ?: return ApiResult.Error(
                    "请求 $displayName 失败，且无离线缓存",
                    kind = ErrorKind.NETWORK
                )

            val maps = MapListParsers.parseMapsFromHtml(sourceResult.html)
            ApiResult.Success(
                GameModeData(displayName, templateName, maps),
                isOffline = sourceResult.isFromCache,
                cacheAgeMs = sourceResult.ageMs
            )
        } catch (e: Exception) {
            ApiResult.Error("加载 $displayName 失败: ${e.message}", kind = e.toErrorKind())
        }
    }
}
