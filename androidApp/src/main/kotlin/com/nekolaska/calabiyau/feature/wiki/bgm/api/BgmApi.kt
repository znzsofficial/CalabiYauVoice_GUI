package com.nekolaska.calabiyau.feature.wiki.bgm.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.bgm.model.BgmPage
import com.nekolaska.calabiyau.feature.wiki.bgm.parser.BgmParsers
import com.nekolaska.calabiyau.feature.wiki.bgm.source.BgmRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object BgmApi : CachedWikiApi<BgmPage>("BgmApi") {

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<BgmPage> =
        ioApiCall("获取 BGM 数据失败") {
            val result = BgmRemoteSource.fetchPage(forceRefresh)
                ?: return@ioApiCall ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val page = BgmParsers.parsePage(result.html)
            if (page.tracks.isEmpty()) {
                ApiResult.Error("未找到 BGM 数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
            }
        }
}
