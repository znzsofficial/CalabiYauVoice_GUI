package com.nekolaska.calabiyau.feature.wiki.imprint.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.feature.wiki.imprint.model.ImprintPage
import com.nekolaska.calabiyau.feature.wiki.imprint.parser.ImprintParsers
import com.nekolaska.calabiyau.feature.wiki.imprint.source.ImprintRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImprintApi {

    init {
        MemoryCacheRegistry.register("ImprintApi", ::clearMemoryCache)
    }

    @Volatile
    private var cachedPage: ImprintPage? = null

    fun clearMemoryCache() {
        cachedPage = null
    }

    suspend fun fetch(
        forceRefresh: Boolean = false,
        cacheOnly: Boolean = false,
        allowMemoryCache: Boolean = true
    ): ApiResult<ImprintPage> {
        if (!forceRefresh && allowMemoryCache) {
            cachedPage?.let { return ApiResult.Success(it) }
        }
        return if (cacheOnly) fetchFromCache() else fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedPage = it.value
        }
    }

    private suspend fun fetchFromCache(): ApiResult<ImprintPage> =
        withContext(Dispatchers.IO) {
            try {
                val result = ImprintRemoteSource.loadCachedPage()
                    ?: return@withContext ApiResult.Error("无离线缓存", kind = ErrorKind.NETWORK)
                val page = ImprintParsers.parseHtml(result.html)
                if (page.sections.none { it.imprints.isNotEmpty() }) {
                    ApiResult.Error("未找到印迹数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    cachedPage = page
                    ApiResult.Success(page, isOffline = true, cacheAgeMs = result.ageMs)
                }
            } catch (e: Exception) {
                ApiResult.Error("获取印迹数据失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<ImprintPage> =
        withContext(Dispatchers.IO) {
            try {
                val result = ImprintRemoteSource.fetchPage(forceRefresh)
                    ?: return@withContext ApiResult.Error("获取印迹数据失败，且无离线缓存", kind = ErrorKind.NETWORK)
                val page = ImprintParsers.parseHtml(result.html)
                if (page.sections.none { it.imprints.isNotEmpty() }) {
                    ApiResult.Error("未找到印迹数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
                }
            } catch (e: Exception) {
                ApiResult.Error("获取印迹数据失败: ${e.message}", kind = e.toErrorKind())
            }
        }
}
