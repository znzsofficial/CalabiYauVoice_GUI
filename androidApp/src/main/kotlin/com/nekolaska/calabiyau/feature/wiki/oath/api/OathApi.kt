package com.nekolaska.calabiyau.feature.wiki.oath.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.oath.model.OathPage
import com.nekolaska.calabiyau.feature.wiki.oath.parser.OathParsers
import com.nekolaska.calabiyau.feature.wiki.oath.source.OathRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object OathApi : CachedWikiApi<OathPage>("OathApi") {

    override suspend fun fetchFromCache(): ApiResult<OathPage> =
        ioApiCall("获取誓约数据失败") {
            val result = OathRemoteSource.loadCachedPage()
                ?: return@ioApiCall ApiResult.Error("无离线缓存", kind = ErrorKind.NETWORK)
            val page = OathParsers.parseHtml(result.html)
            if (page.levels.isEmpty() && page.birthdayGifts.isEmpty() && page.favorGifts.isEmpty() && page.bondSections.isEmpty()) {
                ApiResult.Error("未找到誓约数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = true, cacheAgeMs = result.ageMs)
            }
        }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<OathPage> =
        ioApiCall("获取誓约数据失败") {
            val result = OathRemoteSource.fetchPage(forceRefresh)
                ?: return@ioApiCall ApiResult.Error("获取誓约数据失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val page = OathParsers.parseHtml(result.html)
            if (page.levels.isEmpty() && page.birthdayGifts.isEmpty() && page.favorGifts.isEmpty() && page.bondSections.isEmpty()) {
                ApiResult.Error("未找到誓约数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
            }
        }
}
