package com.nekolaska.calabiyau.data

import android.text.Html
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * 晶源感染卡牌整合页 API。
 *
 * 聚合以下三个 Wiki 页面：
 * - 战斗模式/晶源感染/PC端卡牌筛选
 * - 战斗模式/晶源感染/移动端卡牌筛选
 * - 晶源感染卡组分享
 */
object BioCardApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"

    private const val PC_PAGE = "战斗模式/晶源感染/PC端卡牌筛选"
    private const val MOBILE_PAGE = "战斗模式/晶源感染/移动端卡牌筛选"
    private const val DECK_PAGE = "晶源感染卡组分享"
    private const val MODE_PAGE = "战斗模式/晶源感染"

    @Volatile
    private var cachedData: CardPageData? = null

    fun clearMemoryCache() {
        cachedData = null
    }

    data class CardPageData(
        val pcCards: List<PcCard>,
        val mobileCards: List<MobileCard>,
        val decks: List<SharedDeck>,
        val pcWikiUrl: String,
        val mobileWikiUrl: String,
        val deckWikiUrl: String,
        val refreshProbabilityWikiUrl: String
    )

    data class CardRefreshProbability(
        val stage1: String,
        val stage2: String,
        val stage3: String,
        val stage4: String
    ) {
        val summary: String
            get() = listOf(stage1, stage2, stage3, stage4)
                .mapIndexed { index, value -> "${index + 1}阶段 $value" }
                .joinToString(" · ")
    }

    data class PcCard(
        val name: String,
        val faction: String,
        val rarity: Int,
        val category: String,
        val defaultTag: String,
        val acquireType: String,
        val releaseDate: String,
        val maxLevel: String,
        val effect: String,
        val roles: List<String>,
        val imageUrl: String?,
        val refreshProbability: CardRefreshProbability?
    )

    data class MobileCard(
        val name: String,
        val faction: String,
        val category: String,
        val rarity: Int,
        val maxLevel: String,
        val effect: String,
        val imageUrl: String?
    )

    data class SharedDeck(
        val faction: String,
        val title: String,
        val author: String,
        val shareId: String,
        val intro: String,
        val cardNames: List<String>
    )

    suspend fun fetchAll(forceRefresh: Boolean = false): ApiResult<CardPageData> =
        withContext(Dispatchers.IO) {
            try {
                if (!forceRefresh) {
                    cachedData?.let { return@withContext ApiResult.Success(it) }
                }

                val results = awaitAll(
                    async { fetchPageHtml(PC_PAGE, "bio_cards_pc", forceRefresh) },
                    async { fetchPageHtml(MOBILE_PAGE, "bio_cards_mobile", forceRefresh) },
                    async { fetchPageHtml(DECK_PAGE, "bio_cards_decks", forceRefresh) },
                    async { fetchPageHtml(MODE_PAGE, "bio_cards_mode", forceRefresh) }
                )

                val pc = results[0] ?: return@withContext ApiResult.Error("无法获取 PC 卡牌数据", kind = ErrorKind.NETWORK)
                val mobile = results[1] ?: return@withContext ApiResult.Error("无法获取移动端卡牌数据", kind = ErrorKind.NETWORK)
                val decks = results[2] ?: return@withContext ApiResult.Error("无法获取卡组分享数据", kind = ErrorKind.NETWORK)
                val mode = results[3] ?: return@withContext ApiResult.Error("无法获取刷新概率数据", kind = ErrorKind.NETWORK)

                val cardIndexMapByFaction = when (val deckCardMapResult = BioDeckShareApi.fetchDeckCardMap(forceRefresh)) {
                    is ApiResult.Success -> deckCardMapResult.value.mapValues { (_, options) ->
                        options.mapNotNull { option ->
                            val cardId = option.cardId.trim()
                            val index = option.index
                            if (cardId.isBlank() || index < 0) null else cardId to index
                        }.toMap()
                    }
                    is ApiResult.Error -> emptyMap()
                }

                val refreshProbabilityMap = parseRefreshProbabilities(mode.json)

                val data = CardPageData(
                    pcCards = parsePcCards(pc.json, refreshProbabilityMap),
                    mobileCards = parseMobileCards(mobile.json),
                    decks = parseDecks(decks.json, cardIndexMapByFaction),
                    pcWikiUrl = pageUrl(PC_PAGE),
                    mobileWikiUrl = pageUrl(MOBILE_PAGE),
                    deckWikiUrl = pageUrl(DECK_PAGE),
                    refreshProbabilityWikiUrl = pageUrl(MODE_PAGE)
                )

                if (data.pcCards.isEmpty() && data.mobileCards.isEmpty() && data.decks.isEmpty()) {
                    ApiResult.Error("未找到卡牌数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    cachedData = data
                    ApiResult.Success(
                        data,
                        isOffline = listOf(pc, mobile, decks, mode).any { it.isFromCache },
                        cacheAgeMs = listOf(pc, mobile, decks, mode).maxOfOrNull { it.ageMs } ?: 0L
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error("获取卡牌数据失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    private suspend fun fetchPageHtml(
        pageName: String,
        cacheKey: String,
        forceRefresh: Boolean
    ): OfflineCache.CacheResult? {
        val encoded = URLEncoder.encode(pageName, "UTF-8")
        val url = "$API?action=parse&page=$encoded&prop=text&format=json"
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.BIO_CARDS,
            key = cacheKey,
            forceRefresh = forceRefresh
        ) { WikiEngine.safeGet(url) } ?: return null

        val body = result.json
        val root = SharedJson.parseToJsonElement(body).jsonObject
        val html = root["parse"]?.jsonObject?.get("text")
            ?.jsonObject?.get("*")?.jsonPrimitive?.content
            ?: return null
        return result.copy(json = html)
    }

    private fun parsePcCards(
        html: String,
        refreshProbabilityMap: Map<String, CardRefreshProbability>
    ): List<PcCard> {
        val document = Jsoup.parse(html)
        val rows = document.select("table#CardSelectTr tr.divsort")

        val cards = rows.mapNotNull { row ->
            val cells = row.select("> th, > td")
            if (cells.size < 7) {
                WikiParseLogger.warnMalformed("BioCardApi.parsePcCards", "expected >=7 cells, actual=${cells.size}", row.outerHtml())
                return@mapNotNull null
            }

            val nameCell = cells[0]
            val name = nameCell.selectFirst("big b")?.text()?.trim()
                .orEmpty()
                .ifBlank { nameCell.textNodes().joinToString("") { it.text() }.trim() }
                .ifBlank { "未知卡牌" }

            val roles = cleanHtml(cells[6].html())
                .split(Regex("[、,/\\n]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() && it != "无" }

            PcCard(
                name = name,
                faction = row.attr("data-param1"),
                rarity = row.attr("data-param2").toIntOrNull() ?: 0,
                category = row.attr("data-param3"),
                defaultTag = row.attr("data-param4"),
                acquireType = row.attr("data-param5"),
                releaseDate = row.attr("data-param6"),
                maxLevel = cleanHtml(cells[4].html()),
                effect = cleanHtml(cells[5].html()),
                roles = roles,
                imageUrl = nameCell.selectFirst("img")?.attr("src"),
                refreshProbability = refreshProbabilityMap[name]
            )
        }

        return WikiParseLogger.finishList("BioCardApi.parsePcCards", cards, html, "rows=${rows.size}")
    }

    private fun parseRefreshProbabilities(html: String): Map<String, CardRefreshProbability> {
        val document = Jsoup.parse(html)
        val heading = document.selectFirst("span#卡牌刷新概率表")
        val table = generateSequence(heading?.parent()) { it.nextElementSibling() }
            .flatMap { element -> sequenceOf(element).plus(element.select("table.klbqtable")) }
            .firstOrNull { it.tagName() == "table" && it.hasClass("klbqtable") }

        if (table == null) {
            WikiParseLogger.warnMalformed("BioCardApi.parseRefreshProbabilities", "missing refresh probability table", html)
            return emptyMap()
        }

        val items = table.select("tr").drop(1).mapNotNull { row ->
            val cells = row.select("> th, > td").map { cleanHtml(it.html()) }
            if (cells.size < 5) return@mapNotNull null
            val name = cells[0]
            if (name.isBlank()) return@mapNotNull null
            name to CardRefreshProbability(
                stage1 = cells[1],
                stage2 = cells[2],
                stage3 = cells[3],
                stage4 = cells[4]
            )
        }.toMap()

        return WikiParseLogger.finishMap("BioCardApi.parseRefreshProbabilities", items, html)
    }

    private fun parseMobileCards(html: String): List<MobileCard> {
        val document = Jsoup.parse(html)
        val items = document.select(".mobile-card.gallerygrid-item").map { item ->
            val values = item.select(".mobile-card-item, .mobile-card-item-odd, .mobile-card-desc")
                .mapNotNull { row ->
                    val key = row.selectFirst(".card-item-key")?.text()?.removeSuffix("：")?.trim().orEmpty()
                    val value = row.selectFirst(".card-item-value")?.text()?.trim().orEmpty()
                    if (key.isBlank() || value.isBlank()) null else key to value
                }
                .toMap()

            MobileCard(
                name = item.selectFirst(".mobile-card-name")?.text()?.trim().orEmpty().ifBlank { "未知卡牌" },
                faction = item.attr("data-param1").ifBlank { values["适用阵营"] ?: "" },
                category = item.attr("data-param2").ifBlank { values["分类"] ?: "" },
                rarity = item.attr("data-param3").toIntOrNull() ?: 0,
                maxLevel = values["最大等级"] ?: "",
                effect = values["等级效果描述"] ?: "",
                imageUrl = item.selectFirst("img")?.attr("src")
            )
        }.toList()

        return WikiParseLogger.finishList("BioCardApi.parseMobileCards", items, html)
    }

    private fun parseDecks(
        html: String,
        cardIndexMapByFaction: Map<String, Map<String, Int>>
    ): List<SharedDeck> {
        val document = Jsoup.parse(html)
        val rootChildren = document.select(".mw-parser-output").firstOrNull()?.children().orEmpty()
        var currentFaction = "卡组"
        val decks = mutableListOf<SharedDeck>()

        rootChildren.forEach { element ->
            if (element.tagName().matches(Regex("h[1-6]"))) {
                val title = element.selectFirst("span.mw-headline")?.text()?.trim().orEmpty()
                if (title == "超弦体卡组" || title == "晶源体卡组") {
                    currentFaction = title
                }
                return@forEach
            }

            val tables = when {
                element.tagName() == "table" && element.hasClass("klbqtable") -> listOf(element)
                else -> element.select("table.klbqtable")
            }

            tables.forEach { table ->
                parseDeckTable(table, currentFaction, cardIndexMapByFaction)?.let { decks += it }
            }
        }

        return WikiParseLogger.finishList("BioCardApi.parseDecks", decks, html)
    }

    private fun parseDeckTable(
        table: Element,
        faction: String,
        cardIndexMapByFaction: Map<String, Map<String, Int>>
    ): SharedDeck? {
        val rows = table.select("tr")
        if (rows.isEmpty()) return null

        val title = rows.firstOrNull()?.selectFirst("th[colspan]")?.text()?.trim().orEmpty()
        val fieldMap = rows.drop(1)
            .mapNotNull { row ->
                val key = row.selectFirst("th")?.text()?.trim().orEmpty()
                val valueCell = row.selectFirst("td") ?: return@mapNotNull null
                if (key.isBlank()) null else key to valueCell
            }
            .toMap()

        val author = fieldMap["分享作者"]?.text()?.trim().orEmpty()
        val shareIdCell = fieldMap["分享ID"]
        val shareIdText = shareIdCell?.text().orEmpty()
        val shareId = extractShareId(shareIdText).ifBlank {
            val normalizedFaction = normalizeDeckFaction(faction)
            val cardIndexMap = cardIndexMapByFaction[normalizedFaction].orEmpty()
            extractShareIdFromDynamicScript(shareIdCell?.html().orEmpty(), normalizedFaction, cardIndexMap)
        }
        val intro = fieldMap["介绍"]?.text()?.trim().orEmpty()
        val cardNames = fieldMap["卡牌列表"]
            ?.select(".zombie-card-name, .zombie-card-item-name, .card-name")
            ?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()

        if (title.isBlank() && shareId.isBlank() && cardNames.isEmpty()) return null
        return SharedDeck(
            faction = faction,
            title = title.ifBlank { "未命名卡组" },
            author = author,
            shareId = shareId,
            intro = intro,
            cardNames = cardNames
        )
    }

    private fun cleanHtml(raw: String): String {
        val normalized = raw
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace("&nbsp;", " ")
        return Html.fromHtml(normalized, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace("￼", "")
            .replace("\uFFFC", "")
            .replace('\u00A0', ' ')
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    private fun extractShareId(raw: String): String {
        val cleaned = raw
            .substringAfterLast('｜', raw)
            .substringBefore("**")
            .substringAfterLast(' ', "")
            .trim()
        if (cleaned.isNotBlank()) return cleaned

        return Regex("""[A-Za-z0-9+/=_-]{12,}""")
            .find(raw)
            ?.value
            .orEmpty()
    }

    private fun normalizeDeckFaction(raw: String): String {
        return raw.removeSuffix("卡组").trim()
    }

    private fun extractShareIdFromDynamicScript(
        html: String,
        fallbackFaction: String,
        cardIndexMap: Map<String, Int>
    ): String {
        if (html.isBlank() || cardIndexMap.isEmpty()) return ""

        val cardIdsParam = Regex("""var\s+cardIdsParam\s*=\s*\"([^\"]*)\"""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        if (cardIdsParam.isBlank()) return ""

        val factionFromScript = Regex("""var\s+factionParam\s*=\s*\"([^\"]*)\"""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .trim()
        val faction = if (factionFromScript.isNotBlank()) factionFromScript else fallbackFaction
        if (faction.isBlank()) return ""

        val cardIndexes = cardIdsParam
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { cardId -> cardIndexMap[cardId] }

        if (cardIndexes.isEmpty()) return ""

        return runCatching {
            BioDeckShareApi.encodeShareCode(cardIndexes, faction)
        }.getOrDefault("")
    }

    private fun pageUrl(pageName: String): String {
        return WIKI_BASE + URLEncoder.encode(pageName, "UTF-8").replace("+", "%20")
    }
}
