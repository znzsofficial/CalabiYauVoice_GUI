package com.nekolaska.calabiyau.data

import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * 地图详情 API（Android）。
 *
 * 通过 MediaWiki parse API 获取地图页面的 wikitext，
 * 解析 `{{地图|...}}` 模板参数和概览图片。
 */
object MapDetailApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    /** 地图详情 */
    data class MapDetail(
        val name: String,
        val description: String,    // 简介
        val supportedModes: String, // 支持模式
        val platforms: String,      // 上线平台
        val terrainMapUrl: String?, // 地形图 URL
        val galleryUrls: List<String> // 概览图 URL 列表
    )


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
                val encoded = URLEncoder.encode(mapName, "UTF-8")
                val url = "$API?action=parse&page=$encoded&prop=wikitext&format=json"

                val result = OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.MAP_DETAIL,
                    key = mapName,
                    forceRefresh = forceRefresh
                ) { WikiEngine.safeGet(url) }
                    ?: return@withContext ApiResult.Error(
                        "请求失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )
                val body = result.json

                val json = SharedJson.parseToJsonElement(body).jsonObject
                val parseObj = json["parse"]?.jsonObject
                    ?: return@withContext ApiResult.Error(
                        "无法获取页面内容",
                        kind = ErrorKind.PARSE
                    )
                val wikitext = parseObj["wikitext"]?.jsonObject?.get("*")
                    ?.jsonPrimitive?.content
                    ?: return@withContext ApiResult.Error(
                        "无法获取页面内容",
                        kind = ErrorKind.PARSE
                    )

                val detail = parseMapWikitext(mapName, wikitext)
                    ?: return@withContext ApiResult.Error(
                        "未找到地图信息模板",
                        kind = ErrorKind.NOT_FOUND
                    )

                ApiResult.Success(
                    detail,
                    isOffline = result.isFromCache,
                    cacheAgeMs = result.ageMs
                )
            } catch (e: Exception) {
                ApiResult.Error("获取地图详情失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    private fun parseMapWikitext(
        name: String,
        wikitext: String
    ): MapDetail? {
        // 解析 {{地图|...}} 模板
        val mapContent = extractTemplate(wikitext, "地图") ?: return null
        val params = parseTemplateParams(mapContent)

        // 提取地形图文件名（==地形图== 下的 [[File:xxx]]）
        val terrainFile = Regex("""\[\[File:(地图-[^\]|]+地形图[^\]|]*)""")
            .find(wikitext)?.groupValues?.get(1)

        // 提取概览图文件名（{{Swiper|...}} 中的图片）
        val swiperContent = extractTemplate(wikitext, "Swiper")
        val galleryFiles = if (swiperContent != null) {
            Regex("""\[\[File:([^\]|]+)""").findAll(swiperContent)
                .map { it.groupValues[1] }.toList()
        } else emptyList()

        // 批量获取图片 URL
        val allFiles = buildList {
            if (terrainFile != null) add(terrainFile)
            addAll(galleryFiles)
        }
        val imageUrls = if (allFiles.isNotEmpty()) fetchImageUrls(allFiles) else emptyMap()

        return MapDetail(
            name = name,
            description = params["简介"] ?: "",
            supportedModes = params["支持模式"] ?: "",
            platforms = params["上线平台"] ?: "",
            terrainMapUrl = terrainFile?.let { imageUrls[it] },
            galleryUrls = galleryFiles.mapNotNull { imageUrls[it] }
        )
    }

    /** 批量获取图片 URL */
    private fun fetchImageUrls(fileNames: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        fileNames.chunked(50).forEach { chunk ->
            val titles = chunk.joinToString("|") { "文件:$it" }
            val encoded = URLEncoder.encode(titles, "UTF-8")
            val url = "$API?action=query&titles=$encoded&prop=imageinfo&iiprop=url&format=json"
            val body = WikiEngine.safeGet(url) ?: return@forEach

            val json = SharedJson.parseToJsonElement(body).jsonObject
            val pages = json["query"]?.jsonObject?.get("pages")?.jsonObject ?: return@forEach

            pages.values.forEach { pageValue ->
                val pageObj = pageValue.jsonObject
                val title = pageObj["title"]?.jsonPrimitive?.content ?: return@forEach
                val imgUrl = pageObj["imageinfo"]?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                if (imgUrl != null) {
                    // 去掉 "文件:" 前缀还原为文件名
                    val fileName = title.removePrefix("文件:")
                    result[fileName] = imgUrl
                }
            }
        }
        return result
    }

    /** 提取指定名称的模板内容（处理嵌套大括号） */
    private fun extractTemplate(wikitext: String, templateName: String): String? {
        val startMarker = "{{$templateName"
        val startIdx = wikitext.indexOf(startMarker)
        if (startIdx == -1) return null

        var depth = 0
        var i = startIdx
        while (i < wikitext.length - 1) {
            if (wikitext[i] == '{' && wikitext[i + 1] == '{') {
                depth++; i += 2
            } else if (wikitext[i] == '}' && wikitext[i + 1] == '}') {
                depth--
                if (depth == 0) {
                    return wikitext.substring(startIdx + startMarker.length, i).trimStart()
                }
                i += 2
            } else {
                i++
            }
        }
        return null
    }

    /** 解析模板参数 */
    private fun parseTemplateParams(content: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        content.split("|").forEach { part ->
            val eqIdx = part.indexOf('=')
            if (eqIdx > 0) {
                val key = part.substring(0, eqIdx).trim()
                val value = part.substring(eqIdx + 1).trim()
                if (key.isNotEmpty()) params[key] = value
            }
        }
        return params
    }

}
