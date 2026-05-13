package com.nekolaska.calabiyau.feature.wiki.collaboration.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.feature.wiki.collaboration.model.CollaborationPage
import com.nekolaska.calabiyau.feature.wiki.collaboration.parser.CollaborationParsers
import com.nekolaska.calabiyau.feature.wiki.collaboration.source.CollaborationRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CollaborationApi {

    init {
        MemoryCacheRegistry.register("CollaborationApi", ::clearMemoryCache)
    }

    @Volatile
    private var cachedPage: CollaborationPage? = null

    fun clearMemoryCache() {
        cachedPage = null
    }

    suspend fun fetch(forceRefresh: Boolean = false): ApiResult<CollaborationPage> {
        if (!forceRefresh) {
            cachedPage?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedPage = it.value
        }
    }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<CollaborationPage> =
        withContext(Dispatchers.IO) {
            try {
                val result = CollaborationRemoteSource.fetchPage(forceRefresh)
                    ?: return@withContext ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)
                val page = CollaborationParsers.parsePage(result.html)
                if (page.timelineYears.isEmpty() && page.events.isEmpty()) {
                    ApiResult.Error("未找到联动数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
                }
            } catch (e: Exception) {
                ApiResult.Error("获取联动数据失败: ${e.message}", kind = e.toErrorKind())
            }
        }
}
