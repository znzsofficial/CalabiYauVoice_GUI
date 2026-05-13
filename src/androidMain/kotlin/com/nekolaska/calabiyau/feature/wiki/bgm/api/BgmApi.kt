package com.nekolaska.calabiyau.feature.wiki.bgm.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.feature.wiki.bgm.model.BgmPage
import com.nekolaska.calabiyau.feature.wiki.bgm.parser.BgmParsers
import com.nekolaska.calabiyau.feature.wiki.bgm.source.BgmRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BgmApi {

    init {
        MemoryCacheRegistry.register("BgmApi", ::clearMemoryCache)
    }

    @Volatile
    private var cachedPage: BgmPage? = null

    fun clearMemoryCache() {
        cachedPage = null
    }

    suspend fun fetch(forceRefresh: Boolean = false): ApiResult<BgmPage> {
        if (!forceRefresh) {
            cachedPage?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedPage = it.value
        }
    }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<BgmPage> =
        withContext(Dispatchers.IO) {
            try {
                val result = BgmRemoteSource.fetchPage(forceRefresh)
                    ?: return@withContext ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)
                val page = BgmParsers.parsePage(result.html)
                if (page.tracks.isEmpty()) {
                    ApiResult.Error("未找到 BGM 数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
                }
            } catch (e: Exception) {
                ApiResult.Error("获取 BGM 数据失败: ${e.message}", kind = e.toErrorKind())
            }
        }
}
