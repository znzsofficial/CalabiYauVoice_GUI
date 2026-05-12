package com.nekolaska.calabiyau.feature.wiki.stringer.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.feature.wiki.stringer.model.CardPage
import com.nekolaska.calabiyau.feature.wiki.stringer.parser.StringerPushCardParsers
import com.nekolaska.calabiyau.feature.wiki.stringer.source.StringerRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StringerPushCardApi {

    init {
        MemoryCacheRegistry.register("StringerPushCardApi", ::clearMemoryCache)
    }

    private const val PAGE_NAME = "战斗模式/超弦推进"

    private var cachedPage: CardPage? = null

    fun clearMemoryCache() {
        cachedPage = null
    }

    suspend fun fetch(forceRefresh: Boolean = false): ApiResult<CardPage> {
        if (!forceRefresh) {
            cachedPage?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedPage = it.value
        }
    }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<CardPage> = withContext(Dispatchers.IO) {
        try {
            val result = StringerRemoteSource.fetchPageHtml(PAGE_NAME, "stringer_push_cards", forceRefresh)
                ?: return@withContext ApiResult.Error(
                    "获取超弦推进卡牌失败，且无离线缓存",
                    kind = ErrorKind.NETWORK
                )

            val page = StringerPushCardParsers.parseHtml(result.html)
            if (page.cards.isEmpty()) {
                ApiResult.Error("未找到超弦推进卡牌数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
            }
        } catch (e: Exception) {
            ApiResult.Error("获取超弦推进卡牌失败: ${e.message}", kind = e.toErrorKind())
        }
    }
}
