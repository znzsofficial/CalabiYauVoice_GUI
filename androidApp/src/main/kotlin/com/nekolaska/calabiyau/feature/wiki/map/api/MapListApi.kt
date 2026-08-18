package com.nekolaska.calabiyau.feature.wiki.map.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.map.model.GameModeData
import com.nekolaska.calabiyau.feature.wiki.map.parser.MapListParsers
import com.nekolaska.calabiyau.feature.wiki.map.source.MapRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall
import data.toErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 地图列表 API（Android）。
 *
 * 通过 MediaWiki parse API 渲染 `{{游戏地图|模式名}}` 模板，
 * 从返回的 HTML 中提取地图名、链接和图片 URL。
 */
object MapListApi : CachedWikiApi<List<GameModeData>>("MapListApi") {

    val GAME_MODES: List<Pair<String, String>> = listOf(
        "爆破/团队乱斗" to "一般爆破",
        "排位爆破" to "排位爆破",
        "个人乱斗" to "个人乱斗",
        "无限团竞" to "无限团竞",
        "极限推进" to "极限推进",
        "超弦推进" to "超弦推进",
        "大头乱斗" to "大头乱斗",
        "晶源感染" to "晶源感染",
        "极限刀战" to "极限刀战",
        "弦区争夺" to "弦区争夺",
        "枪王乱斗" to "枪王乱斗",
        "晶能冲突" to "晶能冲突",
        "炸弹派对" to "炸弹派对",
    )

    suspend fun fetchAllModes(
        forceRefresh: Boolean = false,
        cacheOnly: Boolean = false,
        allowMemoryCache: Boolean = true
    ): ApiResult<List<GameModeData>> {
        return fetch(
            forceRefresh = forceRefresh,
            cacheOnly = cacheOnly,
            allowMemoryCache = allowMemoryCache
        )
    }

    override suspend fun fetchFromCache(): ApiResult<List<GameModeData>> = fetchFromSource(cacheOnly = true)

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<GameModeData>> =
        fetchFromSource(forceRefresh = forceRefresh)

    private suspend fun fetchFromSource(
        forceRefresh: Boolean = false,
        cacheOnly: Boolean = false
    ): ApiResult<List<GameModeData>> =
        ioApiCall("获取地图列表失败") {
            val results = coroutineScope {
                GAME_MODES.map { (display, template) ->
                    async { fetchMode(display, template, forceRefresh, cacheOnly) }
                }.awaitAll()
            }

            val errors = results.filterIsInstance<ApiResult.Error>()
            if (errors.size == results.size) {
                return@ioApiCall ApiResult.Error(
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
            ApiResult.Success(modes, isOffline = isOffline, cacheAgeMs = maxAge)
        }

    private suspend fun fetchMode(
        displayName: String,
        templateName: String,
        forceRefresh: Boolean,
        cacheOnly: Boolean
    ): ApiResult<GameModeData> {
        return try {
            val sourceResult = if (cacheOnly) {
                MapRemoteSource.loadCachedModeHtml(templateName)
            } else {
                MapRemoteSource.fetchModeHtml(templateName, forceRefresh)
            }
                ?: return ApiResult.Error("请求 $displayName 失败，且无离线缓存", kind = ErrorKind.NETWORK)

            val maps = MapListParsers.parseMapsFromHtml(sourceResult.html)
            ApiResult.Success(
                GameModeData(displayName, templateName, maps),
                isOffline = sourceResult.isFromCache,
                cacheAgeMs = sourceResult.ageMs
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error("加载 $displayName 失败: ${e.message}", kind = e.toErrorKind())
        }
    }
}
