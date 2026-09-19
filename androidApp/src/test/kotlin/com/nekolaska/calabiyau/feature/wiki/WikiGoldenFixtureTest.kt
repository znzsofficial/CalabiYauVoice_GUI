package com.nekolaska.calabiyau.feature.wiki

import com.nekolaska.calabiyau.feature.wiki.activity.parser.ActivityParsers
import com.nekolaska.calabiyau.feature.wiki.announcement.parser.AnnouncementParsers
import com.nekolaska.calabiyau.feature.weapon.detail.WeaponDetailApi
import data.SharedJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Golden-fixture tests against REAL production responses captured from
 * wiki.biligame.com (2026-09-19). Unlike hand-written fixtures, these fail
 * locally whenever the Wiki changes its HTML/JSON structure — no live flag needed.
 *
 * Refresh: fetch the same API URLs and replace files under
 * androidApp/src/test/resources/fixtures/, then update the anchors below.
 */
class WikiGoldenFixtureTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/fixtures/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
            // PowerShell 写出的文件可能带 BOM，解析前剥离
            .removePrefix("\uFEFF")

    private fun parseResponse(body: String): JsonObject =
        SharedJson.parseToJsonElement(body).jsonObject["parse"]!!.jsonObject

    private fun parseWikitextAndHtml(body: String): Pair<String, String> {
        val parse = parseResponse(body)
        val wikitext = parse["wikitext"]!!.jsonObject["*"]!!.jsonPrimitive.content
        val html = parse["text"]!!.jsonObject["*"]!!.jsonPrimitive.content
        return wikitext to html
    }

    @Test
    fun announcementsAskGoldenFixtureParsesWithDates() {
        val body = fixture("announcement_ask.json")
        val results = SharedJson.parseToJsonElement(body)
            .jsonObject["query"]?.jsonObject?.get("results") as? JsonObject
            ?: JsonObject(emptyMap())
        val items = AnnouncementParsers.parseAnnouncements(results)
        assertTrue(items.isNotEmpty(), "announcement fixture parsed empty — structure or query drifted")
        items.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.wikiUrl.startsWith("https://wiki.biligame.com"))
        }
    }

    @Test
    fun activityGoldenFixtureParsesEventCards() {
        val html = parseWikitextAndHtml(fixture("activity_parse.json")).second
        val items = ActivityParsers.parseActivities(html)
        assertTrue(items.size >= 3, "activity fixture rows=${items.size} — card structure drifted")
        items.forEach {
            assertTrue(it.entry.title.isNotBlank())
            assertTrue(it.entry.wikiUrl.startsWith("https://wiki.biligame.com"))
        }
    }

    @Test
    fun jingfengGoldenFixtureParsesNewGenStructure() {
        val (wikitext, html) = parseWikitextAndHtml(fixture("weapon_jingfeng_parse.json"))
        val detail = WeaponDetailApi.parseWeaponWikitext("静风", wikitext, renderedHtml = html)
        assertNotNull(detail)
        // 新代结构：距离伤害在主 {{武器}}，数据表章节已改名
        assertTrue(detail.damageTable.any { it.distance == "10米" && it.head.isNotBlank() })
        assertTrue(detail.baseDamage.isNotBlank())
        assertTrue(detail.user.contains("、"))
        assertEquals("精确射手步枪", detail.type)
    }

    @Test
    fun beijixingGoldenFixtureParsesMobileDistanceRows() {
        val (wikitext, html) = parseWikitextAndHtml(fixture("weapon_beijixing_parse.json"))
        val detail = WeaponDetailApi.parseWeaponWikitext("北极星", wikitext, renderedHtml = html)
        assertNotNull(detail)
        val mobile = detail.damageTable.filter { it.distance.startsWith("移动端·") }
        assertTrue(mobile.isNotEmpty(), "mobile distance rows missing — new-gen mobile params drifted")
        assertTrue(mobile.all { it.head.isNotBlank() })
        // 新代页面主模板仍带 射速 参数（如 666），属性区正常显示
        assertEquals("666", detail.fireRate)
    }

    @Test
    fun dajianGoldenFixtureExpandsMeleeNestedLists() {
        val (wikitext, html) = parseWikitextAndHtml(fixture("weapon_dajian_parse.json"))
        val detail = WeaponDetailApi.parseWeaponWikitext("大剑", wikitext, isTactical = false, renderedHtml = html)
        assertNotNull(detail)
        assertTrue(
            detail.damageTable.any { it.distance.contains("轻击") && it.head.isNotBlank() },
            "melee nested-list expansion drifted: ${detail.damageTable}"
        )
        // 已知缺口：近战的 弦化伤害 只存在于渲染的 武器数据 表格中，当前 parser 不提取（stringDamage 为空）。
        // 若后续补提取，请把本断言改为 assertEquals("135%", detail.stringDamage)
        assertEquals("", detail.stringDamage)
    }

    @Test
    fun xiaomifengGoldenFixtureParsesSecondaryDistanceTable() {
        val (wikitext, html) = parseWikitextAndHtml(fixture("weapon_xiaomifeng_parse.json"))
        val detail = WeaponDetailApi.parseWeaponWikitext("小蜜蜂", wikitext, renderedHtml = html)
        assertNotNull(detail)
        val distanceRows = detail.damageTable.filter { it.distance.contains("米") }
        assertTrue(distanceRows.isNotEmpty(), "secondary distance table drifted")
        assertTrue(distanceRows.any { it.upper.isNotBlank() }, "distance table lost upper column")
    }
}
