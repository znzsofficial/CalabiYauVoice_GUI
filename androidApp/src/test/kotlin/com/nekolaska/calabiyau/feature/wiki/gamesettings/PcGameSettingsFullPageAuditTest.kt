package com.nekolaska.calabiyau.feature.wiki.gamesettings

import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.gamesettings.api.PcGameSettingsApi
import com.nekolaska.calabiyau.feature.wiki.gamesettings.model.PcSettingsBlock
import com.nekolaska.calabiyau.feature.wiki.gamesettings.parser.PcGameSettingsParser
import com.nekolaska.calabiyau.feature.wiki.gamesettings.source.PcGameSettingsRemoteSource
import data.ApiResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PcGameSettingsFullPageAuditTest {
    @Test fun revision120159PreservesAllRowsAndRejectsStructuralTruncation() {
        val file = System.getenv("PC_GAME_SETTINGS_AUDIT_FILE")?.let(::File)
        assumeTrue("Set PC_GAME_SETTINGS_AUDIT_FILE to the local revision 120159 full HTML capture", file?.isFile == true)
        val html = file!!.readText(Charsets.UTF_8)
        val parsed = PcGameSettingsParser.parse(html)
        assertTrue(parsed.canCache, parsed.issues.joinToString("\n"))
        assertEquals(10, parsed.page.sections.size)
        assertEquals(33, parsed.tableCount)
        assertEquals(323, parsed.page.settings.size)
        assertEquals(31, parsed.page.settings.count { it.isQuickReference })
        assertEquals(323, parsed.page.settings.map { it.id }.distinct().size)
        val knowledge = parsed.page.sections.single { it.title == "DLSS、XESS是什么" }.blocks.filterIsInstance<PcSettingsBlock.Text>()
        assertEquals(2, knowledge.count { it.text.contains("没有此功能仅作科普") })
        val cached = assertIs<ApiResult.Success<*>>(PcGameSettingsApi.parseSource(WikiHtmlPageSourceResult(html, true, 12_345L)))
        assertTrue(cached.isOffline)
        assertEquals(12_345L, cached.cacheAgeMs)
        assertTrue(PcGameSettingsRemoteSource.isValidPayload(payload(html)))
        val changes: List<(org.jsoup.nodes.Document) -> Unit> = listOf(
            { it.select("table.klbqtable").last()!!.remove() },
            { it.select("table.klbqtable")[5].select("tr").last()!!.remove() },
            { it.selectFirst("td[rowspan]")!!.attr("rowspan", "600") },
            { it.select("table.klbqtable")[3].select("tr")[1].child(1).remove() },
            { it.select(".mw-parser-output h2").last()!!.remove() },
            { it.select("table.klbqtable")[17].select("tr").forEach { row -> row.child(2).remove() } },
            { it.select(".mw-parser-output li").first { node -> node.text().contains("帧生成（Frame Generation）") }.remove() }
        )
        changes.forEachIndexed { index, damage ->
            val damaged = Jsoup.parse(html).also(damage).html()
            assertFalse(PcGameSettingsParser.parse(damaged).canCache, "Damage $index accepted")
            assertFalse(PcGameSettingsRemoteSource.isValidPayload(payload(damaged)), "Payload damage $index accepted")
            assertIs<ApiResult.Error>(PcGameSettingsApi.parseSource(WikiHtmlPageSourceResult(damaged, true, 1L)))
        }
    }

    private fun payload(html: String) = buildJsonObject {
        putJsonObject("parse") { put("title", "游戏设置PC端"); putJsonObject("text") { put("*", html) } }
    }.toString()
}
