package com.nekolaska.calabiyau.feature.wiki.stringer.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.stringer.model.TalentPage
import com.nekolaska.calabiyau.feature.wiki.stringer.parser.StringerTalentParsers
import com.nekolaska.calabiyau.feature.wiki.stringer.source.StringerRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object StringerTalentApi : CachedWikiApi<TalentPage>("StringerTalentApi") {

    private const val PAGE_NAME = "超弦体天赋"

    override suspend fun fetchFromCache(): ApiResult<TalentPage> =
        ioApiCall("读取超弦体天赋缓存失败") {
            val result = StringerRemoteSource.loadCachedPageHtml(PAGE_NAME, "stringer_talent_page")
                ?: return@ioApiCall ApiResult.Error("没有超弦体天赋缓存", kind = ErrorKind.NETWORK)
            val page = StringerTalentParsers.parseHtml(result.html)
            if (page.sections.isEmpty()) {
                ApiResult.Error("未找到超弦体天赋缓存数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
            }
        }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<TalentPage> =
        ioApiCall("获取超弦体天赋失败") {
            val result = StringerRemoteSource.fetchPageHtml(PAGE_NAME, "stringer_talent_page", forceRefresh)
                ?: return@ioApiCall ApiResult.Error("获取超弦体天赋失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val page = StringerTalentParsers.parseHtml(result.html)
            if (page.sections.isEmpty()) {
                ApiResult.Error("未找到超弦体天赋数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
            }
        }
}
