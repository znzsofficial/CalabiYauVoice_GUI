package com.nekolaska.calabiyau.feature.wiki.navigation.api

import com.nekolaska.calabiyau.feature.wiki.navigation.model.NavSection
import com.nekolaska.calabiyau.feature.wiki.navigation.parser.NavigationMenuParsers
import com.nekolaska.calabiyau.feature.wiki.navigation.source.NavigationMenuRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall

object NavigationMenuApi {

    suspend fun fetchNavigationSections(forceRefresh: Boolean = false): ApiResult<List<NavSection>> =
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
