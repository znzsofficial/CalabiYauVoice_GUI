package com.nekolaska.calabiyau.feature.character.list

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.core.wiki.fetchBatchImageUrls
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import org.jsoup.Jsoup
import util.buildParseUrl
import util.buildWikiUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 角色列表 API（Android）。
 *
 * 通过 MediaWiki parse API 渲染 `{{阵营角色|阵营名}}` 模板，
 * 从返回的 HTML 中提取角色名、链接和图片 URL。
 */
object CharacterListApi : CachedWikiApi<List<CharacterListApi.FactionData>>("CharacterListApi") {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val WIKI_BASE = "https://wiki.biligame.com"
    private val portraitSemaphore = Semaphore(3)

    private suspend fun cachedPortraits(faction: String): Map<String, String> =
        OfflineCache.getEntry(OfflineCache.Type.CHARACTER_LIST, "portraits_$faction")?.let {
            runCatching { SharedJson.decodeFromString<Map<String, String>>(it.content) }.getOrNull()
        }.orEmpty()

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
    suspend fun fetchAllFactions(
        forceRefresh: Boolean = false,
        cacheOnly: Boolean = false,
        allowMemoryCache: Boolean = true
    ): ApiResult<List<FactionData>> = fetch(
        forceRefresh = forceRefresh,
        cacheOnly = cacheOnly,
        allowMemoryCache = allowMemoryCache
    )

    override suspend fun fetchFromCache(): ApiResult<List<FactionData>> =
        withContext(Dispatchers.IO) {
            try {
                val results = FACTIONS.map { faction ->
                    async { loadCachedFaction(faction) }
                }.awaitAll()

                val errors = results.filterIsInstance<ApiResult.Error>()
                if (errors.size == results.size) {
                    return@withContext ApiResult.Error(
                        "所有阵营缓存加载失败: ${errors.first().message}",
                        kind = errors.first().kind
                    )
                }

                val successes = results.filterIsInstance<ApiResult.Success<FactionData>>()
                val maxAge = successes.maxOfOrNull { it.cacheAgeMs } ?: 0L
                val factions = results.mapIndexed { index, result ->
                    when (result) {
                        is ApiResult.Success -> result.value
                        is ApiResult.Error -> FactionData(FACTIONS[index], emptyList())
                    }
                }
                ApiResult.Success(factions, isOffline = true, cacheAgeMs = maxAge)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ApiResult.Error("读取角色列表缓存失败: ${e.message}", kind = e.toErrorKind())
            }
        }

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
            if (characters.isEmpty()) {
                return ApiResult.Error("未找到 $faction 角色数据", kind = ErrorKind.NOT_FOUND)
            }
            // 立绘始终补抓：批量 imageinfo 请求成本低，缓存路径也执行，
            // 避免首次抓取被限流后缓存里的立绘永远缺失。
            val savedPortraits = cachedPortraits(faction)
            val withPortraits = fetchPortraits(characters.map {
                it.copy(portraitUrl = savedPortraits[it.name])
            })
            val resolved = withPortraits.mapNotNull { char -> char.portraitUrl?.let { char.name to it } }.toMap()
            if (resolved.isNotEmpty()) OfflineCache.put(OfflineCache.Type.CHARACTER_LIST,
                "portraits_$faction", SharedJson.encodeToString(savedPortraits + resolved))
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

