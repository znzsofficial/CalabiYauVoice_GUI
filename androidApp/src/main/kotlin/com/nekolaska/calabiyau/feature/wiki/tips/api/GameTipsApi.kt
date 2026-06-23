package com.nekolaska.calabiyau.feature.wiki.tips.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.tips.model.GameTipsSection
import com.nekolaska.calabiyau.feature.wiki.tips.parser.GameTipsParsers
import com.nekolaska.calabiyau.feature.wiki.tips.source.GameTipsRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object GameTipsApi : CachedWikiApi<List<GameTipsSection>>("GameTipsApi") {

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<GameTipsSection>> =
        ioApiCall("获取游戏Tips失败") {
            val sourceResult = GameTipsRemoteSource.fetchPage(forceRefresh)
                ?: return@ioApiCall ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val sections = GameTipsParsers.parseSections(sourceResult.html)
            if (sections.isEmpty()) {
                ApiResult.Error("未找到游戏Tips数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(sections, isOffline = sourceResult.isFromCache, cacheAgeMs = sourceResult.ageMs)
            }
        }
}
