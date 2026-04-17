package com.nekolaska.calabiyau.data

import android.webkit.CookieManager
import android.util.Base64
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request
import java.math.BigInteger

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

    private const val PREFIX_BASE = "000B"
    private const val HEX_LEN = 25

    private val FACTION_CODE = mapOf(
        "超弦体" to "0002",
        "晶源体" to "0003"
    )

    private val CODE_FACTION = FACTION_CODE.entries.associate { (k, v) -> v to k }

    data class DeckCardOption(
        val name: String,
        val cardId: String,
        val quality: String,
        val isDefault: Boolean,
        val index: Int
    ) {
        val normalizedQuality: String
            get() = normalizeQuality(quality)
    }

    data class SubmitDeckPayload(
        val deckName: String,
        val author: String,
        val intro: String,
        val faction: String,
        val selectedCardIds: List<String>
    )

    data class SubmitDeckResult(
        val title: String,
        val isPending: Boolean
    )

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

                val url = buildUrl(
                    "action" to "query",
                    "prop" to "revisions",
                    "titles" to WIKI_JSON_PAGE,
                    "rvprop" to "content",
                    "rvslots" to "main",
                    "format" to "json"
                )

                val body = httpGet(url)
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
                        put(faction, parseFactionCards(value))
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

    fun encodeShareCode(cardIndexes: List<Int>, faction: String): String {
        val factionCode = FACTION_CODE[faction]
            ?: throw IllegalArgumentException("无效阵营: $faction")
        if (cardIndexes.isEmpty()) {
            throw IllegalArgumentException("卡牌不能为空")
        }

        val mask = cardIndexes.fold(BigInteger.ZERO) { acc, idx ->
            if (idx < 0) acc else acc.setBit(idx)
        }

        val requiredLen = maxOf((mask.bitLength() + 3) / 4, HEX_LEN)
        val hex = mask.toString(16).uppercase().padStart(requiredLen, '0')
        val raw = "$PREFIX_BASE|$factionCode|$hex"
        val base64 = Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "$base64**"
    }

    /**
     * @return Pair(解析出的 cardId 列表, 分享码中自带的阵营)
     */
    fun decodeShareCode(
        rawInput: String,
        expectedFaction: String,
        indexToCardId: Map<Int, String>
    ): Pair<List<String>, String> {
        val code = extractActualShareCode(rawInput)
        if (code.isBlank()) throw IllegalArgumentException("分享码为空")

        val b64 = code.removeSuffix("**")
        val decoded = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
        val parts = decoded.split("|")
        if (parts.size < 3) throw IllegalArgumentException("分享码格式不正确")

        val faction = CODE_FACTION[parts[1]]
            ?: throw IllegalArgumentException("分享码阵营无效")
        if (faction != expectedFaction) {
            throw IllegalArgumentException("阵营不匹配：分享码是“$faction”，当前为“$expectedFaction”")
        }

        val hex = parts[2].trim()
        if (hex.isBlank()) return emptyList<String>() to faction

        val mask = BigInteger(hex, 16)
        val ids = buildList {
            var bit = 0
            while (bit < mask.bitLength()) {
                if (mask.testBit(bit)) {
                    indexToCardId[bit]?.let { add(it) }
                }
                bit++
            }
        }
        return ids to faction
    }

    fun extractDeckNameFromShareInput(rawInput: String): String? {
        val parts = rawInput.split('｜', '|').map { it.trim() }.filter { it.isNotBlank() }
        return if (parts.size >= 3) parts[parts.size - 2] else null
    }

    fun extractActualShareCode(rawInput: String): String {
        val cleaned = rawInput.trim()
        if (cleaned.isBlank()) return ""
        if ('｜' !in cleaned && '|' !in cleaned) return cleaned
        val parts = cleaned.split('｜', '|').map { it.trim() }.filter { it.isNotBlank() }
        return parts.lastOrNull().orEmpty()
    }

    suspend fun submitDeck(payload: SubmitDeckPayload, cardMap: Map<String, List<DeckCardOption>>): ApiResult<SubmitDeckResult> =
        withContext(Dispatchers.IO) {
            val cookies = getWikiCookies()
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

                val token = fetchCsrfToken(cookies)
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
                            else -> "发布失败：${if (info.isNotBlank()) info else code}"
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

    private fun parseFactionCards(arr: JsonArray): List<DeckCardOption> {
        return arr.mapNotNull { element ->
            when (element) {
                is JsonObject -> {
                    val name = element["name"]?.jsonPrimitive?.content.orEmpty().trim()
                    if (name.isBlank()) return@mapNotNull null
                    DeckCardOption(
                        name = name,
                        cardId = element["cardid"]?.jsonPrimitive?.content.orEmpty().trim(),
                        quality = element["quality"]?.jsonPrimitive?.content.orEmpty().trim(),
                        isDefault = element["default"]?.let { boolOf(it) } ?: false,
                        index = element["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
                    )
                }
                else -> null
            }
        }
    }

    private fun boolOf(el: JsonElement): Boolean {
        return el.jsonPrimitive.content.equals("true", ignoreCase = true)
    }

    private fun normalizeQuality(raw: String): String {
        return when {
            raw.contains("完") -> "完美"
            raw.contains("卓") -> "卓越"
            raw.contains("精") -> "精致"
            else -> raw
        }
    }

    private fun rarityRank(q: String): Int = when (normalizeQuality(q)) {
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

    private fun getWikiCookies(): String? {
        return try {
            val cm = CookieManager.getInstance()
            val rootCookies = cm.getCookie("https://wiki.biligame.com") ?: ""
            val klbqCookies = cm.getCookie("https://wiki.biligame.com/klbq/") ?: ""
            val cookieMap = mutableMapOf<String, String>()
            ("$rootCookies; $klbqCookies").split(";").forEach { part ->
                val trimmed = part.trim()
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    cookieMap[trimmed.substring(0, eq).trim()] = trimmed.substring(eq + 1).trim()
                }
            }
            if (cookieMap.isEmpty()) return null
            cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchCsrfToken(cookies: String): String? {
        val url = buildUrl(
            "action" to "query",
            "meta" to "tokens",
            "type" to "csrf",
            "format" to "json"
        )
        val body = httpGetWithCookies(url, cookies) ?: return null
        return runCatching {
            val root = SharedJson.parseToJsonElement(body).jsonObject
            root["query"]?.jsonObject
                ?.get("tokens")?.jsonObject
                ?.get("csrftoken")?.jsonPrimitive
                ?.content
        }.getOrNull()
    }

    private fun buildUrl(vararg params: Pair<String, String>): String {
        val query = params.joinToString("&") { (k, v) ->
            "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
        }
        return "$API?$query"
    }

    private fun httpGet(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "CalabiYauVoice/2.0 (Android)")
                .build()
            WikiEngine.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body.string()
                if (!body.trimStart().startsWith("{") && !body.trimStart().startsWith("[")) return null
                body
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGetWithCookies(url: String, cookies: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookies)
                .header("User-Agent", "CalabiYauVoice/2.0 (Android)")
                .build()
            WikiEngine.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body.string()
                if (!body.trimStart().startsWith("{") && !body.trimStart().startsWith("[")) return null
                body
            }
        } catch (_: Exception) {
            null
        }
    }
}
