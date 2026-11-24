package com.nekolaska.calabiyau.feature.wiki.game.parser

import com.nekolaska.calabiyau.feature.wiki.game.api.GameModeApi
import com.nekolaska.calabiyau.feature.wiki.game.model.FieldGuideBlock.Kind
import com.nekolaska.calabiyau.feature.wiki.hub.*
import com.nekolaska.calabiyau.feature.wiki.map.model.RIFT_MAP
import com.nekolaska.calabiyau.feature.wiki.map.model.RIFT_WIDGET_PAGE
import com.nekolaska.calabiyau.feature.wiki.map.parser.MapDetailParsers
import data.SharedJson
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

class ExtractionFieldGuideTest {
    private val mode = GameModeApi.EXTRACTION_MODE

    @Test fun chapterKeepsTheBranchCaptionItsOwnColumnsAndLinkedMap() {
        val html = """<div class="mw-parser-output"><div id="toc"><h2>目录</h2></div>
            <h2><span class="mw-headline">任务系统</span></h2><h3><span class="mw-headline">战斗线（12 个）</span><span>[编辑]</span></h3>
            <p>前往<a href="/klbq/维里昂裂隙">维里昂裂隙</a>。</p>
            <table class="klbqtable"><caption>支链任务（完成战斗-4 后解锁）</caption>
            <tr><th>任务名</th><th>任务目标</th><th>备注</th></tr>
            <tr><td>门阀残响·首战确认</td><td>参与 BOSS 挑战 1 次</td><td>单目标·5 件</td></tr></table>
            <h2><span class="mw-headline">局内事件</span></h2><ol><li><b>传送门开启</b>：倒计时结束后传送门开放。</li></ol>
            <h2><span class="mw-headline">模式音乐</span></h2><p>主界面</p></div>"""
        val detail = GameModeParsers.parseModeWikitext(mode, "== 模式设定 ==\n* 模式地图：[[维里昂裂隙]]", emptyList(), html)
        assertEquals(listOf(RIFT_MAP), detail.maps)
        assertEquals(listOf("任务系统", "局内事件"), detail.chapters.map { it.title })
        val blocks = detail.chapters.first().blocks
        assertEquals(listOf("战斗线（12 个）", "支链任务（完成战斗-4 后解锁）"), blocks.filter { it.kind == Kind.HEADING }.map { it.content.text })
        assertEquals("任务名：门阀残响·首战确认\n任务目标：参与 BOSS 挑战 1 次\n备注：单目标·5 件", blocks.single { it.kind == Kind.RECORD }.content.text)
        val mapUrl = blocks.single { it.kind == Kind.TEXT }.content.spans.single { it.url != null }.url!!
        assertEquals(WikiRoute.MapDetail(RIFT_MAP, null), nativeWikiRoute(mapUrl))
        assertTrue(detail.chapters.last().blocks.single().content.text.startsWith("1. 传送门开启"))
        assertTrue(GameModeParsers.parseModeWikitext(mode, "== 模式设定 ==\n* 模式地图：[[维里昂裂隙]]", emptyList()).chapters.isEmpty())
    }

    @Test fun mapDoesNotMixDifficultyColumnsOrImportNavigation() {
        val wt = "{{地图|简介=维里昂|支持模式=弦溯战线|上线平台=PC端}}"
        val html = """<div class="mw-parser-output"><h2><span class="mw-headline">难度与准入</span></h2>
            <table class="klbqtable table-hover"><tr><th>维度</th><th>基点</th><th>升变</th><th>危峰（未开放）</th></tr>
            <tr><td>单局时长</td><td>25 分钟</td><td>30 分钟</td><td>—</td></tr></table>
            <table class="navbox"><tr><td>站点导航</td></tr></table></div>"""
        fun mapBlock(source: String) = assertNotNull(MapDetailParsers.parseMapWikitext(RIFT_MAP, wt, source)).chapters.single().blocks.single()
        val record = mapBlock(html)
        assertEquals("维度：单局时长\n基点：25 分钟\n升变：30 分钟\n危峰（未开放）：—", record.content.text)
        assertEquals(Kind.TEXT, mapBlock(html.replace("<td>30 分钟</td>", "")).kind) // Do not shift the unknown value under 升变.
        assertTrue(assertNotNull(MapDetailParsers.parseMapWikitext("404基地", wt, html)).chapters.isEmpty())
    }

