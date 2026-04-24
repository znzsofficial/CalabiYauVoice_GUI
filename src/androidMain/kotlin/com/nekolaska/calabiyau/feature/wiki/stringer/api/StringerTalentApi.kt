package com.nekolaska.calabiyau.feature.wiki.stringer.api

import com.nekolaska.calabiyau.feature.wiki.stringer.model.TalentPage
import com.nekolaska.calabiyau.feature.wiki.stringer.parser.StringerTalentParsers
import com.nekolaska.calabiyau.feature.wiki.stringer.source.StringerRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StringerTalentApi {

    private const val PAGE_NAME = "超弦体天赋"

    private var cachedPage: TalentPage? = null

    fun clearMemoryCache() {
        cachedPage = null
    }

    suspend fun fetch(forceRefresh: Boolean = false): ApiResult<TalentPage> {
        if (!forceRefresh) {
            cachedPage?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedPage = it.value
        }
    }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<TalentPage> =
        withContext(Dispatchers.IO) {
            try {
                val result = StringerRemoteSource.fetchPageHtml(PAGE_NAME, "stringer_talent_page", forceRefresh)
                    ?: return@withContext ApiResult.Error(
                        "获取超弦体天赋失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )

                val page = StringerTalentParsers.parseHtml(result.html)
                if (page.sections.isEmpty()) {
                    ApiResult.Error("未找到超弦体天赋数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
                }
            } catch (e: Exception) {
                ApiResult.Error("获取超弦体天赋失败: ${e.message}", kind = e.toErrorKind())
            }
        }
}
