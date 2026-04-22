package com.nekolaska.calabiyau.data

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.net.URLEncoder

object StringerPushCardApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val PAGE_NAME = "战斗模式/超弦推进"
    private const val WIKI_URL = "https://wiki.biligame.com/klbq/%E6%88%98%E6%96%97%E6%A8%A1%E5%BC%8F/%E8%B6%85%E5%BC%A6%E6%8E%A8%E8%BF%9B"

    data class CardPage(
        val title: String,
        val summary: String,
        val wikiUrl: String,
        val cards: List<ModeCard>
    )

    data class ModeCard(
        val name: String,
        val category: String,
        val rarity: Int,
        val effect: String,
        val roles: List<String>,
        val imageUrl: String?
    )

    private var cachedPage: CardPage? = null
    private val json = SharedJson

    fun clearMemoryCache() {
        cachedPage = null
    }

    suspend fun fetch(forceRefresh: Boolean = false): ApiResult<CardPage> {
        if (!forceRefresh) {
            cachedPage?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedPage = it.value
        }
    }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<CardPage> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(PAGE_NAME, "UTF-8")
            val url = "$API?action=parse&page=$encoded&prop=text&format=json"
            val result = OfflineCache.fetchWithCache(
                type = OfflineCache.Type.GAME_MODES,
                key = "stringer_push_cards",
                forceRefresh = forceRefresh
            ) { WikiEngine.safeGet(url) } ?: return@withContext ApiResult.Error(
                "获取超弦推进卡牌失败，且无离线缓存",
                kind = ErrorKind.NETWORK
            )

            val root = json.parseToJsonElement(result.json).jsonObject
            val html = root["parse"]?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("*")?.jsonPrimitive?.content
                ?: return@withContext ApiResult.Error("解析超弦推进卡牌 HTML 失败", kind = ErrorKind.PARSE)

            val page = parseHtml(html)
            if (page.cards.isEmpty()) {
                ApiResult.Error("未找到超弦推进卡牌数据", kind = ErrorKind.NOT_FOUND)
            } else {
                ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
            }
        } catch (e: Exception) {
            ApiResult.Error("获取超弦推进卡牌失败: ${e.message}", kind = e.toErrorKind())
        }
    }

    private fun parseHtml(html: String): CardPage {
        val document = Jsoup.parse(html)
        val content = document.selectFirst(".mw-parser-output") ?: document.body()

        val summary = content.select("p")
            .map { it.text().trim() }
            .firstOrNull { it.isNotBlank() && !it.contains("此页面内容为【移动端】独有内容") }
            .orEmpty()

        val cardsHeading = content.selectFirst("span.mw-headline#卡牌")
        val cardsRoot = cardsHeading?.parent()
        val gallery = generateSequence(cardsRoot?.nextElementSibling()) { it.nextElementSibling() }
            .firstOrNull { it.hasClass("gallerygrid") }

        val cards = gallery?.select(".gallerygrid-item.mobile-card")?.mapNotNull { item ->
            val name = item.selectFirst(".mobile-card-name")?.text()?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null

            val itemRows = item.select(".mobile-card-item, .mobile-card-role")
            val category = item.attr("data-param1").ifBlank {
                findItemValue(itemRows, "分类")
            }
            val rarity = item.attr("data-param2").toIntOrNull() ?: 0
            val rawEffect = item.selectFirst(".mobile-card-desc .card-item-value")?.text()?.trim().orEmpty()
            val roles = findItemValue(itemRows, "适用角色")
                .split("、")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val effect = rawEffect.trim()

            ModeCard(
                name = name,
                category = category,
                rarity = rarity,
                effect = effect,
                roles = roles,
                imageUrl = item.selectFirst("img")?.attr("src")
            )
        }.orEmpty()

        return CardPage(
            title = "超弦推进卡牌",
            summary = summary,
            wikiUrl = WIKI_URL,
            cards = cards
        )
    }

    private fun findItemValue(rows: org.jsoup.select.Elements, key: String): String {
        return rows.firstOrNull { row ->
            row.selectFirst(".card-item-key")?.text()?.replace("：", "")?.trim() == key
        }?.selectFirst(".card-item-value")?.text()?.trim().orEmpty()
    }
}