package com.nekolaska.calabiyau.feature.wiki.beginner.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.wiki.beginner.model.BeginnerGuidePage
import com.nekolaska.calabiyau.feature.wiki.beginner.source.BeginnerGuideRemoteSource
import com.nekolaska.calabiyau.feature.wiki.beginner.source.GuidePayloadException
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object BeginnerGuideApi : CachedWikiApi<BeginnerGuidePage>("BeginnerGuideApi") {
    override suspend fun fetchFromCache(): ApiResult<BeginnerGuidePage> = loadSource { BeginnerGuideRemoteSource.readCache() }
    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<BeginnerGuidePage> =
        loadSource { BeginnerGuideRemoteSource.fetch(forceRefresh) }

    internal suspend fun loadSource(load: suspend () -> OfflineCache.CacheResult?): ApiResult<BeginnerGuidePage> =
        ioApiCall("新人入坑指南加载失败") {
            try { parseSource(load()) }
            catch (e: GuidePayloadException) {
                WikiParseLogger.warnMalformed("BeginnerGuideApi", e.message.orEmpty(), "")
                ApiResult.Error("暂时无法显示完整指南", kind = ErrorKind.PARSE)
            }
        }

    internal fun parseSource(source: OfflineCache.CacheResult?): ApiResult<BeginnerGuidePage> {
        if (source == null) return ApiResult.Error("指南加载失败，暂无离线内容", kind = ErrorKind.NETWORK)
        val result = BeginnerGuideRemoteSource.parsePayload(source.payload)
        if (result == null || !result.canCache) {
            WikiParseLogger.warnMalformed("BeginnerGuideApi", result?.issues?.joinToString("；") ?: "响应格式异常", source.payload)
            return ApiResult.Error("暂时无法显示完整指南", kind = ErrorKind.PARSE)
        }
        return ApiResult.Success(result.page, isOffline = source.isFromCache, cacheAgeMs = source.ageMs)
    }
}
