package com.nekolaska.calabiyau.feature.wiki.extraction.workshop.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.model.WorkshopPage
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.parser.WorkshopFormat
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.source.WorkshopRemoteSource
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.source.WorkshopSnapshot
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

internal object WorkshopApi : CachedWikiApi<WorkshopPage>("ExtractionWorkshopApi") {
    override suspend fun fetchFromCache() = load(true, WorkshopRemoteSource::readCache)
    override suspend fun fetchFromNetwork(forceRefresh: Boolean) = load(false) { WorkshopRemoteSource.fetch(forceRefresh) }

    internal suspend fun load(cacheOnly: Boolean, fetch: suspend () -> WorkshopSnapshot?): ApiResult<WorkshopPage> = ioApiCall("读取工坊失败") {
        try {
            val snapshot = fetch()
            when {
                snapshot != null -> ApiResult.Success(snapshot.page, isOffline = snapshot.offline, cacheAgeMs = snapshot.ageMs)
                cacheOnly -> ApiResult.Error("暂无工坊缓存", ErrorKind.NOT_FOUND)
                else -> ApiResult.Error("未能获取工坊资料", ErrorKind.NETWORK)
            }
        } catch (e: WorkshopFormat) {
            WikiParseLogger.warnMalformed("ExtractionWorkshop", e.message.orEmpty(), "")
            ApiResult.Error("暂时无法显示完整的工坊资料", ErrorKind.PARSE)
        }
    }
}
