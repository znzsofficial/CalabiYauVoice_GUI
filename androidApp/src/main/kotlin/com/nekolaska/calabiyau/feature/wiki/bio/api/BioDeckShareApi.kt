package com.nekolaska.calabiyau.feature.wiki.bio.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiWriteOwner
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
import data.ioApiCall
import data.toErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import util.buildWikiUrl
import okhttp3.Request
import util.awaitResponseText
import kotlinx.serialization.json.JsonObject
import util.formBodyOf

/**
 * 晶源感染卡组分享（Android）。
 *
 * 功能：
 * 1) 读取 `MediaWiki:ZombieCardList.json`，提供卡牌清单（含默认卡、品质、cardId、index）。
 * 2) 按 Wiki Widget 规则生成/解析卡组分享码。
 * 3) 通过 MediaWiki API（edit）发布卡组页面。
 */
object BioDeckShareApi : CachedWikiApi<Map<String, List<DeckCardOption>>>("BioDeckShareApi") {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val WIKI_JSON_PAGE = "MediaWiki:ZombieCardList.json"
    private const val TARGET_BASE = "晶源感染卡组分享"

    suspend fun fetchDeckCardMap(forceRefresh: Boolean = false): ApiResult<Map<String, List<DeckCardOption>>> =
        fetch(forceRefresh = forceRefresh)

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<Map<String, List<DeckCardOption>>> =
        ioApiCall("读取卡牌数据失败") {
            val url = buildWikiUrl(API,
                "action" to "query",
                "prop" to "revisions",
                "titles" to WIKI_JSON_PAGE,
                "rvprop" to "content",
                "rvslots" to "main",
                "format" to "json"
            )

            val raw = BioDeckShareRemoteSource.httpGetWithCache(
                url = url,
                cacheKey = "bio_deck_card_map",
                forceRefresh = forceRefresh, validate = { parseIndex(it) != null }
            )
                ?: return@ioApiCall ApiResult.Error("请求卡牌数据失败", kind = ErrorKind.NETWORK)

            val index = parseIndex(raw.payload) ?: return@ioApiCall ApiResult.Error("卡牌索引不完整", kind = ErrorKind.PARSE)
            ApiResult.Success(index, raw.isFromCache, raw.ageMs)
        }

