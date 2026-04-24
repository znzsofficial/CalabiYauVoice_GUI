package com.nekolaska.calabiyau.feature.wiki.bio.api

import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.feature.wiki.bio.model.DeckCardOption
import com.nekolaska.calabiyau.feature.wiki.bio.model.SubmitDeckPayload
import com.nekolaska.calabiyau.feature.wiki.bio.model.SubmitDeckResult
import com.nekolaska.calabiyau.feature.wiki.bio.model.normalizeDeckQuality
import com.nekolaska.calabiyau.feature.wiki.bio.parser.BioDeckShareCodecs
import com.nekolaska.calabiyau.feature.wiki.bio.parser.BioDeckShareParsers
import com.nekolaska.calabiyau.feature.wiki.bio.source.BioDeckShareRemoteSource
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request

/**
 * 晶源感染卡组分享（Android）。
 *
 * 功能：
 * 1) 读取 `MediaWiki:ZombieCardList.json`，提供卡牌清单（含默认卡、品质、cardId、index）。
 * 2) 按 Wiki Widget 规则生成/解析卡组分享码。
 * 3) 通过 MediaWiki API（edit）发布卡组页面。
 */
object BioDeckShareApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val WIKI_JSON_PAGE = "MediaWiki:ZombieCardList.json"
    private const val TARGET_BASE = "晶源感染卡组分享"

    @Volatile
    private var cachedDeckCardMap: Map<String, List<DeckCardOption>>? = null

    fun clearMemoryCache() {
        cachedDeckCardMap = null
    }

    suspend fun fetchDeckCardMap(forceRefresh: Boolean = false): ApiResult<Map<String, List<DeckCardOption>>> =
        withContext(Dispatchers.IO) {
            try {
                if (!forceRefresh) {
                    cachedDeckCardMap?.let { return@withContext ApiResult.Success(it) }
                }

                val url = BioDeckShareRemoteSource.buildUrl(
                    "action" to "query",
                    "prop" to "revisions",
                    "titles" to WIKI_JSON_PAGE,
                    "rvprop" to "content",
                    "rvslots" to "main",
                    "format" to "json"
                )

                val body = BioDeckShareRemoteSource.httpGet(url)
                    ?: return@withContext ApiResult.Error("请求卡牌数据失败", kind = ErrorKind.NETWORK)

                val root = SharedJson.parseToJsonElement(body).jsonObject
                val pages = root["query"]?.jsonObject?.get("pages")?.jsonObject
                    ?: return@withContext ApiResult.Error("卡牌数据结构异常", kind = ErrorKind.PARSE)

                val page = pages.values.firstOrNull()?.jsonObject
                    ?: return@withContext ApiResult.Error("卡牌数据为空", kind = ErrorKind.NOT_FOUND)

                if (page.containsKey("missing")) {
                    return@withContext ApiResult.Error("数据源页面不存在: $WIKI_JSON_PAGE", kind = ErrorKind.NOT_FOUND)
                }

                val rawContent = page["revisions"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("slots")
                    ?.jsonObject
                    ?.get("main")
                    ?.jsonObject
                    ?.get("*")
                    ?.jsonPrimitive
                    ?.content
                    .orEmpty()

                val jsonText = rawContent
                    .trim()
                    .replace(Regex("^<pre[^>]*>", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("</pre>$", RegexOption.IGNORE_CASE), "")
                    .trim()

                val parsed = SharedJson.parseToJsonElement(jsonText).jsonObject
                val result = buildMap {
                    parsed.forEach { (faction, value) ->
                        if (value !is JsonArray) return@forEach
                        put(faction, BioDeckShareParsers.parseFactionCards(value))
                    }
                }

                if (result.isEmpty()) {
                    return@withContext ApiResult.Error("未解析到卡牌数据", kind = ErrorKind.PARSE)
                }

                cachedDeckCardMap = result
                ApiResult.Success(result)
            } catch (e: Exception) {
                ApiResult.Error("读取卡牌数据失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    fun encodeShareCode(cardIndexes: List<Int>, faction: String): String =
        BioDeckShareCodecs.encodeShareCode(cardIndexes, faction)

    fun decodeShareCode(
        rawInput: String,
        expectedFaction: String,
        indexToCardId: Map<Int, String>
    ): Pair<List<String>, String> =
        BioDeckShareCodecs.decodeShareCode(rawInput, expectedFaction, indexToCardId)

    fun extractDeckNameFromShareInput(rawInput: String): String? =
        BioDeckShareCodecs.extractDeckNameFromShareInput(rawInput)

    fun extractActualShareCode(rawInput: String): String =
        BioDeckShareCodecs.extractActualShareCode(rawInput)

    suspend fun submitDeck(payload: SubmitDeckPayload, cardMap: Map<String, List<DeckCardOption>>): ApiResult<SubmitDeckResult> =
        withContext(Dispatchers.IO) {
            val cookies = BioDeckShareRemoteSource.getWikiCookies()
            if (cookies.isNullOrBlank()) {
                return@withContext ApiResult.Error("未检测到登录状态，请先在 Wiki 中登录", kind = ErrorKind.NOT_FOUND)
            }

            try {
                val options = cardMap[payload.faction].orEmpty()
                if (options.isEmpty()) {
                    return@withContext ApiResult.Error("当前阵营卡牌数据为空", kind = ErrorKind.NOT_FOUND)
                }
                val byId = options.associateBy { it.cardId }

                val selectedCards = payload.selectedCardIds
                    .mapNotNull { byId[it] }
                    .ifEmpty {
                        return@withContext ApiResult.Error("至少需要选择 1 张卡牌", kind = ErrorKind.NOT_FOUND)
                    }

                val token = BioDeckShareRemoteSource.fetchCsrfToken(cookies)
                    ?: return@withContext ApiResult.Error("获取 CSRF Token 失败", kind = ErrorKind.NETWORK)

                val sortedCards = selectedCards.sortedWith(
                    compareByDescending<DeckCardOption> { rarityRank(it.normalizedQuality) }
                        .thenBy { if (it.index >= 0) it.index else Int.MAX_VALUE }
                )

                val shareCode = encodeShareCode(
                    cardIndexes = sortedCards.mapNotNull { it.index.takeIf { idx -> idx >= 0 } },
                    faction = payload.faction
                )

                val safeDeckName = sanitizeDeckName(payload.deckName)
                val targetTitle = "$TARGET_BASE/$safeDeckName"
                val shareCodeWidget = "{{#widget:晶源感染卡组分享码生成|cardIds=${sortedCards.map { it.cardId }.filter { it.isNotBlank() }.joinToString(",")}|faction=${payload.faction}}}"

                val cardTemplates = sortedCards.joinToString(" ") {
                    "{{晶源感染卡牌图标|cardName=${safeTemplateParam(it.name)}|cardID=${safeTemplateParam(it.cardId)}|quality=${safeTemplateParam(it.normalizedQuality)}}}"
                }.ifBlank { "无" }

                val introLine = payload.intro.takeIf { it.isNotBlank() }?.let {
                    "|卡组介绍=<pre>${safeTemplateParam(it)}</pre>\n"
                }.orEmpty()

                val wikitext = buildString {
                    appendLine("{{晶源感染卡组分享")
                    appendLine("|卡组名称=${safeTemplateParam(payload.deckName)}")
                    appendLine("|卡组作者=${safeTemplateParam(payload.author)}")
                    appendLine("|卡组分享ID=$shareCodeWidget")
                    append(introLine)
                    appendLine("|卡组阵营=${safeTemplateParam(payload.faction)}")
                    appendLine("|卡牌列表=$cardTemplates}}")
                    appendLine("<noinclude>[[分类:${safeTemplateParam(payload.faction)}卡组分享]]</noinclude>")
                    appendLine("<noinclude><!-- 分享码(调试): ${safeTemplateParam(shareCode)} --></noinclude>")
                }

                val formBody = FormBody.Builder()
                    .add("action", "edit")
                    .add("title", targetTitle)
                    .add("text", wikitext)
                    .add("summary", "App 提交: ${payload.deckName}")
                    .add("createonly", "true")
                    .add("token", token)
                    .add("format", "json")
                    .build()

                val request = Request.Builder()
                    .url(API)
                    .post(formBody)
                    .header("Cookie", cookies)
                    .header("User-Agent", "CalabiYauVoice/2.0 (Android)")
                    .build()

                WikiEngine.client.newCall(request).execute().use { resp ->
                    val respBody = resp.body.string()
                    if (!resp.isSuccessful) {
                        return@withContext ApiResult.Error("发布失败：HTTP ${resp.code}", kind = ErrorKind.NETWORK)
                    }

                    val root = SharedJson.parseToJsonElement(respBody).jsonObject
                    val errorObj = root["error"]?.jsonObject
                    if (errorObj != null) {
                        val code = errorObj["code"]?.jsonPrimitive?.content.orEmpty()
                        val info = errorObj["info"]?.jsonPrimitive?.content.orEmpty()
                        val msg = when (code) {
                            "articleexists" -> "同名卡组已存在，请修改卡组名称"
                            "assertuserfailed" -> "登录状态失效，请重新登录"
                            else -> "发布失败：${info.ifBlank { code }}"
                        }
                        return@withContext ApiResult.Error(msg, kind = ErrorKind.UNKNOWN)
                    }

                    val editObj = root["edit"]?.jsonObject
                    val isPending = editObj?.get("moderation") != null
                    ApiResult.Success(SubmitDeckResult(title = targetTitle, isPending = isPending))
                }
            } catch (e: Exception) {
                ApiResult.Error("发布失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    private fun rarityRank(q: String): Int = when (normalizeDeckQuality(q)) {
        "完美" -> 3
        "卓越" -> 2
        "精致" -> 1
        else -> 0
    }

    private fun sanitizeDeckName(value: String): String {
        return value.trim()
            .replace("/", "／")
            .replace(Regex("[<>\\[\\]|{}]"), "")
            .ifBlank { "未命名卡组" }
    }

    private fun safeTemplateParam(s: String): String {
        return s
            .replace(Regex("\\r?\\n"), " ")
            .replace("|", "｜")
            .replace("{{", "{ {")
            .replace("}}", "} }")
            .replace("<", "＜")
            .replace(">", "＞")
            .replace("[", "［")
            .replace("]", "］")
            .replace("{", "｛")
            .replace("}", "｝")
            .trim()
    }
}
