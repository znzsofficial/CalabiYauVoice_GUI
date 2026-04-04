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

    sealed interface ApiResult<out T> {
        data class Success<T>(val value: T) : ApiResult<T>
        data class Error(val message: String) : ApiResult<Nothing>
    }

    // ── 内存缓存 ──
    @Volatile
    private var cachedModes: List<GameModeDetail>? = null

    /**
     * 获取所有游戏模式详情（带缓存）。
     */
    suspend fun fetchAllModes(forceRefresh: Boolean = false): ApiResult<List<GameModeDetail>> {
        if (!forceRefresh) {
            cachedModes?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork().also {
            if (it is ApiResult.Success) cachedModes = it.value
        }
    }

    private suspend fun fetchFromNetwork(): ApiResult<List<GameModeDetail>> =
        withContext(Dispatchers.IO) {
            try {
                val results = MODES.map { mode ->
                    async { fetchMode(mode) }
                }.awaitAll()

                val data = results.filterNotNull()
                if (data.isEmpty()) {
                    ApiResult.Error("获取游戏模式失败")
                } else {
                    ApiResult.Success(data)
                }
            } catch (e: Exception) {
                ApiResult.Error("获取游戏模式失败: ${e.message}")
            }
        }

    private suspend fun fetchMode(mode: ModeEntry): GameModeDetail? {
        return try {
            val encoded = URLEncoder.encode(mode.pageName, "UTF-8")
            val url = "$API?action=parse&page=$encoded&prop=wikitext&format=json"
            val body = httpGet(url) ?: return null

            val json = SharedJson.parseToJsonElement(body).jsonObject
            val wikitext = json["parse"]?.jsonObject?.get("wikitext")
                ?.jsonObject?.get("*")?.jsonPrimitive?.content ?: return null

            parseModeWikitext(mode, wikitext)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseModeWikitext(mode: ModeEntry, wikitext: String): GameModeDetail {
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

        // 提取地图名（[[地图名]] 格式）
        val maps = Regex("""\[\[([^\]|]+)]]""").findAll(settings)
            .map { it.groupValues[1] }
            .filter { !it.startsWith("文件:") && !it.startsWith("File:") }
            .toList()

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
            .replace(Regex("""\{\{#info:[^}]*\}\}"""), "")             // {{#info:...}}
            .replace(Regex("""\{\{[^}]*\}\}"""), "")                   // {{template}}
            .replace(Regex("""<s>.*?</s>"""), "")                      // <s>...</s>
            .replace(Regex("""<[^>]+>"""), "")                         // HTML tags
            .replace(Regex("""^:\s*$""", RegexOption.MULTILINE), "")    // 孤立的 : 行
            .replace(Regex("""^\*\s*""", RegexOption.MULTILINE), "• ") // * → •
            .replace(Regex("""\n{3,}"""), "\n\n")                      // 多余空行
            .trim()
    }

    private fun httpGet(url: String): String? {
        val request = Request.Builder().url(url).build()
        WikiEngine.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body.string()
        }
    }
}
