package com.nekolaska.calabiyau.data

import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.net.URLEncoder

/**
 * 角色列表 API（Android）。
 *
 * 通过 MediaWiki parse API 渲染 `{{阵营角色|阵营名}}` 模板，
 * 从返回的 HTML 中提取角色名、链接和图片 URL。
 */
object CharacterListApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val WIKI_BASE = "https://wiki.biligame.com"

    /** 四个阵营（与 Wiki 首页"超弦体 & 晶源体"内容块一致） */
    val FACTIONS = listOf("欧泊", "剪刀手", "乌尔比诺", "晶源体")

    data class CharacterInfo(
        val name: String,
        val wikiUrl: String,
        val imageUrl: String
    )

    data class FactionData(
        val faction: String,
        val characters: List<CharacterInfo>
    )

    sealed interface ApiResult<out T> {
        data class Success<T>(val value: T) : ApiResult<T>
        data class Error(val message: String) : ApiResult<Nothing>
    }

    /**
     * 获取所有阵营的角色列表。
     * 并行请求四个阵营，返回按 [FACTIONS] 顺序排列的结果。
     */
    suspend fun fetchAllFactions(): ApiResult<List<FactionData>> = withContext(Dispatchers.IO) {
        try {
            val results = FACTIONS.map { faction ->
                async { fetchFaction(faction) }
            }.awaitAll()

            val errors = results.filterIsInstance<ApiResult.Error>()
            if (errors.size == results.size) {
                return@withContext ApiResult.Error("所有阵营加载失败: ${errors.first().message}")
            }

            val factions = results.mapIndexed { index, result ->
                when (result) {
                    is ApiResult.Success -> result.value
                    is ApiResult.Error -> FactionData(FACTIONS[index], emptyList())
                }
            }
            ApiResult.Success(factions)
        } catch (e: Exception) {
            ApiResult.Error("获取角色列表失败: ${e.message}")
        }
    }

    private fun fetchFaction(faction: String): ApiResult<FactionData> {
        return try {
            val wikitext = "{{阵营角色|$faction}}"
            val encoded = URLEncoder.encode(wikitext, "UTF-8")
            val url = "$API?action=parse&text=$encoded&prop=text&contentmodel=wikitext&format=json"

            val body = httpGet(url) ?: return ApiResult.Error("请求 $faction 失败")
            val json = SharedJson.parseToJsonElement(body).jsonObject
            val html = json["parse"]
                ?.jsonObject?.get("text")
                ?.jsonObject?.get("*")
                ?.jsonPrimitive?.content
                ?: return ApiResult.Error("解析 $faction HTML 失败")

            val characters = parseCharactersFromHtml(html)
            ApiResult.Success(FactionData(faction, characters))
        } catch (e: Exception) {
            ApiResult.Error("加载 $faction 失败: ${e.message}")
        }
    }

    /**
     * 从渲染后的 HTML 中提取角色信息。
     *
     * HTML 结构：
     * ```
     * <div class="hvr-bounce-out" ...>
     *   <div ...><a href="/klbq/角色名" title="角色名"><img src="图片URL" ...></a></div>
     *   <a href="/klbq/角色名" title="角色名"><span ...>角色名</span></a>
     * </div>
     * ```
     */
    private fun parseCharactersFromHtml(html: String): List<CharacterInfo> {
        val results = mutableListOf<CharacterInfo>()
        // 匹配 <a href="..." title="角色名"><img ... src="图片URL" ...>
        val regex = Regex(
            """<a\s+href="(/klbq/[^"]+)"\s+title="([^"]+)"><img[^>]*src="([^"]+)"[^>]*/?>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val seen = mutableSetOf<String>()
        regex.findAll(html).forEach { match ->
            val path = match.groupValues[1]
            val name = decodeHtmlEntities(match.groupValues[2])
            val imageUrl = match.groupValues[3]
            if (name !in seen) {
                seen += name
                results += CharacterInfo(
                    name = name,
                    wikiUrl = "$WIKI_BASE$path",
                    imageUrl = imageUrl
                )
            }
        }
        return results
    }

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
    }

    private fun httpGet(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            WikiEngine.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body.string()
                // 检测 CDN 拦截页面（返回 HTML 而非 JSON）
                if (!body.trimStart().startsWith("{")) return null
                body
            }
        } catch (_: Exception) {
            null
        }
    }
}
