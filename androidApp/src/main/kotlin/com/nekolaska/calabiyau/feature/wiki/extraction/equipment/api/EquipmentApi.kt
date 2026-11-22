package com.nekolaska.calabiyau.feature.wiki.extraction.equipment.api

import com.nekolaska.calabiyau.core.cache.KeyedCachedWikiApi
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.EquipmentKind
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.model.EquipmentPage
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.parser.EquipmentFormatException
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.source.EquipmentRemoteSource
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.source.EquipmentSnapshot
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

internal object EquipmentApi : KeyedCachedWikiApi<EquipmentKind, EquipmentPage>("ExtractionEquipment") {
    override suspend fun fetchFromCache(key: EquipmentKind) = load(key) { EquipmentRemoteSource.readCache(key) }
    override suspend fun fetchFromNetwork(key: EquipmentKind, forceRefresh: Boolean) = load(key) { EquipmentRemoteSource.fetch(key, forceRefresh) }

    internal suspend fun load(kind: EquipmentKind, source: suspend () -> EquipmentSnapshot?): ApiResult<EquipmentPage> =
        ioApiCall("${kind.title}加载失败") {
            try {
                source()?.let { ApiResult.Success(it.page, isOffline = it.offline, cacheAgeMs = it.ageMs) }
                    ?: ApiResult.Error("加载失败，暂无离线资料", kind = ErrorKind.NETWORK)
            } catch (error: EquipmentFormatException) {
                WikiParseLogger.warnMalformed("ExtractionEquipment.${kind.name}", error.message.orEmpty(), "")
                ApiResult.Error("暂时无法显示完整${kind.title}", kind = ErrorKind.PARSE)
            }
        }
}
