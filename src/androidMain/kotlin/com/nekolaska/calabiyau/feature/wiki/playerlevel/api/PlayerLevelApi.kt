package com.nekolaska.calabiyau.feature.wiki.playerlevel.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.feature.wiki.playerlevel.model.PlayerLevelPage
import com.nekolaska.calabiyau.feature.wiki.playerlevel.parser.PlayerLevelParsers
import com.nekolaska.calabiyau.feature.wiki.playerlevel.source.PlayerLevelRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PlayerLevelApi {

    init {
        MemoryCacheRegistry.register("PlayerLevelApi", ::clearMemoryCache)
    }

    @Volatile
    private var cachedPage: PlayerLevelPage? = null

    fun clearMemoryCache() {
        cachedPage = null
    }

    suspend fun fetch(forceRefresh: Boolean = false): ApiResult<PlayerLevelPage> {
        if (!forceRefresh) cachedPage?.let { return ApiResult.Success(it) }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedPage = it.value
        }
    }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<PlayerLevelPage> =
        withContext(Dispatchers.IO) {
            try {
                val result = PlayerLevelRemoteSource.fetchPage(forceRefresh)
                    ?: return@withContext ApiResult.Error("获取玩家等级数据失败，且无离线缓存", kind = ErrorKind.NETWORK)
                val page = PlayerLevelParsers.parseHtml(result.html, result.wikitext)
                if (page.levels.isEmpty() && page.rewards.isEmpty()) {
                    ApiResult.Error("未找到玩家等级数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
                }
            } catch (e: Exception) {
                ApiResult.Error("获取玩家等级数据失败: ${e.message}", kind = e.toErrorKind())
            }
        }
}
