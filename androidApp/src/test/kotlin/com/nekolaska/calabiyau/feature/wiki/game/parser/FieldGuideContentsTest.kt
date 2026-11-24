package com.nekolaska.calabiyau.feature.wiki.game.parser

import com.nekolaska.calabiyau.feature.wiki.game.endReadingBlock
import com.nekolaska.calabiyau.feature.wiki.game.model.FieldGuideBlock.Kind
import com.nekolaska.calabiyau.feature.wiki.game.model.sectionAt
import com.nekolaska.calabiyau.feature.wiki.game.model.sections
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

class FieldGuideContentsTest {
    private val pageUrl = "https://wiki.biligame.com/klbq/战斗模式/弦溯战线"

    private fun table(caption: String, name: String) = """<table class="klbqtable"><caption>$caption</caption>
        <tr><th>任务名</th></tr><tr><td>$name</td></tr></table>"""

    @Test fun captionsCoverOnlyTheirOwnTableAndDoNotBecomeParentsOfLaterHeadings() {
        val chapter = parseFieldGuide(Jsoup.parse("""<div class="mw-parser-output">
            <h2><span class="mw-headline">任务系统</span></h2><p>导语</p>
            <h3><span class="mw-headline">战斗线</span></h3>
            ${table("主链任务", "接触威胁·初次确认")}
            ${table("支链任务（完成战斗-4 后解锁）", "门阀残响·首战确认")}
            <dl><dt>推进规则：</dt><dd>说明</dd></dl>
            <h5><span class="mw-headline">奖励</span></h5>${table("奖励", "安全箱卡")}
            <h3><span class="mw-headline">撤离线</span></h3>
            <h4><span class="mw-headline">奖励</span></h4><dl><dt>芯元：</dt></dl>
            <table class="navbox"><caption>站点导航</caption></table></div>"""), setOf("任务系统"), pageUrl).single()
        val sections = chapter.sections()
        assertEquals(listOf("战斗线", "主链任务", "支链任务（完成战斗-4 后解锁）", "奖励", "撤离线", "奖励"), sections.map { it.label })
        assertEquals(listOf(0, 1, 1, 1, 0, 1), sections.map { it.depth })
        assertNull(sections.sectionAt(0)) // No section selected in the introduction.
        fun at(text: String) = sections.sectionAt(chapter.blocks.indexOfFirst { it.content.text == text }.also { assertTrue(it >= 0) })
        assertEquals(sections[1], at("任务名：接触威胁·初次确认"))
        assertEquals(sections[2], at("任务名：门阀残响·首战确认"))
        assertEquals(sections[0], at("推进规则：")) // Left the branch table, still in 战斗线.
        assertEquals(sections[3], at("任务名：安全箱卡")) // A caption repeating its heading is not a second entry.
        assertEquals(sections[5], at("芯元：")) // Same label in a different group has its own target.
        sections.forEach { section ->
            assertEquals(section.label, chapter.blocks[section.blocks.first].content.text)
            assertEquals(section, sections.sectionAt(section.blocks.first))
        }
    }

    @Test fun chapterLevelTableIsNavigableButEmptyAndUnsupportedTablesAreNot() {
        val chapter = parseFieldGuide(Jsoup.parse("""<div class="mw-parser-output"><h2><span class="mw-headline">局内事件</span></h2>
            ${table("事件奖励", "配给券")}<p>表外说明</p>
            <table class="wikitable"><caption>未列任务</caption><tr><th>名称</th></tr></table>
            <table class="wikitable"><caption>另见原表</caption><tr><th colspan="2">奖励</th></tr></table>
            </div>"""), setOf("局内事件"), pageUrl).single()
        val section = chapter.sections().single()
        assertEquals("事件奖励", section.label)
        assertEquals(0, section.depth)
        assertEquals(0..1, section.blocks)
        assertNull(chapter.sections().sectionAt(2))
        assertTrue(chapter.blocks.any { it.content.text == "在 Wiki 查看此表" })
    }

    @Test fun restoredEndPositionDistinguishesTheSelectedSectionFromAnOrdinaryScrollToTheBottom() {
        val visible = 10..15
        assertEquals(12, endReadingBlock(visible, atStart = false, selected = 12))
        assertEquals(15, endReadingBlock(visible, atStart = false, selected = -1))
        assertEquals(15, endReadingBlock(visible, atStart = false, selected = 7))
        // A whole chapter can fit on a tablet: opening at its introduction and selecting a child differ.
        assertEquals(0, endReadingBlock(0..5, atStart = true, selected = -1))
        assertEquals(4, endReadingBlock(0..5, atStart = true, selected = 4))
    }

    @Test fun revision120798BattleLineOwnsBothChainsAndGrowthKeepsMaterialsInTheBody() {
        val path = System.getenv("WIKI_MAP_MODE_AUDIT_DIR")
        assumeTrue("Set WIKI_MAP_MODE_AUDIT_DIR to the captured 120798 revision", !path.isNullOrBlank())
        val document = Jsoup.parse(File(requireNotNull(path), "120798.html").readText(Charsets.UTF_8))
        val chapters = parseFieldGuide(document, setOf("等级成长系统", "任务系统"), pageUrl).associateBy { it.title }
        val tasks = chapters.getValue("任务系统")
        val outline = tasks.sections()
        val battle = outline.single { it.label == "战斗线（12 个）" }
        val chains = outline.filter { it.depth == battle.depth + 1 && it.blocks.first in battle.blocks }
        assertEquals(listOf("主链任务", "支链任务（完成战斗-4 后解锁）"), chains.map { it.label })
        assertEquals(listOf(10, 2), chains.map { chain -> chain.blocks.count { tasks.blocks[it].kind == Kind.RECORD } })
        assertContains(tasks.blocks[chains.last().blocks.first + 1].content.text, "任务名：门阀残响·首战确认")
        assertTrue(battle.blocks.last < outline.single { it.label == "撤离线（9 个）" }.blocks.first)
        val growth = chapters.getValue("等级成长系统")
        for (field in listOf("联合币：", "芯元：", "谐定阵列：")) {
            val block = growth.blocks.indexOfFirst { it.content.text == field }
            assertTrue(block >= 0)
            assertEquals("等级奖励", growth.sections().sectionAt(block)?.label)
        }
    }
}