    private fun parseIndex(body: String): Map<String, List<DeckCardOption>>? = runCatching {
            val root = SharedJson.parseToJsonElement(body).jsonObject
            if ("error" in root) return null
            val pages = root["query"]?.jsonObject?.get("pages")?.jsonObject
                ?: return null

            val page = pages.values.firstOrNull()?.jsonObject
                ?: return null

            if (page.containsKey("missing")) {
                return null
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

            result.takeIf { !it["超弦体"].isNullOrEmpty() && !it["晶源体"].isNullOrEmpty() }
        }.getOrNull()

    fun decodeShareCode(
        rawInput: String,
        expectedFaction: String,
        indexToCardId: Map<Int, String>
    ): Pair<List<String>, String> =
        BioDeckShareCodecs.decodeShareCode(rawInput, expectedFaction, indexToCardId)

    fun extractDeckNameFromShareInput(rawInput: String): String? =
        BioDeckShareCodecs.extractDeckNameFromShareInput(rawInput)

    suspend fun submitDeck(payload: SubmitDeckPayload, cardMap: Map<String, List<DeckCardOption>>): ApiResult<SubmitDeckResult> =
        submit(payload, cardMap, BioDeckShareRemoteSource::getWikiCookies, { WikiWriteOwner.capture(it) },
            BioDeckShareRemoteSource::fetchCsrfToken, { WikiEngine.client.newCall(it).awaitResponseText() })

    internal suspend fun submit(payload: SubmitDeckPayload, cardMap: Map<String, List<DeckCardOption>>,
        getCookies: () -> String?, capture: (String) -> WikiWriteOwner, getToken: suspend (String) -> String?,
        send: suspend (Request) -> util.HttpTextResponse): ApiResult<SubmitDeckResult> =
        withContext(Dispatchers.IO) {
            val cookies = getCookies()
            if (cookies.isNullOrBlank()) {
                return@withContext ApiResult.Error("登录 Wiki 后可发布卡组", kind = ErrorKind.NOT_FOUND)
            }

            try {
                val owner = capture(cookies)
                val options = cardMap[payload.faction].orEmpty()
                if (options.isEmpty()) {
                    return@withContext ApiResult.Error("当前阵营卡牌数据为空", kind = ErrorKind.NOT_FOUND)
                }
                val selectedCards = BioDeckShareParsers.resolveSelectedCards(payload.selectedCardIds, options)

                val sortedCards = selectedCards.sortedWith(
                    compareByDescending<DeckCardOption> { rarityRank(it.normalizedQuality) }
                        .thenBy { if (it.index >= 0) it.index else Int.MAX_VALUE }
                )

                val shareCode = BioDeckShareParsers.publicationShareCode(
                    cards = sortedCards, faction = payload.faction,
                    allowMissingShareCode = payload.allowMissingShareCode
                )

                val token = owner.run { getToken(cookies) }
                    ?: return@withContext ApiResult.Error("获取 CSRF Token 失败", kind = ErrorKind.NETWORK)

                val safeDeckName = sanitizeDeckName(payload.deckName)
                val targetTitle = "$TARGET_BASE/$safeDeckName"
                val shareCodeWidget = if (shareCode.isBlank()) "" else
                    "{{#widget:晶源感染卡组分享码生成|cardIds=${sortedCards.map { it.cardId }.filter { it.isNotBlank() }.joinToString(",")}|faction=${payload.faction}}}"

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
                    if (shareCode.isNotBlank()) appendLine("<noinclude><!-- 分享码(调试): ${safeTemplateParam(shareCode)} --></noinclude>")
                }

                val formBody = formBodyOf(
                    "action" to "edit",
                    "title" to targetTitle,
                    "text" to wikitext,
                    "summary" to "App 提交: ${payload.deckName}",
                    "createonly" to "true",
                    "token" to token,
                    "format" to "json"
                )

                val request = Request.Builder().url(API).apply {
                    post(formBody)
                    header("Cookie", cookies)
                    header("User-Agent", "CalabiYauVoice/2.0 (Android)")
                }.build()
                owner.run { send(request) }.let { resp ->
                    val respBody = resp.body
                    if (!resp.isSuccessful) {
                        return@withContext ApiResult.Error("发布失败：HTTP ${resp.code}", kind = ErrorKind.NETWORK)
                    }

                    val root = SharedJson.parseToJsonElement(respBody).jsonObject
                    val errorObj = root["error"]?.jsonObject
                    if (errorObj != null) {
                        val code = errorObj["code"]?.jsonPrimitive?.content.orEmpty()
                        val info = errorObj["info"]?.jsonPrimitive?.content.orEmpty()
                        if (code == "moderation-edit-queued") return@withContext ApiResult.Success(SubmitDeckResult(targetTitle, true))
                        val msg = when (code) {
                            "articleexists" -> "同名卡组已存在，请修改卡组名称"
                            "assertuserfailed" -> "登录状态失效，请重新登录"
                            else -> "发布失败：${info.ifBlank { code }}"
                        }
                        return@withContext ApiResult.Error(msg, kind = ErrorKind.UNKNOWN)
                    }

                    editReceipt(root, targetTitle)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ApiResult.Error("发布失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    internal fun editReceipt(root: JsonObject, title: String): ApiResult<SubmitDeckResult> {
        val edit = root["edit"] as? JsonObject
        val moderation = (edit?.get("moderation") as? kotlinx.serialization.json.JsonPrimitive)?.content
        val status = (edit?.get("result") as? kotlinx.serialization.json.JsonPrimitive)?.content
        val pending = moderation in setOf("pending", "queued", "1", "true") && status in setOf(null, "Pending", "Success")
        return if ("error" !in root && (status == "Success" || pending))
            ApiResult.Success(SubmitDeckResult(title, pending))
        else ApiResult.Error("未确认发布结果，请查看 Wiki 后再试", ErrorKind.PARSE)
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
            .replace("#", "＃") // MediaWiki 标题非法字符，触发 invalidtitle
            .replace(Regex("[<>\\[\\]|{}\\p{Cntrl}]"), "")
            .take(50)
            .ifBlank { "未命名卡组" }
    }

    private fun safeTemplateParam(s: String): String {
        return s
            .replace(Regex("\\r?\\n"), " ")
            .replace("|", "｜")
            .replace("<", "＜")
            .replace(">", "＞")
            .replace("[", "［")
            .replace("]", "］")
            .replace("{", "｛")
            .replace("}", "｝")
            .trim()
    }
}
