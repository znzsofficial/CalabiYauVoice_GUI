package com.nekolaska.calabiyau.feature.wiki.map.api

import com.nekolaska.calabiyau.feature.wiki.map.model.MapDetail
import com.nekolaska.calabiyau.feature.wiki.map.parser.MapDetailParsers
import com.nekolaska.calabiyau.feature.wiki.map.source.MapRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 地图详情 API（Android）。
 *
 * 通过 MediaWiki parse API 同时获取地图页面的 wikitext 与渲染 HTML。
 *
 * - `{{地图|...}}` 模板参数继续从 wikitext 读取，适合简介/支持模式/上线平台等稳定文本字段
 * - 地形图、概览图、更新改动历史改从 HTML 章节读取，避免依赖脆弱的文件名正则或 `#lst` 源码结构
 */
object MapDetailApi {

    /**
     * 获取地图详情。
     * @param mapName 地图名（如"风曳镇"）
     */
    suspend fun fetchMapDetail(
        mapName: String,
        forceRefresh: Boolean = false
    ): ApiResult<MapDetail> =
        withContext(Dispatchers.IO) {
            try {
                val sourceResult = MapRemoteSource.fetchMapDetailPayload(mapName, forceRefresh)
                    ?: return@withContext ApiResult.Error(
                        "请求失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )

                val detail = MapDetailParsers.parseMapWikitext(
                    mapName,
                    sourceResult.wikitext,
                    sourceResult.html
                ) ?: return@withContext ApiResult.Error(
                    "未找到地图信息模板",
                    kind = ErrorKind.NOT_FOUND
                )

                ApiResult.Success(
                    detail,
                    isOffline = sourceResult.isFromCache,
                    cacheAgeMs = sourceResult.ageMs
                )
            } catch (e: Exception) {
                ApiResult.Error("获取地图详情失败: ${e.message}", kind = e.toErrorKind())
            }
        }
}
