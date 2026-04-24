package com.nekolaska.calabiyau.feature.wiki.navigation.api

import com.nekolaska.calabiyau.feature.wiki.navigation.model.NavSection
import com.nekolaska.calabiyau.feature.wiki.navigation.parser.NavigationMenuParsers
import com.nekolaska.calabiyau.feature.wiki.navigation.source.NavigationMenuRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wiki 导航菜单提取 API（Android）。
 */
object NavigationMenuApi {

    suspend fun fetchNavigationSections(
        forceRefresh: Boolean = false
    ): ApiResult<List<NavSection>> = withContext(Dispatchers.IO) {
        try {
            val sourceResult = NavigationMenuRemoteSource.fetchSidebar(forceRefresh)
                ?: return@withContext ApiResult.Error(
                    "请求导航数据失败，且无离线缓存",
                    kind = ErrorKind.NETWORK
                )

            val sections = NavigationMenuParsers.parseSidebar(sourceResult.sidebar)
            if (sections.isEmpty()) {
                ApiResult.Error("导航菜单为空", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(
                    sections,
                    isOffline = sourceResult.isFromCache,
                    cacheAgeMs = sourceResult.ageMs
                )
            }
        } catch (e: Exception) {
            ApiResult.Error("解析导航失败: ${e.message}", kind = e.toErrorKind())
        }
    }
}
