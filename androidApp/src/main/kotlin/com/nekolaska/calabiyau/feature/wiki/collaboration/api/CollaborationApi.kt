package com.nekolaska.calabiyau.feature.wiki.collaboration.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.collaboration.model.CollaborationPage
import com.nekolaska.calabiyau.feature.wiki.collaboration.parser.CollaborationParsers
import com.nekolaska.calabiyau.feature.wiki.collaboration.source.CollaborationRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object CollaborationApi : CachedWikiApi<CollaborationPage>("CollaborationApi") {

    override suspend fun fetchFromCache(): ApiResult<CollaborationPage> =
        ioApiCall("读取联动缓存失败") {
            val result = CollaborationRemoteSource.loadCachedPage()
                ?: return@ioApiCall ApiResult.Error("没有联动缓存", kind = ErrorKind.NETWORK)
            val page = CollaborationParsers.parsePage(result.html)
            if (page.timelineYears.isEmpty() && page.events.isEmpty()) {
                ApiResult.Error("未找到联动缓存数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = true, cacheAgeMs = result.ageMs)
            }
        }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<CollaborationPage> =
        ioApiCall("获取联动数据失败") {
            val result = CollaborationRemoteSource.fetchPage(forceRefresh)
                ?: return@ioApiCall ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val page = CollaborationParsers.parsePage(result.html)
            if (page.timelineYears.isEmpty() && page.events.isEmpty()) {
                ApiResult.Error("未找到联动数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
            }
        }
}
