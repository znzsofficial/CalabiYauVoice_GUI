package com.nekolaska.calabiyau.feature.wiki.meow.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.feature.wiki.meow.model.MeowLanguageSection
import com.nekolaska.calabiyau.feature.wiki.meow.parser.MeowLanguageParsers
import com.nekolaska.calabiyau.feature.wiki.meow.source.MeowLanguageRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MeowLanguageApi {

    init {
        MemoryCacheRegistry.register("MeowLanguageApi", ::clearMemoryCache)
    }

    @Volatile
    private var cachedData: List<MeowLanguageSection>? = null

    fun clearMemoryCache() {
        cachedData = null
    }

    suspend fun fetchMeowLanguage(forceRefresh: Boolean = false): ApiResult<List<MeowLanguageSection>> {
        if (!forceRefresh) {
            cachedData?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedData = it.value
        }
    }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<MeowLanguageSection>> =
        withContext(Dispatchers.IO) {
            try {
                val sourceResult = MeowLanguageRemoteSource.fetchPage(forceRefresh)
                    ?: return@withContext ApiResult.Error(
                        "请求失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )

                val sections = MeowLanguageParsers.parseSections(sourceResult.html)
                if (sections.isEmpty()) {
                    ApiResult.Error("未找到喵言喵语数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(
                        sections,
                        isOffline = sourceResult.isFromCache,
                        cacheAgeMs = sourceResult.ageMs
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error("获取喵言喵语失败: ${e.message}", kind = e.toErrorKind())
            }
        }
}
