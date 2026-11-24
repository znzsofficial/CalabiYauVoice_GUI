package com.nekolaska.calabiyau.feature.wiki.activity.api

import com.nekolaska.calabiyau.core.wiki.UnusableWikiHtml
import com.nekolaska.calabiyau.core.wiki.WikiArticleLoader
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityDetailBlock
import com.nekolaska.calabiyau.feature.wiki.activity.source.ActivityRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

internal object ActivityDetailApi {
    suspend fun fetch(title: String, forceRefresh: Boolean = false, cacheOnly: Boolean = false,
        load: WikiArticleLoader = WikiParseSource::loadArticle): ApiResult<List<ActivityDetailBlock>> =
        ioApiCall("获取活动详情失败") {
            val snapshot = try {
                ActivityRemoteSource.readDetail(title, forceRefresh, cacheOnly, load)
            } catch (_: UnusableWikiHtml) {
                return@ioApiCall ApiResult.Error("此活动暂未提供可读取的正文，可查看 Wiki 原文", kind = ErrorKind.PARSE)
            } ?: return@ioApiCall ApiResult.Error("未取得活动详情", kind = ErrorKind.NETWORK)
            ApiResult.Success(snapshot.value, isOffline = snapshot.isFromCache, cacheAgeMs = snapshot.ageMs)
        }
}
