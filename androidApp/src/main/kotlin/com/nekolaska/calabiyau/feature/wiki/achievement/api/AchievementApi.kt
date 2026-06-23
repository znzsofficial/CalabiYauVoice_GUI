package com.nekolaska.calabiyau.feature.wiki.achievement.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.achievement.model.AchievementPage
import com.nekolaska.calabiyau.feature.wiki.achievement.parser.AchievementParsers
import com.nekolaska.calabiyau.feature.wiki.achievement.source.AchievementRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object AchievementApi : CachedWikiApi<AchievementPage>("AchievementApi") {

    override suspend fun fetchFromCache(): ApiResult<AchievementPage> =
        ioApiCall("获取成就数据失败") {
            val result = AchievementRemoteSource.loadCachedPage()
                ?: return@ioApiCall ApiResult.Error("无离线缓存", kind = ErrorKind.NETWORK)
            val page = AchievementParsers.parseHtml(result.html)
            if (page.sections.none { it.achievements.isNotEmpty() }) {
                ApiResult.Error("未找到成就数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = true, cacheAgeMs = result.ageMs)
            }
        }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<AchievementPage> =
        ioApiCall("获取成就数据失败") {
            val result = AchievementRemoteSource.fetchPage(forceRefresh)
                ?: return@ioApiCall ApiResult.Error("获取成就数据失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val page = AchievementParsers.parseHtml(result.html)
            if (page.sections.none { it.achievements.isNotEmpty() }) {
                ApiResult.Error("未找到成就数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
            }
        }
}