    private suspend fun loadCachedFaction(faction: String): ApiResult<FactionData> {
        return try {
            val entry = OfflineCache.getEntry(
                type = OfflineCache.Type.CHARACTER_LIST,
                key = "faction_$faction"
            ) ?: return ApiResult.Error("没有 $faction 缓存", kind = ErrorKind.NETWORK)

            val json = SharedJson.parseToJsonElement(entry.content).jsonObject
            val html = json["parse"]
                ?.jsonObject?.get("text")
                ?.jsonObject?.get("*")
                ?.jsonPrimitive?.content
                ?: return ApiResult.Error("解析 $faction 缓存 HTML 失败", kind = ErrorKind.PARSE)

            val characters = parseCharactersFromHtml(html)
            if (characters.isEmpty()) {
                return ApiResult.Error("未找到 $faction 角色缓存数据", kind = ErrorKind.NOT_FOUND)
            }

            val portraits = cachedPortraits(faction)
            ApiResult.Success(
                FactionData(faction, characters.map { it.copy(portraitUrl = portraits[it.name]) }),
                isOffline = true,
                cacheAgeMs = entry.ageMs
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error("加载 $faction 缓存失败: ${e.message}", kind = e.toErrorKind())
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
            if (name.isNotBlank() && name !in seen) {
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
     *
     * 首选：立绘文件名遵循 "角色名-初始立绘.png" 规律，用批量 imageinfo 一次查 50 个，
     * 把请求数从「每角色一次页面渲染」降到「每 50 角色一次」，避免触发 EdgeOne 频率拦截
     * （此前并发逐角色抓页面经常收到 567 拦截页，解析失败被静默吞掉导致 fallback）。
     *
     * 兜底：规律查不到的角色（命名不同/无初始立绘）退回逐角色页面解析，限并发 3。
     */
    private suspend fun fetchPortraits(
        characters: List<CharacterInfo>
    ): List<CharacterInfo> = withContext(Dispatchers.IO) {
        if (characters.isEmpty()) return@withContext characters
        val byBatchUrl = fetchPortraitsViaImageInfo(characters)
        val missing = characters.filter { it.portraitUrl == null && it.name !in byBatchUrl }
        // 批量查询已覆盖标准命名；剩余数量过多说明批量失败或整批命名特殊，
        // 此时逐页兜底既慢又容易再次触发限流，直接保留卡片缩略图展示。
        val byPage = if (missing.isEmpty() || missing.size > 8) emptyMap() else fetchPortraitsViaPages(missing)
        characters.map { char ->
            char.copy(portraitUrl = byBatchUrl[char.name] ?: byPage[char.name] ?: char.portraitUrl)
        }
    }

    /** 「角色名-初始立绘.png」规律 + 批量 imageinfo，一次请求查一批。 */
    private suspend fun fetchPortraitsViaImageInfo(
        characters: List<CharacterInfo>
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val fileNames = characters.map { "${it.name}-初始立绘.png" }
        fetchBatchImageUrls(fileNames, API) { url -> WikiEngine.safeGet(url) }
            .mapKeys { (fileName, _) -> fileName.removeSuffix("-初始立绘.png") }
    }

    /** 逐角色页面解析兜底；限并发 3，失败时保留原数据而不是静默丢立绘。 */
    private suspend fun fetchPortraitsViaPages(
        characters: List<CharacterInfo>
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val portraitPattern = Regex(
            """^.+-[^|\]\n{}]+立绘\.(?:png|jpg|jpeg|webp)$""",
            RegexOption.IGNORE_CASE
        )
        val result = ConcurrentHashMap<String, String>()
        characters.map { char ->
            async {
                portraitSemaphore.withPermit {
                    try {
                        val url = buildParseUrl(API, char.name, "text")
                        val body = WikiEngine.safeGet(url) ?: return@withPermit
                        val json = SharedJson.parseToJsonElement(body).jsonObject
                        val html = json["parse"]
                            ?.jsonObject?.get("text")
                            ?.jsonObject?.get("*")
                            ?.jsonPrimitive?.content
                            ?: return@withPermit
                        val portraitSrc = Jsoup.parse(html).select("img[src][alt]")
                            .firstOrNull { img -> portraitPattern.matches(img.attr("alt").trim()) }
                            ?.attr("src")
                        WikiImageUrls.originalFromThumbnail(portraitSrc)
                            ?.takeIf { it.isNotBlank() }
                            ?.let { result[char.name] = it }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // 立绘抓取失败只影响 portraitUrl，列表数据保留
                    }
                }
            }
        }.awaitAll()
        result.toMap()
    }

}
