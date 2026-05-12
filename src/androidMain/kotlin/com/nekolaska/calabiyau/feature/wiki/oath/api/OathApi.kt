package com.nekolaska.calabiyau.feature.wiki.oath.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.feature.wiki.oath.model.OathPage
import com.nekolaska.calabiyau.feature.wiki.oath.parser.OathParsers
import com.nekolaska.calabiyau.feature.wiki.oath.source.OathRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object OathApi {

    init {
        MemoryCacheRegistry.register("OathApi", ::clearMemoryCache)
    }

    @Volatile
    private var cachedPage: OathPage? = null

    fun clearMemoryCache() {
        cachedPage = null
    }

    suspend fun fetch(forceRefresh: Boolean = false): ApiResult<OathPage> {
        if (!forceRefresh) {
            cachedPage?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedPage = it.value
        }
    }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<OathPage> =
        withContext(Dispatchers.IO) {
            try {
                val result = OathRemoteSource.fetchPage(forceRefresh)
                    ?: return@withContext ApiResult.Error("获取誓约数据失败，且无离线缓存", kind = ErrorKind.NETWORK)
                val page = OathParsers.parseHtml(result.html)
                if (page.levels.isEmpty() && page.birthdayGifts.isEmpty() && page.favorGifts.isEmpty() && page.bondSections.isEmpty()) {
                    ApiResult.Error("未找到誓约数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
                }
            } catch (e: Exception) {
                ApiResult.Error("获取誓约数据失败: ${e.message}", kind = e.toErrorKind())
            }
        }
}
