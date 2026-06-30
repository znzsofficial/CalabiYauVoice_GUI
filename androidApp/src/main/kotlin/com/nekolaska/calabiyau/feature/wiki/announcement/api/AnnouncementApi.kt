package com.nekolaska.calabiyau.feature.wiki.announcement.api

import com.nekolaska.calabiyau.core.cache.KeyedCachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.announcement.model.Announcement
import com.nekolaska.calabiyau.feature.wiki.announcement.parser.AnnouncementParsers
import com.nekolaska.calabiyau.feature.wiki.announcement.source.AnnouncementRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

/**
 * 公告资讯 API（Android）。
 *
 * 通过 Semantic MediaWiki ask API 获取公告列表，
 * 包含标题、时间、B站链接和官网链接。
 */
object AnnouncementApi : KeyedCachedWikiApi<Int, List<Announcement>>("AnnouncementApi") {

    suspend fun fetchAnnouncements(
        limit: Int = 50,
        forceRefresh: Boolean = false,
        cacheOnly: Boolean = false,
        allowMemoryCache: Boolean = true
    ): ApiResult<List<Announcement>> = fetch(
        limit,
        forceRefresh = forceRefresh,
        cacheOnly = cacheOnly,
        allowMemoryCache = allowMemoryCache
    )

    override suspend fun fetchFromCache(key: Int): ApiResult<List<Announcement>> =
        ioApiCall("读取公告缓存失败") {
            val sourceResult = AnnouncementRemoteSource.loadCachedAnnouncements(key)
                ?: return@ioApiCall ApiResult.Error("没有公告缓存", kind = ErrorKind.NETWORK)

            val announcements = AnnouncementParsers.parseAnnouncements(sourceResult.results)
            if (announcements.isEmpty()) {
                ApiResult.Error("未找到公告缓存数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(announcements, isOffline = true, cacheAgeMs = sourceResult.ageMs)
            }
        }

    override suspend fun fetchFromNetwork(key: Int, forceRefresh: Boolean): ApiResult<List<Announcement>> =
        ioApiCall("获取公告失败") {
            val sourceResult = AnnouncementRemoteSource.fetchAnnouncements(key, forceRefresh)
                ?: return@ioApiCall ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)

            val announcements = AnnouncementParsers.parseAnnouncements(sourceResult.results)
            if (announcements.isEmpty()) {
                ApiResult.Error("未找到公告数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(announcements, isOffline = sourceResult.isFromCache, cacheAgeMs = sourceResult.ageMs)
            }
        }
}