    @Test fun widgetUsesItsOwnInAppWebDocumentAndSpecificSearchesFindNativeReaders() {
        val widget = extractionGuideLinks.single { it.article == "用户:5205017/StrinovaMapTest" }
        assertContains(widget.label, "测试页")
        assertNull(widget.route)
        assertNull(nativeWikiRoute(widget.originalUrl, maps = setOf(RIFT_MAP)))
        assertTrue(WikiNavigationPolicy.mayLoadDocument(widget.originalUrl))
        assertEquals(WikiRoute.MapDetail(RIFT_MAP, null), matchingHubEntries("弦溯互动地图").first().targetRoute)
        assertEquals(WikiRoute.ExtractionMode, matchingHubEntries("局内事件").first().targetRoute)
    }

    @Test fun completeNovember24RevisionsKeepAllMapAndModeRowsAndMatchWidgetDataIdentity() {
        val path = System.getenv("WIKI_MAP_MODE_AUDIT_DIR")
        assumeTrue("Set WIKI_MAP_MODE_AUDIT_DIR to the captured 120739/120758/120781/120798 revisions", !path.isNullOrBlank())
        fun captured(name: String) = File(requireNotNull(path), name).readText(Charsets.UTF_8)
        val data = SharedJson.parseToJsonElement(captured("120739.txt")).jsonObject.getValue("4203").jsonObject
        assertEquals(RIFT_MAP, data.getValue("name").jsonPrimitive.content)
        assertEquals(8, data.getValue("regions").jsonArray.size)
        assertEquals(10, data.getValue("keys").jsonArray.size)
        assertEquals(120, data.getValue("points").jsonObject.values.sumOf { it.jsonArray.size })
        val widget = Jsoup.parse(captured("120758.html"))
        assertEquals("4203", widget.select(".sm-app[data-map]").single().attr("data-map"))
        assertContains(captured("120758.txt"), "{{#widget:StrinovaMap}}")
        assertContains(widget.select("script").joinToString { it.data() }, "MediaWiki:维里昂裂隙Map.json")
        assertEquals(RIFT_WIDGET_PAGE, extractionGuideLinks.single { "测试页" in it.label }.article)

        val map = assertNotNull(MapDetailParsers.parseMapWikitext(RIFT_MAP, captured("120781.txt"), captured("120781.html")))
        assertEquals(listOf("概览", "难度与准入", "八大主区域", "楼层结构", "钥匙卡与钥匙房"), map.chapters.map { it.title })
        assertEquals(listOf(0, 11, 8, 4, 10), map.chapters.map { c -> c.blocks.count { it.kind == Kind.RECORD } })
        assertTrue(map.chapters.last().blocks.any { "石室（隐藏区域）" in it.content.text })
        val overview = GameModeParsers.parseModeWikitext(mode, captured("120798.txt"), emptyList(), captured("120798.html"))
        assertEquals(listOf("等级成长系统", "角色天赋", "任务系统", "局内事件"), overview.chapters.map { it.title })
        assertEquals(listOf(112, 18, 54, 18), overview.chapters.map { c -> c.blocks.count { it.kind == Kind.RECORD } })
        assertTrue(overview.chapters.last().blocks.any { "危峰奖励（未开放）：" in it.content.text && "基点奖励：不出现" in it.content.text })
        assertEquals(listOf(RIFT_MAP), overview.maps)
    }
}
