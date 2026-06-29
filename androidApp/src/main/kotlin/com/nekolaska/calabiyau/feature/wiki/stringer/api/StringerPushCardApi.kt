package com.nekolaska.calabiyau.feature.wiki.stringer.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.stringer.model.CardPage
import com.nekolaska.calabiyau.feature.wiki.stringer.parser.StringerPushCardParsers
import com.nekolaska.calabiyau.feature.wiki.stringer.source.StringerRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object StringerPushCardApi : CachedWikiApi<CardPage>("StringerPushCardApi") {

    private const val PAGE_NAME = "战斗模式/超弦推进"

    override suspend fun fetchFromCache(): ApiResult<CardPage> =
        ioApiCall("获取超弦推进卡牌失败") {
            val result = StringerRemoteSource.loadCachedPageHtml(PAGE_NAME, "stringer_push_cards")
                ?: return@ioApiCall ApiResult.Error("无离线缓存", kind = ErrorKind.NETWORK)
            val page = StringerPushCardParsers.parseHtml(result.html)
            if (page.cards.isEmpty()) {
                ApiResult.Error("未找到超弦推进卡牌数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = true, cacheAgeMs = result.ageMs)
            }
        }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<CardPage> =
        ioApiCall("获取超弦推进卡牌失败") {
            val result = StringerRemoteSource.fetchPageHtml(PAGE_NAME, "stringer_push_cards", forceRefresh)
                ?: return@ioApiCall ApiResult.Error("获取超弦推进卡牌失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val page = StringerPushCardParsers.parseHtml(result.html)
            if (page.cards.isEmpty()) {
                ApiResult.Error("未找到超弦推进卡牌数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
            }
        }
}
