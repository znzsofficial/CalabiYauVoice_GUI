package com.nekolaska.calabiyau.feature.wiki.announcement.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.feature.wiki.announcement.model.Announcement
import com.nekolaska.calabiyau.feature.wiki.announcement.parser.AnnouncementParsers
import com.nekolaska.calabiyau.feature.wiki.announcement.source.AnnouncementRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 公告资讯 API（Android）。
 *
 * 通过 Semantic MediaWiki ask API 获取公告列表，
 * 包含标题、时间、B站链接和官网链接。
 */
object AnnouncementApi {

    init {
        MemoryCacheRegistry.register("AnnouncementApi", ::clearMemoryCache)
    }

    @Volatile
    private var cachedData: List<Announcement>? = null

    fun clearMemoryCache() { cachedData = null }

    suspend fun fetchAnnouncements(
        limit: Int = 50,
        forceRefresh: Boolean = false
    ): ApiResult<List<Announcement>> {
        if (!forceRefresh) {
            cachedData?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(limit, forceRefresh).also {
            if (it is ApiResult.Success) cachedData = it.value
        }
    }

    private suspend fun fetchFromNetwork(
        limit: Int,
        forceRefresh: Boolean
    ): ApiResult<List<Announcement>> =
        withContext(Dispatchers.IO) {
            try {
                val sourceResult = AnnouncementRemoteSource.fetchAnnouncements(limit, forceRefresh)
                    ?: return@withContext ApiResult.Error(
                        "请求失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )

                val announcements = AnnouncementParsers.parseAnnouncements(sourceResult.results)
                if (announcements.isEmpty()) {
                    ApiResult.Error("未找到公告数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(
                        announcements,
                        isOffline = sourceResult.isFromCache,
                        cacheAgeMs = sourceResult.ageMs
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error("获取公告失败: ${e.message}", kind = e.toErrorKind())
            }
        }
}
