package com.nekolaska.calabiyau.feature.wiki.decoration.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jsoup.Jsoup

class DecorationHtmlCompletenessTest {
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/fixtures/decoration/$name.html")).readText(Charsets.UTF_8)

    @Test fun declaredMissingMainArtworkKeepsEveryIdentityWithoutAnyImg() {
        val result = DecorationHtmlParser.parseResult(fixture("missing-main-artwork"))
        assertTrue(result.isComplete, result.issues.joinToString())
        assertEquals(4, result.cardCount)
        assertEquals(4, result.parsedCardCount)
        val items = result.sections.flatMap { it.items }
        assertEquals(listOf(33300231, 93001, 93002, 33500294), items.map { it.id })
        assertEquals(listOf("荣誉之王", "弦流识域", "旧版房间", "八面玲珑"), items.map { it.name })
        assertTrue(items.all { it.image.originalUrl.isEmpty() })
        assertEquals("荣誉成就S级奖励", items.first().source)
        assertEquals(result.sections, DecorationHtmlParser.parse(fixture("missing-main-artwork")))
    }

    @Test fun oneBadCardRejectsAnOtherwiseNonemptyDirectory() {
        val doc = Jsoup.parse(fixture("namecards"))
        doc.select(".gallerygrid-item").last()!!.select(".gallerygrid-imagebox").empty()
        val result = DecorationHtmlParser.parseResult(doc.outerHtml())
        assertFalse(result.isComplete)
        assertEquals(2, result.cardCount)
        assertEquals(1, result.parsedCardCount)
        assertEquals(1, result.sections.single().items.size)
        assertTrue(result.issues.any { it.contains("卡片[2]") })
    }

    @Test fun duplicateCardsAreCountedBeforeGroupDeduplication() {
        val doc = Jsoup.parse(fixture("namecards"))
        val card = doc.selectFirst(".gallerygrid-item")!!
        card.after(card.outerHtml())
        val result = DecorationHtmlParser.parseResult(doc.outerHtml())
        assertTrue(result.isComplete, result.issues.joinToString())
        assertEquals(3, result.cardCount)
        assertEquals(3, result.parsedCardCount)
        assertEquals(2, result.sections.single().items.size)
    }

    @Test fun previewsAndAcquisitionLinksCannotSupplyMissingMainIdentity() {
        for (body in listOf(
            "<div class='gallerygrid-imagebox'></div><div class='wiki-lemma-content-data'><a title='文件:动作_123.gif'>预览</a><img alt='动作_123.gif'></div>",
            "<div class='wiki-lemma-content-data'><div class='thumb'><a title='文件:动作_123.gif'>预览</a><img alt='动作_123.gif'></div></div>",
            "<a title='文件:无关说明_123.png'>无主图容器的外部说明</a>",
            "<div class='gallerygrid-imagebox'><a title='角色:装饰_123.png'>无效</a></div><ul><li><a title='文件:装饰_123.png'>获取说明</a></li></ul>",
            "<div class='gallerygrid-imagebox'><a title='文件:没有编号.png'>无身份</a></div>",
            "<div class='gallerygrid-imagebox'><a title='文件:装饰_0.png'>零编号</a></div>",
            "<div class='gallerygrid-imagebox'><a title='文件:装饰_123.png'>一</a><a title='文件:装饰_456.png'>二</a></div>"
        )) {
            val html = "<div class='mw-parser-output'><div class='gallerygrid-item'>$body<span data-quality='3'>装饰</span></div></div>"
            val result = DecorationHtmlParser.parseResult(html)
            assertFalse(result.isComplete, body)
            assertEquals(0, result.parsedCardCount)
        }
    }

    @Test fun filenamesAloneAreNotMeaningfulCatalogContentAndTemplateErrorsAreDiagnosed() {
        val doc = Jsoup.parse(fixture("namecards"))
        doc.select(".gallerygrid-player-decoration").remove()
        assertFalse(DecorationHtmlParser.parseResult(doc.outerHtml()).isComplete)
        assertFalse(DecorationHtmlParser.parseResult(fixture("namecards").replace(
            "</div>", "<span class='scribunto-error'>模块错误</span></div>"
        )).isComplete)
    }
}
