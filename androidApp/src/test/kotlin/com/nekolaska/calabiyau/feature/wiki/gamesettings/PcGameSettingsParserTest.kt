package com.nekolaska.calabiyau.feature.wiki.gamesettings

import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsBlock
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsView
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.filteredSections
import com.nekolaska.calabiyau.feature.wiki.gamesettings.parser.PcGameSettingsParser
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PcGameSettingsParserTest {
    private fun fixture() = checkNotNull(javaClass.getResource("/fixtures/gamesettings/pc_settings_variants.html"))
        .readText(Charsets.UTF_8)

    @Test fun semanticHeadersKeepDefaultsAdviceEffectsAndOptionsIndependent() {
        val page = PcGameSettingsParser.parse(fixture()).page
        val display = page.sections.single { it.title == "画面" }.blocks.filterIsInstance<PcSettingsBlock.Setting>().single()
        assertEquals("无边框", display.field("默认"))
        assertEquals("全屏 / 无边框", display.field("可选值"))
        assertEquals("与硬件有关", display.field("帧数影响"))
        assertNull(display.field("推荐"))
        val sound = page.sections.single { it.title == "声音" }.blocks.filterIsInstance<PcSettingsBlock.Setting>().single()
        assertEquals("100", sound.field("默认"))
        assertEquals("按设备调整", sound.field("推荐"))
        assertFalse(sound.isQuickReference)
        val basic = page.settings.single { it.name == "玩家居中" }
        assertEquals("开启：玩家为中心。\n关闭：地图为中心。", basic.field("说明"))
        assertEquals("按住跳跃", page.settings.first { it.name == "跳跃" }.field("可选值或说明"))
        val merged = page.settings.single { it.name == "地图边界" }
        assertEquals("未单列说明与选项", merged.field("说明"))
        assertEquals("未单列说明与选项", merged.field("可选值"))
        assertEquals("关", merged.field("默认"))
    }

    @Test fun rowspanRecommendationsPreserveCategoryAndParagraphsWithoutInventingDefaults() {
        val recommendations = PcGameSettingsParser.parse(fixture()).page.settings.filter { it.isQuickReference }
        assertEquals(listOf("显示", "显示", "图像质量"), recommendations.map { it.recommendationCategory })
        assertEquals("• DX11：全屏\n• DX12：无边框", recommendations.first().field("推荐"))
        assertEquals("按启动模式选择。\n先检查显示器。", recommendations.first().field("说明"))
        assertTrue(recommendations.all { it.field("默认") == null })
    }

    @Test fun reorderedHeadersAndRepeatedNamesKeepTheirOwnContextAndKeys() {
        val page = PcGameSettingsParser.parse(fixture()).page
        val jumps = page.settings.filter { it.name == "跳跃" }
        assertEquals(listOf("空格", "A"), jumps.map { it.field("默认") })
        assertNotEquals(jumps[0].id, jumps[1].id)
        assertEquals(listOf("移动与身法", "手柄键位"), jumps.map { it.subsection })
        assertEquals(page.settings.size, page.settings.map { it.id }.distinct().size)
        val duplicate = Jsoup.parse(fixture())
        val table = duplicate.select("table.klbqtable")[2]
        table.selectFirst("tbody")!!.appendChild(table.select("tr")[1].clone())
        val repeated = PcGameSettingsParser.parse(duplicate.html()).page.settings.filter { it.name == "跳跃" }
        assertEquals(3, repeated.map { it.id }.distinct().size)
    }

    @Test fun explanatoryParagraphsNestedListsAndUnavailableFeatureLimitsSurvive() {
        val page = PcGameSettingsParser.parse(fixture()).page
        assertFalse(page.sections.any { it.title == "目录" })
        val startup = page.sections.single { it.title == "启动模式" }.blocks.filterIsInstance<PcSettingsBlock.Text>()
        assertEquals(listOf(0, 1, 2, 2), startup.map { it.listDepth })
        assertEquals("切换方式：", startup[1].text)
        assertEquals(1, startup.count { it.text.contains("Wegame") })
        val knowledge = page.filteredSections(PcSettingsView.KNOWLEDGE, query = "没有此功能")
        assertEquals(2, knowledge.sumOf { it.blocks.size })
        assertTrue(knowledge.flatMap { it.blocks }.all { it is PcSettingsBlock.Text && it.text.contains("仅作科普") })
    }

    @Test fun localSearchIncludesContextAndValuesWithoutMergingQuickReference() {
        val page = PcGameSettingsParser.parse(fixture()).page
        val result = page.filteredSections(PcSettingsView.SETTINGS, query = "手柄 跳跃")
        assertEquals(1, result.size)
        assertEquals("A", (result.single().blocks.single() as PcSettingsBlock.Setting).field("默认"))
        assertEquals(1, page.filteredSections(PcSettingsView.RECOMMENDATIONS, query = "198").sumOf { it.blocks.size })
        assertTrue(page.filteredSections(PcSettingsView.SETTINGS, query = "198").isEmpty())
        val sourceId = page.settings.single { it.name == "主音量" }.id
        assertEquals(sourceId, page.filteredSections(PcSettingsView.SETTINGS, query = "按设备调整").single().blocks.single().id)
    }

    @Test fun incompletePageMissingCellsAndDanglingSpansAreNotCacheable() {
        val partial = PcGameSettingsParser.parse(fixture())
        assertFalse(partial.canCache)
        assertTrue(partial.page.settings.isNotEmpty())
        assertTrue(partial.issues.any { it.contains("至少 31 行") })
        val missingCell = Jsoup.parse(fixture())
        missingCell.select("table.klbqtable")[1].select("td").last()!!.remove()
        assertTrue(PcGameSettingsParser.parse(missingCell.html()).issues.any { it.contains("字段缺失") })
        val dangling = Jsoup.parse(fixture())
        dangling.selectFirst("td[rowspan]")!!.attr("rowspan", "100")
        assertTrue(PcGameSettingsParser.parse(dangling.html()).issues.any { it.contains("跨行单元格") })
        assertFalse(PcGameSettingsParser.parse("<html><body>Access denied</body></html>").canCache)
    }

    @Test fun missingWholeSemanticColumnIsDiagnosedEvenWhenEveryRowStillAligns() {
        val document = Jsoup.parse(fixture())
        document.select("table.klbqtable")[3].select("tr").forEach { it.child(2).remove() }
        val result = PcGameSettingsParser.parse(document.html())
        assertTrue(result.issues.any { it.contains("画面/显示：章节表头字段不完整") })
    }

    @Test fun expandedTechnologyHeadingAndReferenceTableStayInKnowledgeNotSettings() {
        val html = checkNotNull(javaClass.getResource("/fixtures/gamesettings/upscaling_reference.html")).readText(Charsets.UTF_8)
        val result = PcGameSettingsParser.parse(html)
        val section = result.page.sections.single()
        assertEquals(PcSettingsView.KNOWLEDGE, section.view)
        assertEquals("DLSS、FSR、XESS是什么", section.title)
        assertTrue(result.page.settings.isEmpty())
        val rows = section.blocks.filterIsInstance<PcSettingsBlock.Reference>()
        assertEquals(4, rows.size)
        assertEquals("质量档位 (命名)", rows.first().nameLabel)
        assertEquals("DLAA / 超高质量", rows.first().name)
        assertEquals(listOf("100% (DLAA)", "~77%"), rows.first().fields.map { it.value })
        assertTrue(result.issues.none { "DLSS / XeSS 科普" in it || "语义表头" in it || "没有设置名" in it })
        assertEquals(rows.last().id, result.page.filteredSections(PcSettingsView.KNOWLEDGE, query = "43.5%")
            .single().blocks.single().id)
        assertTrue(section.blocks.filterIsInstance<PcSettingsBlock.Text>().any { "目前仅支持" in it.text })
        val broken = Jsoup.parse(html).apply { select("tr").last()!!.children().last()!!.remove() }.html()
        assertTrue(PcGameSettingsParser.parse(broken).issues.any { "字段缺失" in it })
    }
}
