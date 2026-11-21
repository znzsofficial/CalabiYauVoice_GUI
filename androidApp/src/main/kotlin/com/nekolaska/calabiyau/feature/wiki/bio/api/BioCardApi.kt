package com.nekolaska.calabiyau.feature.wiki.bio.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.wiki.UnusableWikiHtml
import com.nekolaska.calabiyau.feature.wiki.bio.model.*
import com.nekolaska.calabiyau.feature.wiki.bio.parser.BioCardParsers
import com.nekolaska.calabiyau.feature.wiki.bio.source.BioCardPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.bio.source.BioCardRemoteSource
import data.ApiResult
import data.ErrorKind
import data.ioApiCall
import kotlinx.coroutines.CancellationException

object BioCardApi {
    private val loader = BioTabLoader(loadPc = { force -> fetchTab(BioCatalogTab.PC, force) })
    private val catalogs = BioCatalogTab.entries.associateWith { tab -> BioTabCatalog(tab, loader) }

    suspend fun fetchTab(tab: BioCatalogTab, forceRefresh: Boolean = false): ApiResult<CardPageData> =
        catalogs.getValue(tab).fetchPage(forceRefresh)

    internal fun invalidateDeck() = catalogs.getValue(BioCatalogTab.DECK).invalidate()
}

private class BioTabCatalog(private val tab: BioCatalogTab, private val loader: BioTabLoader) : CachedWikiApi<CardPageData>("BioCard.$tab") {
    fun invalidate() = updateCache(null)
    suspend fun fetchPage(force: Boolean): ApiResult<CardPageData> = fetch(forceRefresh = force,
        allowMemoryCache = getCachedValue()?.deckEditorError == null)
    override suspend fun fetchFromNetwork(forceRefresh: Boolean) = loader.load(tab, forceRefresh)
    override fun shouldCache(value: CardPageData) = value.deckEditorError == null &&
        (tab != BioCatalogTab.PC || value.pcCards.any { it.refreshProbability != null })
}

/** Independent tab dependencies. A broken mobile page cannot take down PC cards or deck browsing. */
internal class BioTabLoader(
    private val source: suspend (String, String, Boolean) -> BioCardPageSourceResult? = BioCardRemoteSource::fetchPageHtml,
    private val cardMap: suspend (Boolean) -> ApiResult<Map<String, List<DeckCardOption>>> = BioDeckShareApi::fetchDeckCardMap,
    private val loadPc: (suspend (Boolean) -> ApiResult<CardPageData>)? = null
) {
    suspend fun load(tab: BioCatalogTab, force: Boolean): ApiResult<CardPageData> = ioApiCall("获取卡牌数据失败") {
        val pcPage = "战斗模式/晶源感染/PC端卡牌筛选"
        val mobilePage = "战斗模式/晶源感染/移动端卡牌筛选"
        val deckPage = "晶源感染卡组分享"
        val page = when (tab) { BioCatalogTab.PC -> pcPage; BioCatalogTab.MOBILE -> mobilePage; BioCatalogTab.DECK -> deckPage }
        val key = when (tab) { BioCatalogTab.PC -> "bio_cards_pc"; BioCatalogTab.MOBILE -> "bio_cards_mobile"; BioCatalogTab.DECK -> "bio_cards_decks" }
        val raw = try { source(page, key, force) } catch (e: UnusableWikiHtml) {
            return@ioApiCall ApiResult.Error(e.message.orEmpty(), kind = ErrorKind.PARSE)
        } ?: return@ioApiCall ApiResult.Error("无法获取当前卡牌页面", kind = ErrorKind.NETWORK)
        val empty = CardPageData(emptyList(), emptyList(), emptyList(), BioCardRemoteSource.pageUrl(pcPage),
            BioCardRemoteSource.pageUrl(mobilePage), BioCardRemoteSource.pageUrl(deckPage), loadedTab = tab)
        when (tab) {
            BioCatalogTab.PC -> {
                // Refresh probabilities are supplemental. A failed mode page must not discard
                // the PC cards we already have or become a permanent memory hit.
                val mode = try {
                    source("战斗模式/晶源感染", "bio_cards_mode", force)?.let { it to BioCardParsers.parseRefreshProbabilities(it.html) }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                val cards = BioCardParsers.parsePcCards(raw.html, mode?.second.orEmpty())
                if (cards.isEmpty()) ApiResult.Error("未找到 PC 卡牌数据", kind = ErrorKind.PARSE)
                else ApiResult.Success(empty.copy(pcCards = cards), isOffline = raw.isFromCache || mode?.first?.isFromCache == true,
                    cacheAgeMs = maxOf(raw.ageMs, mode?.first?.ageMs ?: 0L))
            }
            BioCatalogTab.MOBILE -> {
                val cards = BioCardParsers.parseMobileCards(raw.html)
                if (cards.isEmpty()) ApiResult.Error("未找到移动端卡牌数据", kind = ErrorKind.PARSE)
                else ApiResult.Success(empty.copy(mobileCards = cards), isOffline = raw.isFromCache, cacheAgeMs = raw.ageMs)
            }
            BioCatalogTab.DECK -> {
                // Official indexes are needed only by decks; verified PC cards complete the editor pool.
                val mapResult = cardMap(force)
                val official = (mapResult as? ApiResult.Success)?.value.orEmpty()
                val pcResult = loadPc?.invoke(force) ?: load(BioCatalogTab.PC, force)
                val pc = (pcResult as? ApiResult.Success)?.value?.pcCards.orEmpty()
                val indexes = official.mapValues { (_, cards) -> cards.filter { it.cardId.isNotBlank() && it.index >= 0 }.associate { it.cardId to it.index } }
                val decks = BioCardParsers.parseDecks(raw.html, indexes)
                if (decks.isEmpty()) return@ioApiCall ApiResult.Error("未找到卡组分享数据", kind = ErrorKind.PARSE)
                val editorError = (mapResult as? ApiResult.Error)?.message ?: (pcResult as? ApiResult.Error)?.message
                // Deck browsing remains available if editor-only dependencies fail; refresh retries them explicitly.
                ApiResult.Success(empty.copy(pcCards = pc, decks = decks, deckCardMap = official, deckEditorError = editorError),
                    isOffline = raw.isFromCache || (pcResult as? ApiResult.Success)?.isOffline == true || (mapResult as? ApiResult.Success)?.isOffline == true,
                    cacheAgeMs = maxOf(raw.ageMs, (pcResult as? ApiResult.Success)?.cacheAgeMs ?: 0L, (mapResult as? ApiResult.Success)?.cacheAgeMs ?: 0L))
            }
        }
    }
}
