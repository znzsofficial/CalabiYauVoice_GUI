package com.nekolaska.calabiyau.feature.wiki.bio

import com.nekolaska.calabiyau.feature.wiki.bio.api.BioTabLoader
import com.nekolaska.calabiyau.feature.wiki.bio.model.*
import com.nekolaska.calabiyau.feature.wiki.bio.source.BioCardPageSourceResult
import data.ApiResult
import kotlinx.coroutines.*
import kotlin.test.*

class BioTabLoaderTest {
    private val pc = """<div class="gallerygrid-item zombie-card" data-param1="3" data-param2="防御" data-param3="晶源体"><div class="zombie-card__name">测试卡</div></div>"""
    private val mobile = """<div class="gallerygrid-item zombie-card-mobile" data-param1="超弦体" data-param2="进攻" data-param3="4"><div class="zombie-card-mobile__name">移动卡</div></div>"""
    private val deck = """<div class="mw-parser-output"><table class="klbqtable"><tr><th colspan="2">测试卡组</th></tr><tr><th>分享作者</th><td>测试作者</td></tr></table></div>"""

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

    @Test fun emptyPcAndCancellationDoNotBecomeSuccessfulEmptyCatalogs() = runBlocking {
        val empty = BioTabLoader(source = { _, _, _ -> BioCardPageSourceResult("<p>建设中</p>", false, 0) })
        assertIs<ApiResult.Error>(empty.load(BioCatalogTab.PC, false))
        val cancelled = BioTabLoader(source = { _, _, _ -> throw CancellationException("closed") })
        assertFailsWith<CancellationException> { cancelled.load(BioCatalogTab.MOBILE, false) }
        Unit
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
