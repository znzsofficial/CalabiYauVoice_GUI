package com.nekolaska.calabiyau.feature.wiki.activity.api

import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityEntry
import com.nekolaska.calabiyau.feature.wiki.activity.parser.ActivityParsers
import com.nekolaska.calabiyau.feature.wiki.activity.parser.ParsedActivity
import com.nekolaska.calabiyau.feature.wiki.activity.source.ActivityRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 活动页 API（Android）。
 *
 * 解析 Wiki「活动」页面表格，提取活动标题、起止时间、简介与配图。
 */
object ActivityApi {

    private val thumbToOriginalRegex = Regex(
        """(https?://[^/]+/images/[^/]+)/thumb/([^/]+/[^/]+/[^/]+)/(?:\d+px-)?[^/?#]+"""
    )

    @Volatile
    private var cachedData: List<ActivityEntry>? = null

    fun clearMemoryCache() {
        cachedData = null
    }

    suspend fun fetchActivities(forceRefresh: Boolean = false): ApiResult<List<ActivityEntry>> {
        if (!forceRefresh) {
            cachedData?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedData = it.value
        }
    }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<ActivityEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val result = ActivityRemoteSource.fetchActivitiesPage(forceRefresh)
                    ?: return@withContext ApiResult.Error(
                        "请求失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )

                val parsedActivities = ActivityParsers.parseActivities(result.html)
                val activities = enrichActivitiesWithHighResImages(parsedActivities, forceRefresh)
                if (activities.isEmpty()) {
                    ApiResult.Error("未找到活动数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(
                        activities,
                        isOffline = result.isFromCache,
                        cacheAgeMs = result.ageMs
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error("获取活动失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    private suspend fun enrichActivitiesWithHighResImages(
        parsed: List<ParsedActivity>,
        forceRefresh: Boolean
    ): List<ActivityEntry> {
        if (parsed.isEmpty()) return emptyList()
        return coroutineScope {
            parsed.map { item ->
                async {
                    val directOriginal = toOriginalFromThumb(item.entry.imageUrl)
                    val detailImage = if (directOriginal == null) {
                        item.detailPageTitle?.let { ActivityRemoteSource.resolveHighResImageUrl(it, forceRefresh) }
                    } else {
                        null
                    }
                    item.entry.copy(imageUrl = directOriginal ?: detailImage ?: item.entry.imageUrl)
                }
            }.awaitAll()
        }
    }

    private fun toOriginalFromThumb(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val m = thumbToOriginalRegex.find(url) ?: return null
        return "${m.groupValues[1]}/${m.groupValues[2]}"
    }
}
