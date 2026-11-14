package com.nekolaska.calabiyau.feature.wiki.decoration.parser

import com.nekolaska.calabiyau.feature.wiki.decoration.api.DecorationAssembler
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogs
import java.io.File
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Optional full HTML captures; this audit performs no network requests. */
class PlayerDecorationRealPageAuditTest {
    @Test
    fun allFiveNamecardsMatchTheNovemberCapture() {
        audit("个性名片", (34000001..34000005).toList(), listOf(
            "序列证", "弦辉名笺", "肥肥鲨的见面礼", "妄破之证", "入梦星音"
        ))
    }

    @Test
    fun allFourteenLoungeFiguresMatchTheNovemberCapture() {
        audit("休息室手办", (70001..70014).toList(), listOf(
            "星绘-星佑手办", "米雪儿-喵喵搜查官手办", "香奈美-夜莺手办", "奥黛丽-追耀手办",
            "艾卡-高校制服手办", "珐格兰丝-寻芳手办", "伊薇特-绒绒熊手办", "忧雾-弦能拘束手办",
            "明-叛逆手办", "千代-御天织手办", "汐-邦邦代打手办", "喵萌元气", "绯莎-潜行手办", "肥肥鲨绯绯莎"
        ))
    }

    private fun audit(pageName: String, expectedIds: List<Int>, expectedNames: List<String>) {
        val directory = System.getenv("PLAYER_DECORATION_AUDIT_DIR")?.let(::File)
        assumeTrue("Set PLAYER_DECORATION_AUDIT_DIR to offline HTML captures", directory?.isDirectory == true)
        val file = File(requireNotNull(directory), "$pageName.html")
        assertTrue(file.isFile, "Missing full capture: $file")
        val html = file.readText(Charsets.UTF_8)
        val sourceCards = Jsoup.parse(html).select(".mw-parser-output .player-decoration .gallerygrid-item")
        assertEquals(expectedIds.size, sourceCards.size, "$pageName source card count changed")

        val raw = DecorationHtmlParser.parse(html)
        val sections = DecorationAssembler.assemble(raw, DecorationCatalogs.forPage(pageName))
        val items = sections.flatMap { it.items }
        assertEquals(expectedIds, items.map { it.id }, "$pageName missing/reordered IDs")
        assertEquals(expectedNames, items.map { it.name })
        assertEquals(items.size, items.map { it.id }.distinct().size)
        assertTrue(DecorationAssembler.unresolvedImageFiles(raw).isEmpty(), "Original HTML URLs should suffice")

        sourceCards.zip(items).forEach { (card, item) ->
            val badge = requireNotNull(card.selectFirst("[data-quality]"))
            val description = requireNotNull(card.selectFirst(".klbq-decoration-card__subtitle")).text()
            val source = card.select(".klbq-decoration-card__obtain-value li").joinToString("、") { it.text() }
            assertEquals(badge.attr("data-quality").toInt(), item.quality, "${item.id}: quality")
            assertEquals(description, item.description, "${item.id}: description")
            assertEquals(source, item.source, "${item.id}: acquisition")
            assertTrue(description.isNotBlank() && source.isNotBlank(), "${item.id}: source text missing")
            assertEquals(card.selectFirst(".gallerygrid-imagebox img")?.attr("src"), item.imageUrl)
            assertTrue(item.imageUrl.startsWith("https://patchwiki.biligame.com/") && "/thumb/" !in item.imageUrl)
            assertEquals(item.imageUrl, item.iconUrl)
        }
        println("$pageName: ${items.size} complete cards; IDs, text, quality and original images match capture")
    }
}
