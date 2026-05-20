package com.nekolaska.calabiyau.feature.wiki.meme.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.feature.wiki.meme.model.MemePage
import com.nekolaska.calabiyau.feature.wiki.meme.parser.MemeParsers
import com.nekolaska.calabiyau.feature.wiki.meme.source.MemeRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MemeApi {

    init {
        MemoryCacheRegistry.register("MemeApi", ::clearMemoryCache)
    }

    @Volatile
    private var cachedPage: MemePage? = null

    fun clearMemoryCache() {
        cachedPage = null
    }

    suspend fun fetch(forceRefresh: Boolean = false): ApiResult<MemePage> {
        if (!forceRefresh) {
            cachedPage?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedPage = it.value
        }
    }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<MemePage> =
        withContext(Dispatchers.IO) {
            try {
                val result = MemeRemoteSource.fetchPage(forceRefresh)
                    ?: return@withContext ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)
                val page = MemeParsers.parsePage(result.html)
                if (page.officialIssues.isEmpty() && page.editorEntries.isEmpty()) {
                    ApiResult.Error("未找到梗百科数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
                }
            } catch (e: Exception) {
                ApiResult.Error("获取梗百科失败: ${e.message}", kind = e.toErrorKind())
            }
        }
}
