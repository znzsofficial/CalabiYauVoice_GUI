package com.nekolaska.calabiyau.feature.wiki.tips.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.feature.wiki.tips.model.GameTipsSection
import com.nekolaska.calabiyau.feature.wiki.tips.parser.GameTipsParsers
import com.nekolaska.calabiyau.feature.wiki.tips.source.GameTipsRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GameTipsApi {

    init {
        MemoryCacheRegistry.register("GameTipsApi", ::clearMemoryCache)
    }

    @Volatile
    private var cachedData: List<GameTipsSection>? = null

    fun clearMemoryCache() {
        cachedData = null
    }

    suspend fun fetchGameTips(forceRefresh: Boolean = false): ApiResult<List<GameTipsSection>> {
        if (!forceRefresh) {
            cachedData?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedData = it.value
        }
    }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<GameTipsSection>> =
        withContext(Dispatchers.IO) {
            try {
                val sourceResult = GameTipsRemoteSource.fetchPage(forceRefresh)
                    ?: return@withContext ApiResult.Error(
                        "请求失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )

                val sections = GameTipsParsers.parseSections(sourceResult.html)
                if (sections.isEmpty()) {
                    ApiResult.Error("未找到游戏Tips数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(
                        sections,
                        isOffline = sourceResult.isFromCache,
                        cacheAgeMs = sourceResult.ageMs
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error("获取游戏Tips失败: ${e.message}", kind = e.toErrorKind())
            }
        }
}
