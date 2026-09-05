package com.nekolaska.calabiyau.feature.wiki.activity.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityEntry
import com.nekolaska.calabiyau.feature.wiki.activity.parser.ActivityParsers
import com.nekolaska.calabiyau.feature.wiki.activity.parser.ParsedActivity
import com.nekolaska.calabiyau.feature.wiki.activity.source.ActivityRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object ActivityApi : CachedWikiApi<List<ActivityEntry>>("ActivityApi") {

    override suspend fun fetchFromCache(): ApiResult<List<ActivityEntry>> =
        ioApiCall("读取活动缓存失败") {
            val result = ActivityRemoteSource.loadCachedActivitiesPage()
                ?: return@ioApiCall ApiResult.Error("没有活动缓存", kind = ErrorKind.NETWORK)
            val parsedActivities = ActivityParsers.parseActivities(result.html)
            val activities = enrichActivitiesWithHighResImages(
                parsedActivities,
                forceRefresh = false,
                cacheOnly = true
            )
            if (activities.isEmpty()) {
                ApiResult.Error("未找到活动缓存数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(activities, isOffline = true, cacheAgeMs = result.ageMs)
            }
        }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<ActivityEntry>> =
        ioApiCall("获取活动失败") {
            val result = ActivityRemoteSource.fetchActivitiesPage(forceRefresh)
                ?: return@ioApiCall ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val parsedActivities = ActivityParsers.parseActivities(result.html)
            val activities = enrichActivitiesWithHighResImages(
                parsedActivities,
                forceRefresh,
                cacheOnly = result.isFromCache
            )
            if (activities.isEmpty()) {
                ApiResult.Error("未找到活动数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(activities, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
            }
        }

    private suspend fun enrichActivitiesWithHighResImages(
        parsed: List<ParsedActivity>,
        forceRefresh: Boolean,
        cacheOnly: Boolean
    ): List<ActivityEntry> {
        if (parsed.isEmpty()) return emptyList()
        return coroutineScope {
            parsed.map { item ->
                async {
                    val directOriginal = WikiImageUrls.originalFromThumbnail(item.entry.imageUrl)
                    val detailImage = if (!cacheOnly && directOriginal == null) {
                        item.detailPageTitle?.let { ActivityRemoteSource.resolveHighResImageUrl(it, forceRefresh) }
                    } else { null }
                    item.entry.copy(imageUrl = directOriginal ?: detailImage ?: item.entry.imageUrl)
                }
            }.awaitAll()
        }
    }
}
