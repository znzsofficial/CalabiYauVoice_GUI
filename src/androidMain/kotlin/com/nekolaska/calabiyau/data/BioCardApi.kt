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

                val refreshProbabilityMap = parseRefreshProbabilities(mode.json)

                val data = CardPageData(
                    pcCards = parsePcCards(pc.json, refreshProbabilityMap),
                    mobileCards = parseMobileCards(mobile.json),
                    decks = parseDecks(decks.json),
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
        val rowRegex = Regex(
            """<tr\s+class=\"divsort\"([^>]*)>([\s\S]*?)</tr>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val cellRegex = Regex("""<t[hd][^>]*>([\s\S]*?)</t[hd]>""", RegexOption.DOT_MATCHES_ALL)
        val srcRegex = Regex("""<img[^>]*src=\"([^\"]+)\"[^>]*>""")

        return rowRegex.findAll(html).mapNotNull { match ->
            val attrs = match.groupValues[1]
            val rowHtml = match.groupValues[2]
            val cells = cellRegex.findAll(rowHtml).map { it.groupValues[1] }.toList()
            if (cells.size < 7) return@mapNotNull null

            val faction = attr(attrs, "data-param1")
            val rarity = attr(attrs, "data-param2").toIntOrNull() ?: 0
            val category = attr(attrs, "data-param3")
            val defaultTag = attr(attrs, "data-param4")
            val acquireType = attr(attrs, "data-param5")
            val releaseDate = attr(attrs, "data-param6")
            val firstCell = cells[0]
            val name = cleanHtml(firstCell).lineSequence().lastOrNull { it.isNotBlank() }?.trim().orEmpty()
            val imageUrl = srcRegex.find(firstCell)?.groupValues?.get(1)
            val maxLevel = cleanHtml(cells[4])
            val effect = cleanHtml(cells[5])
            val roles = cleanHtml(cells[6])
                .split(Regex("[、,/\\n]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() && it != "无" }

            PcCard(
                name = name.ifBlank { "未知卡牌" },
                faction = faction,
                rarity = rarity,
                category = category,
                defaultTag = defaultTag,
                acquireType = acquireType,
                releaseDate = releaseDate,
                maxLevel = maxLevel,
                effect = effect,
                roles = roles,
                imageUrl = imageUrl,
                refreshProbability = refreshProbabilityMap[name.ifBlank { "未知卡牌" }]
            )
        }.toList()
    }

    private fun parseRefreshProbabilities(html: String): Map<String, CardRefreshProbability> {
        val sectionMatch = Regex(
            """<span[^>]*id=\"卡牌刷新概率表\"[\s\S]*?</h2>([\s\S]*?)<h2""",
            RegexOption.DOT_MATCHES_ALL
        ).find(html) ?: return emptyMap()

        val tableHtml = Regex(
            """<table[^>]*class=\"klbqtable\"[^>]*>([\s\S]*?)</table>""",
            RegexOption.DOT_MATCHES_ALL
        ).find(sectionMatch.groupValues[1])?.groupValues?.get(1) ?: return emptyMap()

        val rowRegex = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("""<t[hd][^>]*>([\s\S]*?)</t[hd]>""", RegexOption.DOT_MATCHES_ALL)

        return rowRegex.findAll(tableHtml)
            .drop(1)
            .mapNotNull { row ->
                val cells = cellRegex.findAll(row.groupValues[1])
                    .map { cleanHtml(it.groupValues[1]) }
                    .toList()
                if (cells.size < 5) return@mapNotNull null
                val name = cells[0]
                if (name.isBlank()) return@mapNotNull null
                name to CardRefreshProbability(
                    stage1 = cells[1],
                    stage2 = cells[2],
                    stage3 = cells[3],
                    stage4 = cells[4]
                )
            }
            .toMap()
    }

    private fun parseMobileCards(html: String): List<MobileCard> {
        val itemRegex = Regex(
            """<div\s+class=\"divsort gallerygrid-item mobile-card\"([^>]*)>([\s\S]*?)</div>\s*</div>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val srcRegex = Regex("""<img[^>]*src=\"([^\"]+)\"[^>]*>""")
        val nameRegex = Regex("""mobile-card-name\">([\s\S]*?)</div>""")
        val fieldRegex = Regex(
            """card-item-key\">\s*([^<：]+)：</span><span class=\"card-item-value\">([\s\S]*?)</span>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val descRegex = Regex(
            """mobile-card-desc\">[\s\S]*?card-item-value\">([\s\S]*?)</span>""",
            RegexOption.DOT_MATCHES_ALL
        )

        return itemRegex.findAll(html).mapNotNull { match ->
            val attrs = match.groupValues[1]
            val body = match.groupValues[2]
            val values = fieldRegex.findAll(body).associate {
                cleanHtml(it.groupValues[1]) to cleanHtml(it.groupValues[2])
            }
            MobileCard(
                name = cleanHtml(nameRegex.find(body)?.groupValues?.get(1).orEmpty()).ifBlank { "未知卡牌" },
                faction = attr(attrs, "data-param1").ifBlank { values["适用阵营"] ?: "" },
                category = attr(attrs, "data-param2").ifBlank { values["分类"] ?: "" },
                rarity = attr(attrs, "data-param3").toIntOrNull() ?: 0,
                maxLevel = values["最大等级"] ?: "",
                effect = cleanHtml(descRegex.find(body)?.groupValues?.get(1).orEmpty()),
                imageUrl = srcRegex.find(body)?.groupValues?.get(1)
            )
        }.toList()
    }

    private fun parseDecks(html: String): List<SharedDeck> {
        data class Marker(val index: Int, val faction: String)
        val headers = listOf(
            "超弦体卡组" to html.indexOf("id=\"超弦体卡组\""),
            "晶源体卡组" to html.indexOf("id=\"晶源体卡组\"")
        ).filter { it.second >= 0 }.map { Marker(it.second, it.first) }.sortedBy { it.index }

        val tableRegex = Regex("""<table[^>]*class=\"klbqtable\"[^>]*>([\s\S]*?)</table>""", RegexOption.DOT_MATCHES_ALL)
        val cardNameRegex = Regex(
            """(?:zombie-card-name|zombie-card-item-name|card-name)\">([\s\S]*?)</""",
            RegexOption.DOT_MATCHES_ALL
        )
        val titleRegex = Regex(
            """<th[^>]*colspan=\"2\"[^>]*>([\s\S]*?)</th>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val fieldRegex = { label: String ->
            Regex(
                """<tr[^>]*>\s*<th[^>]*>\s*${Regex.escape(label)}\s*</th>\s*<td[^>]*>([\s\S]*?)</td>\s*</tr>""",
                RegexOption.DOT_MATCHES_ALL
            )
        }

        return tableRegex.findAll(html).mapNotNull { match ->
            val start = match.range.first
            val faction = headers.lastOrNull { it.index <= start }?.faction ?: "卡组"
            val tableHtml = match.groupValues[1]
            val sanitizedTableHtml = tableHtml
                .replace(Regex("""<style[^>]*>[\s\S]*?</style>""", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("""<script[^>]*>[\s\S]*?</script>""", RegexOption.DOT_MATCHES_ALL), "")

            val title = cleanHtml(titleRegex.find(sanitizedTableHtml)?.groupValues?.get(1).orEmpty())
            val author = cleanHtml(fieldRegex("分享作者").find(sanitizedTableHtml)?.groupValues?.get(1).orEmpty())
            val shareIdRaw = fieldRegex("分享ID").find(sanitizedTableHtml)?.groupValues?.get(1).orEmpty()
            val intro = cleanHtml(fieldRegex("介绍").find(sanitizedTableHtml)?.groupValues?.get(1).orEmpty())
            val shareId = extractShareId(cleanHtml(shareIdRaw))

            val cardNames = cardNameRegex.findAll(sanitizedTableHtml)
                .map { cleanHtml(it.groupValues[1]) }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()

            if (title.isBlank() && shareId.isBlank() && cardNames.isEmpty()) return@mapNotNull null
            SharedDeck(
                faction = faction,
                title = title.ifBlank { "未命名卡组" },
                author = author,
                shareId = shareId,
                intro = intro,
                cardNames = cardNames
            )
        }.toList()
    }

    private fun attr(html: String, name: String): String {
        val regex = Regex("""$name=\"([^\"]*)\"""")
        return regex.find(html)?.groupValues?.get(1).orEmpty()
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

    private fun pageUrl(pageName: String): String {
        return WIKI_BASE + URLEncoder.encode(pageName, "UTF-8").replace("+", "%20")
    }
}
