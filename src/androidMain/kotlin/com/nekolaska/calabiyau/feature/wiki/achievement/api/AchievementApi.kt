package com.nekolaska.calabiyau.feature.wiki.achievement.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.feature.wiki.achievement.model.AchievementPage
import com.nekolaska.calabiyau.feature.wiki.achievement.parser.AchievementParsers
import com.nekolaska.calabiyau.feature.wiki.achievement.source.AchievementRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AchievementApi {

    init {
        MemoryCacheRegistry.register("AchievementApi", ::clearMemoryCache)
    }

    @Volatile
    private var cachedPage: AchievementPage? = null

    fun clearMemoryCache() {
        cachedPage = null
    }

    suspend fun fetch(forceRefresh: Boolean = false): ApiResult<AchievementPage> {
        if (!forceRefresh) cachedPage?.let { return ApiResult.Success(it) }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedPage = it.value
        }
    }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<AchievementPage> =
        withContext(Dispatchers.IO) {
            try {
                val result = AchievementRemoteSource.fetchPage(forceRefresh)
                    ?: return@withContext ApiResult.Error("获取成就数据失败，且无离线缓存", kind = ErrorKind.NETWORK)
                val page = AchievementParsers.parseHtml(result.html)
                if (page.sections.none { it.achievements.isNotEmpty() }) {
                    ApiResult.Error("未找到成就数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
                }
            } catch (e: Exception) {
                ApiResult.Error("获取成就数据失败: ${e.message}", kind = e.toErrorKind())
            }
        }
}
