package com.nekolaska.calabiyau.feature.wiki.bio

import com.nekolaska.calabiyau.feature.wiki.bio.api.BioTabLoader
import com.nekolaska.calabiyau.feature.wiki.bio.model.*
import com.nekolaska.calabiyau.feature.wiki.bio.source.BioCardPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.bio.source.BioCardRemoteSource
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.cache.fetchValidatedCachePayload
import data.ApiResult
import kotlinx.coroutines.*
import kotlin.test.*

class BioTabLoaderTest {
    private val pc = """<div class="gallerygrid-item zombie-card" data-param1="3" data-param2="防御" data-param3="晶源体"><div class="zombie-card__name">测试卡</div></div>"""
    private val mobile = """<div class="gallerygrid-item zombie-card-mobile" data-param1="超弦体" data-param2="进攻" data-param3="4"><div class="zombie-card-mobile__name">移动卡</div></div>"""
    private val deck = """<div class="mw-parser-output"><table class="klbqtable"><tr><th colspan="2">测试卡组</th></tr><tr><th>分享作者</th><td>测试作者</td></tr></table></div>"""

    @Test fun wrongTabAndPlaceholderCannotReplaceUsableOfflineCards() = runBlocking {
        for ((stored, wrongTab) in listOf(pc to mobile, mobile to pc)) {
            var disk = stored
            for (bad in listOf(wrongTab, "<div class='mw-parser-output'>建设中</div>")) {
                val result = fetchValidatedCachePayload(true, { bad }, { OfflineCache.CacheResult(disk, true, 42) },
                    { disk = it }, { BioCardRemoteSource.hasNamedCards(it, mobile = stored == mobile) })
                assertEquals(stored, result?.payload)
                assertEquals(true, result?.isFromCache)
                assertEquals(stored, disk)
            }
        }
    }

    @Test fun pcAndMobileNeverLoadDeckIndexesOrEachOthersPages() = runBlocking {
        for (tab in listOf(BioCatalogTab.PC, BioCatalogTab.MOBILE)) {
            val keys = mutableListOf<String>()
            val loader = BioTabLoader(source = { _, key, _ -> keys += key
                BioCardPageSourceResult(if (key == "bio_cards_pc") pc else if (key == "bio_cards_mobile") mobile else "", false, 0) },
                cardMap = { error("indexes loaded outside deck tab") })
            val result = assertIs<ApiResult.Success<CardPageData>>(loader.load(tab, false))
            assertEquals(tab, result.value.loadedTab)
            assertEquals(if (tab == BioCatalogTab.PC) listOf("bio_cards_pc", "bio_cards_mode") else listOf("bio_cards_mobile"), keys)
        }
    }

    @Test fun deckBrowsingSurvivesEditorDependencyFailureWithoutLoadingMobile() = runBlocking {
        val keys = mutableListOf<String>()
        val loader = BioTabLoader(source = { _, key, _ -> keys += key; BioCardPageSourceResult(deck, true, 42) },
            cardMap = { ApiResult.Error("indexes unavailable") }, loadPc = { ApiResult.Error("PC unavailable") })
        val result = assertIs<ApiResult.Success<CardPageData>>(loader.load(BioCatalogTab.DECK, true))
        assertEquals(listOf("bio_cards_decks"), keys)
        assertEquals("测试卡组", result.value.decks.single().title)
        assertEquals("indexes unavailable", result.value.deckEditorError)
        assertTrue(result.isOffline)
        assertEquals(42L, result.cacheAgeMs)
    }

    @Test fun pcListSurvivesFailureOfOptionalProbabilityPage() = runBlocking {
        val loader = BioTabLoader(source = { _, key, _ ->
            if (key == "bio_cards_mode") throw java.io.IOException("connection reset")
            BioCardPageSourceResult(pc, false, 0)
        })
        val card = assertIs<ApiResult.Success<CardPageData>>(loader.load(BioCatalogTab.PC, false)).value.pcCards.single()
        assertEquals("测试卡", card.name)
        assertNull(card.refreshProbability)
    }

    @Test fun emptyPcAndCancellationDoNotBecomeSuccessfulEmptyCatalogs() = runBlocking {
        val empty = BioTabLoader(source = { _, _, _ -> BioCardPageSourceResult("<p>建设中</p>", false, 0) })
        assertIs<ApiResult.Error>(empty.load(BioCatalogTab.PC, false))
        val cancelled = BioTabLoader(source = { _, _, _ -> throw CancellationException("closed") })
        assertFailsWith<CancellationException> { cancelled.load(BioCatalogTab.MOBILE, false) }
        Unit
    }

    @Test fun onlyOfficialIndexFallsBackAndRemainsRetryableOnOrdinaryNextRead() = runBlocking {
        var online = false
        var indexReads = 0
        val official = mapOf("晶源体" to listOf(DeckCardOption("卡", "id", "精致", false, 1)))
        val loader = BioTabLoader(source = { _, _, _ -> BioCardPageSourceResult(deck, false, 0) },
            cardMap = { indexReads++; ApiResult.Success(official, !online, if (online) 0 else 42) },
            loadPc = { ApiResult.Success(CardPageData(emptyList(), emptyList(), emptyList(), "", "", "")) })
        val api = object : com.nekolaska.calabiyau.core.cache.CachedWikiApi<CardPageData>("offlineDeckIndexTest") {
            override suspend fun fetchFromNetwork(forceRefresh: Boolean) = loader.load(BioCatalogTab.DECK, forceRefresh)
        }
        val first = assertIs<ApiResult.Success<CardPageData>>(api.fetch()); assertTrue(first.isOffline); assertEquals(42L, first.cacheAgeMs)
        online = true
        assertFalse(assertIs<ApiResult.Success<CardPageData>>(api.fetch()).isOffline); assertEquals(2, indexReads)
    }

    @Test fun deckEditorUsesOfficialMapAndPcDependencyOnceWithoutMobileRequests() = runBlocking {
        val keys = mutableListOf<String>()
        var mapCalls = 0
        var pcCalls = 0
        val official = mapOf("晶源体" to listOf(DeckCardOption("已知", "known", "精致", false, 1)))
        val loader = BioTabLoader(source = { _, key, force -> assertTrue(force); keys += key; BioCardPageSourceResult(deck, false, 0) },
            cardMap = { force -> assertTrue(force); mapCalls++; ApiResult.Success(official) },
            loadPc = { force -> assertTrue(force); pcCalls++
                ApiResult.Success(CardPageData(emptyList(), emptyList(), emptyList(), "", "", "")) })
        val result = assertIs<ApiResult.Success<CardPageData>>(loader.load(BioCatalogTab.DECK, true))
        assertEquals(official, result.value.deckCardMap)
        assertNull(result.value.deckEditorError)
        assertEquals(1, mapCalls)
        assertEquals(1, pcCalls)
        assertEquals(listOf("bio_cards_decks"), keys)
    }
}
