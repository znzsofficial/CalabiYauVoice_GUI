package com.nekolaska.calabiyau.feature.wiki.imprint.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.imprint.model.ImprintPage
import com.nekolaska.calabiyau.feature.wiki.imprint.parser.ImprintParsers
import com.nekolaska.calabiyau.feature.wiki.imprint.source.ImprintRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object ImprintApi : CachedWikiApi<ImprintPage>("ImprintApi") {

    override suspend fun fetchFromCache(): ApiResult<ImprintPage> =
        ioApiCall("获取印迹数据失败") {
            val result = ImprintRemoteSource.loadCachedPage()
                ?: return@ioApiCall ApiResult.Error("无离线缓存", kind = ErrorKind.NETWORK)
            val page = ImprintParsers.parseHtml(result.html)
            if (page.sections.none { it.imprints.isNotEmpty() }) {
                ApiResult.Error("未找到印迹数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = true, cacheAgeMs = result.ageMs)
            }
        }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<ImprintPage> =
        ioApiCall("获取印迹数据失败") {
            val result = ImprintRemoteSource.fetchPage(forceRefresh)
                ?: return@ioApiCall ApiResult.Error("获取印迹数据失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val page = ImprintParsers.parseHtml(result.html)
            if (page.sections.none { it.imprints.isNotEmpty() }) {
                ApiResult.Error("未找到印迹数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
            }
        }
}
