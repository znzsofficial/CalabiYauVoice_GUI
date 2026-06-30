package com.nekolaska.calabiyau.feature.wiki.submission.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.submission.model.SubmissionPage
import com.nekolaska.calabiyau.feature.wiki.submission.parser.SubmissionParsers
import com.nekolaska.calabiyau.feature.wiki.submission.source.SubmissionRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object SubmissionApi : CachedWikiApi<SubmissionPage>("SubmissionApi") {

    override suspend fun fetchFromCache(): ApiResult<SubmissionPage> =
        ioApiCall("读取投稿作品缓存失败") {
            val result = SubmissionRemoteSource.loadCachedPage()
                ?: return@ioApiCall ApiResult.Error("没有投稿作品缓存", kind = ErrorKind.NETWORK)
            val entries = SubmissionParsers.parseEntries(result.html)
            if (entries.isEmpty()) {
                ApiResult.Error("未找到投稿作品缓存数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(SubmissionPage(entries), isOffline = true, cacheAgeMs = result.ageMs)
            }
        }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<SubmissionPage> =
        ioApiCall("获取投稿作品失败") {
            val result = SubmissionRemoteSource.fetchPage(forceRefresh)
                ?: return@ioApiCall ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val entries = SubmissionParsers.parseEntries(result.html)
            if (entries.isEmpty()) {
                ApiResult.Error("未找到投稿作品数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(SubmissionPage(entries), isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
            }
        }
}
