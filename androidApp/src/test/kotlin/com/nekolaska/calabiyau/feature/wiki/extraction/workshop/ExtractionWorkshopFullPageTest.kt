package com.nekolaska.calabiyau.feature.wiki.extraction.workshop

import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.source.WorkshopRemoteSource
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

class ExtractionWorkshopFullPageTest {
    @Test fun november21WorkshopMatchesEveryRecipeTierAndUpgradeOffline() {
        val path = System.getenv("EXTRACTION_SPLIT_AUDIT_DIR")
        assumeTrue("Set EXTRACTION_SPLIT_AUDIT_DIR to the November 21 evening captures", !path.isNullOrBlank())
        val dir = File(requireNotNull(path))
        val page = WorkshopRemoteSource.parsePayload(dir.resolve("workshop.json").readText())
        val html = Jsoup.parse(dir.resolve("workshop.html").readText())
        assertEquals(119, html.select(".sec-ws-card").size)
        assertEquals(60, page.recipes.size); assertEquals(226, page.recipes.sumOf { it.tiers.size })
        assertEquals(7, page.rooms.size); assertEquals(31, page.rooms.sumOf { it.upgrades.size })
        assertEquals(24, page.staff.size); assertEquals(5, page.buffs.size)
        assertEquals(mapOf("家具" to 16, "套装" to 8, "赛季珍藏品" to 4), page.decorations.associate { it.title to it.cards.size })
        assertEquals(119, page.recipes.size + page.rooms.size + page.staff.size + page.decorations.sumOf { it.cards.size })

        for (card in html.select(".sec-ws-card:has(.sec-ws-card__recipe)")) {
            val name = card.selectFirst(".sec-ws-card__title")!!.text()
            val recipe = page.recipes.single { it.name == name }
            assertEquals(card.select(".sec-ws-card__tiername").eachText(), recipe.tiers.map { it.quality }, name)
            assertEquals(card.select(".sec-ws-card__lv").eachText(), recipe.tiers.map { it.roomLevel }, name)
            assertEquals(card.select(".sec-ws-card__cost").eachText(), recipe.tiers.map { tier ->
                when (val cost = tier.cost) {
                    is RecipeCost.Listed -> cost.materials.joinToString("、") { "${it.name}×${it.quantity}" }
                    RecipeCost.Unspecified -> "#0×0"
                }
            }, name)
            assertEquals(card.select(".sec-ws-card__time").eachText(), recipe.tiers.map { "${it.duration} · 加速 ${it.speedupEnergy}" }, name)
        }
        assertEquals(listOf("基础护甲充能球" to "优秀", "弦-5针剂" to "优秀"), page.recipes.flatMap { recipe ->
            recipe.tiers.filter { it.cost == RecipeCost.Unspecified }.map { recipe.name to it.quality }
        })
        val rawUpgrades = html.select(".sec-ws-ladder")
        for (list in rawUpgrades) {
            val name = list.previousElementSibling()!!.selectFirst(".mw-headline")!!.text()
            val rows = page.rooms.single { it.overview.name == name }.upgrades
            assertEquals(list.select(".sec-ws-lv__lv").eachText(), rows.map { it.level })
            assertEquals(list.select(".sec-ws-lv__mat").eachText(), rows.flatMap { row -> row.materials.map { "${it.name}×${it.quantity}" } })
        }
        assertEquals(listOf("精致", "卓越", "完美", "传说"), page.recipes.single { it.name == "超阈空间折叠包" }.tiers.map { it.quality })
        val lateHit = page.recipes.searchRecipes("弦能缓冲翼甲").single { it.recipe.name == "警探" }
        assertEquals(listOf("传说"), lateHit.materialTiers.map { it.quality })
        assertContains(page.rooms.single { it.overview.name == "收藏厅" }.upgrades.single().stats, "收集上限 200")
        assertContains(page.staffNotes, "600/秒"); assertContains(page.decorations.last().notes, "7 件尚未公布")
    }
}
