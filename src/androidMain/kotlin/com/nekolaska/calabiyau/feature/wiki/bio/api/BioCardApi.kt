package com.nekolaska.calabiyau.feature.wiki.bio.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.feature.wiki.bio.model.CardPageData
import com.nekolaska.calabiyau.feature.wiki.bio.parser.BioCardParsers
import com.nekolaska.calabiyau.feature.wiki.bio.source.BioCardRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * 晶源感染卡牌整合页 API。
 *
 * 聚合以下三个 Wiki 页面：
 * - 战斗模式/晶源感染/PC端卡牌筛选
 * - 战斗模式/晶源感染/移动端卡牌筛选
 * - 晶源感染卡组分享
 */
object BioCardApi {

    init {
        MemoryCacheRegistry.register("BioCardApi", ::clearMemoryCache)
    }

    private const val PC_PAGE = "战斗模式/晶源感染/PC端卡牌筛选"
    private const val MOBILE_PAGE = "战斗模式/晶源感染/移动端卡牌筛选"
    private const val DECK_PAGE = "晶源感染卡组分享"
    private const val MODE_PAGE = "战斗模式/晶源感染"

    @Volatile
    private var cachedData: CardPageData? = null

    fun clearMemoryCache() {
        cachedData = null
    }

    suspend fun fetchAll(forceRefresh: Boolean = false): ApiResult<CardPageData> =
        withContext(Dispatchers.IO) {
            try {
                if (!forceRefresh) {
                    cachedData?.let { return@withContext ApiResult.Success(it) }
                }

                val results = awaitAll(
                    async { BioCardRemoteSource.fetchPageHtml(PC_PAGE, "bio_cards_pc", forceRefresh) },
                    async { BioCardRemoteSource.fetchPageHtml(MOBILE_PAGE, "bio_cards_mobile", forceRefresh) },
                    async { BioCardRemoteSource.fetchPageHtml(DECK_PAGE, "bio_cards_decks", forceRefresh) },
                    async { BioCardRemoteSource.fetchPageHtml(MODE_PAGE, "bio_cards_mode", forceRefresh) }
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

                val refreshProbabilityMap = BioCardParsers.parseRefreshProbabilities(mode.json)

                val data = CardPageData(
                    pcCards = BioCardParsers.parsePcCards(pc.json, refreshProbabilityMap),
                    mobileCards = BioCardParsers.parseMobileCards(mobile.json),
                    decks = BioCardParsers.parseDecks(decks.json, cardIndexMapByFaction),
                    pcWikiUrl = BioCardRemoteSource.pageUrl(PC_PAGE),
                    mobileWikiUrl = BioCardRemoteSource.pageUrl(MOBILE_PAGE),
                    deckWikiUrl = BioCardRemoteSource.pageUrl(DECK_PAGE),
                    refreshProbabilityWikiUrl = BioCardRemoteSource.pageUrl(MODE_PAGE)
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
}
