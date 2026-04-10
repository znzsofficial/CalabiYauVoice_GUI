package com.nekolaska.calabiyau.data

import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * 游戏模式 API（Android）。
 *
 * 通过 MediaWiki parse API 获取各战斗模式子页面的 wikitext，
 * 解析模式说明、获胜条件、模式设定等信息。
 */
object GameModeApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"

    /** 游戏模式列表 */
    val MODES = listOf(
        ModeEntry("一般爆破", "战斗模式/一般爆破"),
        ModeEntry("团队乱斗", "战斗模式/团队乱斗"),
        ModeEntry("无限团竞", "战斗模式/无限团竞"),
        ModeEntry("极限推进", "战斗模式/极限推进"),
        ModeEntry("晶源感染", "战斗模式/晶源感染"),
        ModeEntry("极限刀战", "战斗模式/极限刀战"),
        ModeEntry("枪王乱斗", "战斗模式/枪王乱斗"),
        ModeEntry("晶能冲突", "战斗模式/晶能冲突"),
        ModeEntry("弦区争夺", "战斗模式/弦区争夺"),
        ModeEntry("大头乱斗", "战斗模式/大头乱斗"),
    )

    data class ModeEntry(val displayName: String, val pageName: String)

    /** 游戏模式详情 */
    data class GameModeDetail(
        val name: String,
        val summary: String,        // 简介（第一行文字）
        val winCondition: String,   // 获胜条件
        val settings: String,       // 模式设定
        val maps: List<String>,     // 可用地图列表
        val wikiUrl: String
    )


    // ── 内存缓存 ──
    @Volatile
    private var cachedModes: List<GameModeDetail>? = null

    fun clearMemoryCache() { cachedModes = null }

    /**
     * 获取所有游戏模式详情（带缓存）。
     */
    suspend fun fetchAllModes(forceRefresh: Boolean = false): ApiResult<List<GameModeDetail>> {
        if (!forceRefresh) {
            cachedModes?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedModes = it.value
        }
    }

    /** 内部：带缓存元数据的模式结果 */
    private data class ModeResult(
        val detail: GameModeDetail,
        val isFromCache: Boolean,
        val ageMs: Long
    )

    private suspend fun fetchFromNetwork(
        forceRefresh: Boolean
    ): ApiResult<List<GameModeDetail>> =
        withContext(Dispatchers.IO) {
            try {
                // 先从「模板:卡拉彼丘」获取所有模式的地图映射
                val modeMapMapping = fetchModeMapMapping(forceRefresh)

                val results = MODES.map { mode ->
                    async { fetchMode(mode, modeMapMapping, forceRefresh) }
                }.awaitAll()

                val data = results.filterNotNull()
                if (data.isEmpty()) {
                    ApiResult.Error("获取游戏模式失败", kind = ErrorKind.NETWORK)
                } else {
                    val isOffline = data.any { it.isFromCache }
                    val maxAge = data.maxOfOrNull { it.ageMs } ?: 0L
                    ApiResult.Success(
                        data.map { it.detail },
                        isOffline = isOffline,
                        cacheAgeMs = maxAge
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error("获取游戏模式失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    /**
     * 从「模板:卡拉彼丘」解析模式→地图映射。
     * 模板结构：group{N}=模式名, list{N}=地图列表
     */
    private suspend fun fetchModeMapMapping(forceRefresh: Boolean): Map<String, List<String>> {
        val mapping = mutableMapOf<String, List<String>>()
        try {
            val encoded = URLEncoder.encode("模板:卡拉彼丘", "UTF-8")
            val url = "$API?action=parse&page=$encoded&prop=wikitext&format=json"
            val cacheResult = OfflineCache.fetchWithCache(
                type = OfflineCache.Type.GAME_MODES,
                key = "mode_map_mapping",
                forceRefresh = forceRefresh
            ) { WikiEngine.safeGet(url) } ?: return mapping
            val body = cacheResult.json
            val json = SharedJson.parseToJsonElement(body).jsonObject
            val wikitext = json["parse"]?.jsonObject?.get("wikitext")
                ?.jsonObject?.get("*")?.jsonPrimitive?.content ?: return mapping

            // 提取 group{N} 和 list{N}
            val groupRegex = Regex("""\|group(\d+)=(.+)""")
            val listRegex = Regex("""\|list(\d+)=(.+)""")

            val groups = mutableMapOf<String, String>() // num → 模式名
            val lists = mutableMapOf<String, String>()  // num → 地图原文

            for (line in wikitext.lines()) {
                groupRegex.find(line)?.let { m ->
                    groups[m.groupValues[1]] = m.groupValues[2].trim()
                }
                listRegex.find(line)?.let { m ->
                    lists[m.groupValues[1]] = m.groupValues[2].trim()
                }
            }

            // 解析每个 group 中的模式名（可能有多个，用 <br> 分隔）
            for ((num, groupRaw) in groups) {
                val listRaw = lists[num] ?: continue
                // 提取地图名：[[404基地]] • [[88区]] ...
                val maps = Regex("""\[\[([^\]|]+)]]""").findAll(listRaw)
                    .map { it.groupValues[1] }
                    .toList()
                // 提取模式显示名：[[...|xxx]] 中的 xxx
                val modeNames = Regex("""\[\[[^\]]*\|([^\]]+)]]""").findAll(groupRaw)
                    .map { it.groupValues[1].trim() }
                    .toList()
                // 每个模式名都映射到同一份地图列表
                for (modeName in modeNames) {
                    mapping[modeName] = maps
                }
            }
        } catch (_: Exception) { }
        return mapping
    }

    private suspend fun fetchMode(
        mode: ModeEntry,
        modeMapMapping: Map<String, List<String>>,
        forceRefresh: Boolean
    ): ModeResult? {
        return try {
            val encoded = URLEncoder.encode(mode.pageName, "UTF-8")
            val url = "$API?action=parse&page=$encoded&prop=wikitext&format=json"
            val cacheResult = OfflineCache.fetchWithCache(
                type = OfflineCache.Type.GAME_MODES,
                key = "mode_${mode.pageName}",
                forceRefresh = forceRefresh
            ) { WikiEngine.safeGet(url) } ?: return null
            val body = cacheResult.json

            val json = SharedJson.parseToJsonElement(body).jsonObject
            val wikitext = json["parse"]?.jsonObject?.get("wikitext")
                ?.jsonObject?.get("*")?.jsonPrimitive?.content ?: return null

            val detail = parseModeWikitext(
                mode,
                wikitext,
                modeMapMapping[mode.displayName] ?: emptyList()
            )
            ModeResult(
                detail = detail,
                isFromCache = cacheResult.isFromCache,
                ageMs = cacheResult.ageMs
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseModeWikitext(mode: ModeEntry, wikitext: String, maps: List<String>): GameModeDetail {
        // 先移除 <gallery>...</gallery> 块
        val noGallery = wikitext.replace(Regex("""<gallery[^>]*>[\s\S]*?</gallery>"""), "")
        val lines = noGallery.lines()

        // 简介：提取第一段有意义的文字
        val summary = lines
            .map { line ->
                line.trim()
                    .replace(Regex("""\[\[文件:[^\]]*]]"""), "")  // 移除图片标记
                    .replace(Regex("""\[\[File:[^\]]*]]"""), "")
                    .replace(Regex("""<br\s*/?>"""), "")           // 移除换行标记
                    .trim()
            }
            .filter {
                it.isNotBlank() &&
                !it.startsWith("{{") &&
                !it.startsWith("==") &&
                !it.startsWith("__") &&
                !it.startsWith("<") &&
                !it.startsWith("*") &&
                !it.startsWith(":") &&
                !it.startsWith(";") &&
                !it.startsWith("文件:") &&
                !it.startsWith("File:")
            }
            .firstOrNull() ?: ""

        // 获胜条件：==获胜条件== 下的内容
        val winCondition = extractSection(wikitext, "获胜条件")

        // 模式设定：==模式设定== 下的内容
        val settings = extractSection(wikitext, "模式设定")

        val enc = URLEncoder.encode(mode.pageName, "UTF-8").replace("+", "%20")

        return GameModeDetail(
            name = mode.displayName,
            summary = summary,
            winCondition = cleanWikitext(winCondition),
            settings = cleanWikitext(settings),
            maps = maps,
            wikiUrl = "$WIKI_BASE$enc"
        )
    }

    /** 提取 ==sectionName== 下的内容（到下一个 == 为止） */
    private fun extractSection(wikitext: String, sectionName: String): String {
        val pattern = Regex("""==\s*${Regex.escape(sectionName)}\s*==\s*\n""")
        val match = pattern.find(wikitext) ?: return ""
        val start = match.range.last + 1
        val nextSection = Regex("""^==\s*[^=]""", RegexOption.MULTILINE).find(wikitext, start)
        val end = nextSection?.range?.first ?: wikitext.length
        return wikitext.substring(start, end).trim()
    }

    /** 清理 wikitext 标记 */
    private fun cleanWikitext(text: String): String {
        return text
            .replace(Regex("""\[\[文件:[^\]]*]]"""), "")          // 移除图片链接 [[文件:...]]
            .replace(Regex("""\[\[File:[^\]]*]]"""), "")               // 移除图片链接 [[File:...]]
            .replace(Regex("""\[\[([^\]|]*)\|([^\]]*)\]\]"""), "$2")  // [[link|text]] → text
            .replace(Regex("""\[\[([^\]]*)\]\]"""), "$1")              // [[link]] → link
            .replace(Regex("""\{\{#ask:[^}]*\}\}"""), "")              // {{#ask:...}} SMW 查询
            .replace(Regex("""\{\{#info:[^}]*\}\}"""), "")             // {{#info:...}}
            .replace(Regex("""\{\{[^}]*\}\}"""), "")                   // {{template}}
            .replace(Regex("""<s>.*?</s>"""), "")                      // <s>...</s>
            .replace(Regex("""<[^>]+>"""), "")                         // HTML tags
            .replace(Regex("""^:{1,}\s*$""", RegexOption.MULTILINE), "") // 孤立的 : 或 :: 行
            .replace(Regex("""^\*\s*""", RegexOption.MULTILINE), "• ") // * → •
            .replace(Regex("""\n{3,}"""), "\n\n")                      // 多余空行
            .trim()
    }

}
