package com.nekolaska.calabiyau.feature.character.list

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.character.list.CharacterListApi.FACTIONS
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import util.buildParseUrl
import util.buildWikiUrl

/**
 * 角色列表 API（Android）。
 *
 * 通过 MediaWiki parse API 渲染 `{{阵营角色|阵营名}}` 模板，
 * 从返回的 HTML 中提取角色名、链接和图片 URL。
 */
object CharacterListApi : CachedWikiApi<List<CharacterListApi.FactionData>>("CharacterListApi") {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val WIKI_BASE = "https://wiki.biligame.com"

    /** 四个阵营（与 Wiki 首页"超弦体 & 晶源体"内容块一致） */
    val FACTIONS = listOf("欧泊", "剪刀手", "乌尔比诺", "晶源体")

    data class CharacterInfo(
        val name: String,
        val wikiUrl: String,
        val imageUrl: String,
        val portraitUrl: String? = null
    )

    data class FactionData(
        val faction: String,
        val characters: List<CharacterInfo>
    )

    /**
     * 获取所有阵营的角色列表。
     * 并行请求四个阵营，返回按 [FACTIONS] 顺序排列的结果。
     */
    suspend fun fetchAllFactions(forceRefresh: Boolean = false): ApiResult<List<FactionData>> =
        fetch(forceRefresh = forceRefresh)

    override suspend fun fetchFromCache(): ApiResult<List<FactionData>> =
        ApiResult.Error("角色列表不支持 cacheOnly", kind = ErrorKind.NETWORK)

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<FactionData>> = withContext(Dispatchers.IO) {
        try {
            val results = FACTIONS.map { faction ->
                async { fetchFaction(faction, forceRefresh) }
            }.awaitAll()

            val errors = results.filterIsInstance<ApiResult.Error>()
            if (errors.size == results.size) {
                return@withContext ApiResult.Error(
                    "所有阵营加载失败: ${errors.first().message}",
                    kind = errors.first().kind
                )
            }

            val successes = results.filterIsInstance<ApiResult.Success<FactionData>>()
            val isOffline = successes.any { it.isOffline }
            val maxAge = successes.maxOfOrNull { it.cacheAgeMs } ?: 0L

            val factions = results.mapIndexed { index, result ->
                when (result) {
                    is ApiResult.Success -> result.value
                    is ApiResult.Error -> FactionData(FACTIONS[index], emptyList())
                }
            }
            ApiResult.Success(factions, isOffline = isOffline, cacheAgeMs = maxAge)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error("获取角色列表失败: ${e.message}", kind = e.toErrorKind())
        }
    }

    private suspend fun fetchFaction(
        faction: String,
        forceRefresh: Boolean
    ): ApiResult<FactionData> {
        return try {
            val wikitext = "{{阵营角色|$faction}}"
            val url = buildWikiUrl(API, "action" to "parse", "text" to wikitext, "prop" to "text", "contentmodel" to "wikitext", "format" to "json")

            val result = OfflineCache.fetchWithCache(
                type = OfflineCache.Type.CHARACTER_LIST,
                key = "faction_$faction",
                forceRefresh = forceRefresh
            ) { WikiEngine.safeGet(url) }
                ?: return ApiResult.Error(
                    "请求 $faction 失败，且无离线缓存",
                    kind = ErrorKind.NETWORK
                )
            val body = result.payload
            val json = SharedJson.parseToJsonElement(body).jsonObject
            val html = json["parse"]
                ?.jsonObject?.get("text")
                ?.jsonObject?.get("*")
                ?.jsonPrimitive?.content
                ?: return ApiResult.Error("解析 $faction HTML 失败", kind = ErrorKind.PARSE)

            val characters = parseCharactersFromHtml(html)
            val withPortraits = fetchPortraits(characters)
            ApiResult.Success(
                FactionData(faction, withPortraits),
                isOffline = result.isFromCache,
                cacheAgeMs = result.ageMs
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error("加载 $faction 失败: ${e.message}", kind = e.toErrorKind())
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
        val seen = mutableSetOf<String>()
        val document = Jsoup.parse(html)

        document.select("div.hvr-bounce-out").forEach { card ->
            val imageLink = card.selectFirst("a[href^=/klbq/]:has(img)") ?: return@forEach
            val path = imageLink.attr("href")
            val name = imageLink.attr("title").trim()
            val imageUrl = WikiImageUrls.originalFromThumbnail(
                imageLink.selectFirst("img")?.attr("src")
            ).orEmpty()
            if (name !in seen) {
                seen += name
                results += CharacterInfo(
                    name = name,
                    wikiUrl = "$WIKI_BASE$path",
                    imageUrl = imageUrl
                )
            }
        }
        return WikiParseLogger.finishList("CharacterListApi.parseCharactersFromHtml", results, html)
    }

    /**
     * 批量获取角色立绘 URL。
     * 并发请求每个角色的 Wiki 页面，从渲染 HTML 中解析第一个立绘图片。
     */
    private suspend fun fetchPortraits(
        characters: List<CharacterInfo>
    ): List<CharacterInfo> = withContext(Dispatchers.IO) {
        if (characters.isEmpty()) return@withContext characters
        val portraitPattern = Regex(
            """^.+-[^|\]\n{}]+立绘\.(?:png|jpg|jpeg|webp)$""",
            RegexOption.IGNORE_CASE
        )
        characters.map { char ->
            async {
                try {
                    val url = buildParseUrl(API, char.name, "text")
                    val body = WikiEngine.safeGet(url) ?: return@async char
                    val json = SharedJson.parseToJsonElement(body).jsonObject
                    val html = json["parse"]
                        ?.jsonObject?.get("text")
                        ?.jsonObject?.get("*")
                        ?.jsonPrimitive?.content
                        ?: return@async char
                    val portraitSrc = Jsoup.parse(html).select("img[src][alt]")
                        .firstOrNull { img -> portraitPattern.matches(img.attr("alt").trim()) }
                        ?.attr("src")
                    val portraitUrl = WikiImageUrls.originalFromThumbnail(portraitSrc)
                    if (portraitUrl.isNullOrBlank()) char else char.copy(portraitUrl = portraitUrl)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    char
                }
            }
        }.awaitAll()
    }

}
