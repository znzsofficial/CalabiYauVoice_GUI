package com.nekolaska.calabiyau.feature.wiki.navigation.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.navigation.model.NavSection
import com.nekolaska.calabiyau.feature.wiki.navigation.parser.NavigationMenuParsers
import com.nekolaska.calabiyau.feature.wiki.navigation.source.NavigationMenuRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object NavigationMenuApi : CachedWikiApi<List<NavSection>>("NavigationMenuApi") {

    suspend fun fetchNavigationSections(
        forceRefresh: Boolean = false,
        cacheOnly: Boolean = false,
        allowMemoryCache: Boolean = true
    ): ApiResult<List<NavSection>> = fetch(
        forceRefresh = forceRefresh,
        cacheOnly = cacheOnly,
        allowMemoryCache = allowMemoryCache
    )

    override suspend fun fetchFromCache(): ApiResult<List<NavSection>> =
        ioApiCall("读取导航缓存失败") {
            val sourceResult = NavigationMenuRemoteSource.loadCachedSidebar()
                ?: return@ioApiCall ApiResult.Error("没有导航缓存", kind = ErrorKind.NETWORK)
            val sections = NavigationMenuParsers.parseSidebar(sourceResult.sidebar)
            if (sections.isEmpty()) {
                ApiResult.Error("导航缓存为空", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(sections, isOffline = true, cacheAgeMs = sourceResult.ageMs)
            }
        }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<NavSection>> =
        ioApiCall("解析导航失败") {
            val sourceResult = NavigationMenuRemoteSource.fetchSidebar(forceRefresh)
                ?: return@ioApiCall ApiResult.Error("请求导航数据失败，且无离线缓存", kind = ErrorKind.NETWORK)
            val sections = NavigationMenuParsers.parseSidebar(sourceResult.sidebar)
            if (sections.isEmpty()) {
                ApiResult.Error("导航菜单为空", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(sections, isOffline = sourceResult.isFromCache, cacheAgeMs = sourceResult.ageMs)
            }
        }
}
