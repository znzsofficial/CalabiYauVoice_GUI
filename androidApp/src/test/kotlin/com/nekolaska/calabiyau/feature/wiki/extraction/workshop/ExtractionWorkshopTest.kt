package com.nekolaska.calabiyau.feature.wiki.extraction.workshop

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.cache.fetchValidatedCachePayload
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.api.WorkshopApi
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.parser.WorkshopFormat
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.source.WorkshopRemoteSource
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import kotlin.test.*

class ExtractionWorkshopTest {
    private fun fixture(ext: String) = checkNotNull(javaClass.getResource("/fixtures/extraction/workshop/compact.$ext")).readText()
    private val html = fixture("html")
    private val wt = fixture("wt")
    private fun payload(text: String = html, title: String = WORKSHOP_PAGE, source: String = wt) = buildJsonObject {
        putJsonObject("parse") {
            put("title", title)
            putJsonObject("text") { put("*", text) }; putJsonObject("wikitext") { put("*", source) }
        }
    }.toString()

    @Test fun searchesAllTiersAndPreservesPublishedLabelsCostsAndScope() {
        val page = WorkshopRemoteSource.parsePayload(payload())
        val recipe = page.recipes.searchRecipes("折叠").single().recipe
        assertEquals(listOf("精致", "完美", "传说"), recipe.tiers.map { it.quality })
        assertNull(recipe.image)
        val hit = page.recipes.searchRecipes(" 合晶 ").single()
        val tier = hit.materialTiers.single()
        assertEquals("完美", tier.quality); assertEquals("器具研发舱", hit.recipe.room); assertEquals("Lv.1", tier.roomLevel)
        assertEquals(listOf(WorkshopMaterial("材料乙", 3), WorkshopMaterial("合晶", 2)), tier.materials)
        assertEquals("1 小时 20 分", tier.duration); assertEquals(65, tier.speedupEnergy)
        assertTrue(page.recipes.searchRecipes("工具箱").isEmpty()) // upgrade-only material
        assertEquals(WorkshopMaterial("工具箱", 3), page.rooms.single().upgrades.single().materials.single())
        assertEquals("技术能手", page.staff.single().subtitle)
        assertEquals(WorkshopBuff("技术能手", "速度提升 3%", "器具研发舱"), page.buffs.single())
        assertEquals("含 22 件家具", page.decorations.single { it.title == "套装" }.cards.single().detail)
        val season = page.decorations.single { it.title == "赛季珍藏品" }
        assertEquals("「银河」", season.cards.single().name); assertContains(season.notes, "7 件尚未公布")
    }

    @Test fun rejectsWrongPageAndTruncatedSectionsIncludingHiddenMiddleTier() {
        fun without(selector: String) = Jsoup.parse(html).apply { select(selector).remove() }.outerHtml()
        val failures = listOf(
            payload(title = "战斗模式/弦溯战线/物资图鉴"), payload(text = "<div class='redirectMsg'>工坊</div>"),
            payload(source = ""), payload(without(".sec-ws-card__tier:has(.sec-ws-card__tiername:contains(完美))")),
            payload(html.replace("合晶×2", "合晶")), payload(html.replace("材料乙×3、合晶×2", "合晶×2")),
            payload(without(".sec-ws-lv__mat")), payload(without(".sec-ws-lv__meta")),
            payload(without("tr:has(td)")), payload(without("td:first-child")), payload(html.replace("共 1 个舱室", "共 2 个舱室")),
            payload(html.replace("家具共 1 件", "家具共 2 件")), payload(html.replace("珍藏品，共 1 件", "珍藏品，共 8 件")),
            payload(html.replace("另有 7 件尚未公布，暂不列出。", "")), payload(without("h3:contains(套装)")),
            payload(without(".sec-ws-card:has(.sec-ws-card__title:contains(暖木套装))"))
        )
        failures.forEachIndexed { i, raw -> assertFailsWith<WorkshopFormat>("case $i") { WorkshopRemoteSource.parsePayload(raw) } }
    }

    @Test fun onlyTheConfirmedStandaloneRecipePlaceholderIsUnspecified() {
        fun parse(rendered: String, declared: String = rendered) = WorkshopRemoteSource.parsePayload(
            payload(html.replace("材料甲×1", rendered), source = wt.replace("材料甲×1", declared)))
        val page = parse("#0×0")
        val tier = page.recipes.searchRecipes("折叠").single().recipe.tiers.first()
        assertEquals(RecipeCost.Unspecified, tier.cost); assertTrue(tier.materials.isEmpty())
        assertEquals("20 分", tier.duration); assertTrue(page.recipes.searchRecipes("#0").isEmpty())
        for (value in listOf("材料甲×0", "材料甲×-2", "#0×1", "#0×00", "#0×0、材料甲×1")) {
            assertFailsWith<WorkshopFormat>(value) { parse(value) }
        }
        assertFailsWith<WorkshopFormat> { parse("#0×0", "材料甲×1") }
        assertFailsWith<WorkshopFormat> { parse("材料甲×1", "#0×0") }
        assertFailsWith<WorkshopFormat> { WorkshopRemoteSource.parsePayload(payload(html.replace("工具箱<em>×3", "#0<em>×0"),
            source = wt.replace("工具箱<em>×3", "#0<em>×0"))) }
    }

    @Test fun invalidRefreshKeepsGoodDiskAgeAndDoesNotTurnParseFailureIntoNetworkFailure() = runBlocking {
        val good = payload(); val bad = payload(html.replace("材料乙×3、合晶×2", "合晶×2"))
        var writes = 0
        suspend fun load(disk: String?) = WorkshopApi.load(false) {
            WorkshopRemoteSource.fetchValidated { validate -> fetchValidatedCachePayload(true, { bad },
                { disk?.let { OfflineCache.CacheResult(it, true, 81L) } }, { writes++ }, validate) }
        }
        val offline = assertIs<ApiResult.Success<WorkshopPage>>(load(good))
        assertTrue(offline.isOffline); assertEquals(81L, offline.cacheAgeMs)
        val failure = assertIs<ApiResult.Error>(load(null))
        assertEquals(ErrorKind.PARSE, failure.kind)
        assertEquals("暂时无法显示完整的工坊资料", failure.message)
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(load(bad)).kind)
        assertEquals(0, writes)
    }
}
