package com.nekolaska.calabiyau.feature.wiki.meow.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.meow.model.MeowLanguageSection
import com.nekolaska.calabiyau.feature.wiki.meow.parser.MeowLanguageParsers
import com.nekolaska.calabiyau.feature.wiki.meow.source.MeowLanguageRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object MeowLanguageApi : CachedWikiApi<List<MeowLanguageSection>>("MeowLanguageApi") {

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<MeowLanguageSection>> =
        ioApiCall("获取喵言喵语失败") {
            val sourceResult = MeowLanguageRemoteSource.fetchPage(forceRefresh)
                ?: return@ioApiCall ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val sections = MeowLanguageParsers.parseSections(sourceResult.html)
            if (sections.isEmpty()) {
                ApiResult.Error("未找到喵言喵语数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(sections, isOffline = sourceResult.isFromCache, cacheAgeMs = sourceResult.ageMs)
            }
        }
}
