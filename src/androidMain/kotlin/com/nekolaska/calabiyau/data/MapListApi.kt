package com.nekolaska.calabiyau.data

import data.ApiResult
import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * 地图列表 API（Android）。
 *
 * 通过 MediaWiki parse API 渲染 `{{游戏地图|模式名}}` 模板，
 * 从返回的 HTML 中提取地图名、链接和图片 URL。
 */
object MapListApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val WIKI_BASE = "https://wiki.biligame.com"

    /** 内存缓存 */
    @Volatile
    private var cachedModes: List<GameModeData>? = null

    /** 游戏模式列表（与 Wiki 首页一致） */
    val GAME_MODES = listOf(
        "爆破/团队乱斗" to "一般爆破",
        "无限团竞" to "无限团竞",
        "极限推进" to "极限推进",
        "大头乱斗" to "大头乱斗",
        "晶源感染" to "晶源感染",
        "极限刀战" to "极限刀战",
        "弦区争夺" to "弦区争夺",
        "枪王乱斗" to "枪王乱斗",
        "晶能冲突" to "晶能冲突",
    )

    data class MapInfo(
        val name: String,
        val wikiUrl: String,
        val imageUrl: String   // 600px 宽度版本
    )

    data class GameModeData(
        val displayName: String,
        val templateName: String,
        val maps: List<MapInfo>
    )



    /**
     * 获取所有模式的地图列表（并行请求）。
     */
    suspend fun fetchAllModes(forceRefresh: Boolean = false): ApiResult<List<GameModeData>> = withContext(Dispatchers.IO) {
        if (!forceRefresh) cachedModes?.let { return@withContext ApiResult.Success(it) }
        try {
            val results = GAME_MODES.map { (display, template) ->
                async { fetchMode(display, template) }
            }.awaitAll()

            val errors = results.filterIsInstance<ApiResult.Error>()
            if (errors.size == results.size) {
                return@withContext ApiResult.Error("所有模式加载失败: ${errors.first().message}")
            }

            val modes = results.mapIndexed { index, result ->
                when (result) {
                    is ApiResult.Success -> result.value
                    is ApiResult.Error -> {
                        val (display, template) = GAME_MODES[index]
                        GameModeData(display, template, emptyList())
                    }
                }
            }
            cachedModes = modes
            ApiResult.Success(modes)
        } catch (e: Exception) {
            ApiResult.Error("获取地图列表失败: ${e.message}")
        }
    }

    private suspend fun fetchMode(displayName: String, templateName: String): ApiResult<GameModeData> {
        return try {
            val wikitext = "{{游戏地图|$templateName}}"
            val encoded = URLEncoder.encode(wikitext, "UTF-8")
            val url = "$API?action=parse&text=$encoded&prop=text&contentmodel=wikitext&format=json"

            val (body, _) = OfflineCache.fetchWithCache(
                type = OfflineCache.Type.MAP_LIST,
                key = "mode_$templateName"
            ) { WikiEngine.safeGet(url) }
                ?: return ApiResult.Error("请求 $displayName 失败，且无离线缓存")
            val json = SharedJson.parseToJsonElement(body).jsonObject
            val html = json["parse"]
                ?.jsonObject?.get("text")
                ?.jsonObject?.get("*")
                ?.jsonPrimitive?.content
                ?: return ApiResult.Error("解析 $displayName HTML 失败")

            val maps = parseMapsFromHtml(html)
            ApiResult.Success(GameModeData(displayName, templateName, maps))
        } catch (e: Exception) {
            ApiResult.Error("加载 $displayName 失败: ${e.message}")
        }
    }

    /**
     * 从渲染后的 HTML 中提取地图信息。
     *
     * HTML 结构：
     * ```
     * <div class="hvr-bounce-out" style="width: 300px;...">
     *   <div ...><a href="/klbq/地图名" title="地图名">
     *     <img src="300px-..." srcset="...600px-... 2x" ...>
     *   </a></div>
     *   <a href="/klbq/地图名" title="地图名">地图名</a>
     * </div>
     * ```
     */
    private fun parseMapsFromHtml(html: String): List<MapInfo> {
        val results = mutableListOf<MapInfo>()
        // 匹配 <a href="..." title="地图名"><img ... src="..." srcset="...">
        val regex = Regex(
            """<a\s+href="(/klbq/[^"]+)"\s+title="([^"]+)"[^>]*><img[^>]*src="([^"]+)"[^>]*srcset="([^"]*)"[^>]*/?>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val seen = mutableSetOf<String>()
        regex.findAll(html).forEach { match ->
            val path = match.groupValues[1]
            val name = decodeHtmlEntities(match.groupValues[2])
            val defaultSrc = match.groupValues[3]
            val srcset = match.groupValues[4]

            if (name !in seen) {
                seen += name
                // 从 srcset 提取 600px 版本（2x），否则回退到默认 src
                val imageUrl = extract600pxUrl(srcset) ?: defaultSrc
                results += MapInfo(
                    name = name,
                    wikiUrl = "$WIKI_BASE$path",
                    imageUrl = imageUrl
                )
            }
        }
        return results
    }

    /** 从 srcset 中提取 600px 版本的 URL */
    private fun extract600pxUrl(srcset: String): String? {
        // srcset 格式: "url1 1.5x, url2 2x"
        return srcset.split(",")
            .map { it.trim() }
            .firstOrNull { it.contains("600px") || it.endsWith("2x") }
            ?.split(" ")
            ?.firstOrNull()
    }

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
    }

}
