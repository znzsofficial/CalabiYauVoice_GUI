package com.nekolaska.calabiyau.feature.wiki.meme.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.meme.model.MemePage
import com.nekolaska.calabiyau.feature.wiki.meme.parser.MemeParsers
import com.nekolaska.calabiyau.feature.wiki.meme.source.MemeRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object MemeApi : CachedWikiApi<MemePage>("MemeApi") {

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<MemePage> =
        ioApiCall("获取梗百科失败") {
            val result = MemeRemoteSource.fetchPage(forceRefresh)
                ?: return@ioApiCall ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val page = MemeParsers.parsePage(result.html)
            if (page.officialIssues.isEmpty() && page.editorEntries.isEmpty()) {
                ApiResult.Error("未找到梗百科数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
            }
        }
}
