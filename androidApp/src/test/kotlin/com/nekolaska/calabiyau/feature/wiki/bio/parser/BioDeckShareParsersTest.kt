package com.nekolaska.calabiyau.feature.wiki.bio.parser

import data.SharedJson
import com.nekolaska.calabiyau.feature.wiki.bio.model.DeckCardOption
import java.util.Base64
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BioDeckShareParsersTest {

    @Test
    fun parsesFactionCardsAndSkipsNameless() {
        val cards = BioDeckShareParsers.parseFactionCards(
            SharedJson.parseToJsonElement(
                """
                [
                  {"name":"火力全开","cardid":"c1","quality":"4","default":"true","index":"2"},
                  {"name":"  ","cardid":"skip"},
                  {"name":"护盾","cardid":"c2","quality":"2","default":"false"}
                ]
                """.trimIndent()
            ).jsonArray
        )

        assertEquals(listOf("火力全开", "护盾"), cards.map { it.name })
        assertTrue(cards[0].isDefault)
        assertEquals(2, cards[0].index)
        assertFalse(cards[1].isDefault)
        assertEquals(-1, cards[1].index)
    }

    /** 真实页面（2026-09-21 抓取）的分享串格式：`提示语｜卡组名｜分享码**` */
    @Test
    fun parsesRealPageShareInput() {
        val raw = "谁还没玩过这套？复制代码后点击创建卡组即可生成｜人类核爆｜MDAwQnwwMDAyfDEwQUYxNEEyMDg0MEI4MEE2RkQ1NDNFMUU=**"
        assertEquals("人类核爆", BioDeckShareCodecs.extractDeckNameFromShareInput(raw))
        assertEquals("MDAwQnwwMDAyfDEwQUYxNEEyMDg0MEI4MEE2RkQ1NDNFMUU=", BioDeckShareCodecs.extractActualShareCode(raw))
        // 纯码粘贴（无分隔符）原样返回
        val bare = "MDAwQnwwMDAyfDEwQUYxNEEyMDg0MEI4MEE2RkQ1NDNFMUU="
        assertEquals(bare, BioDeckShareCodecs.extractActualShareCode(bare))
    }

    /** 真实页面分享ID单元格的实际形态：完整串/纯码/垃圾文本/空白/按钮脚本 */
    @Test
    fun parsesDeckTablesAcrossShareIdVariants() {
        val html = """
        <div class="mw-parser-output">
        <h2><span class="mw-headline">超弦体卡组</span></h2>
        <table class="klbqtable">
          <tr><th colspan="2"><center><b>(喵喵喵)人类核爆</b></center></th></tr>
          <tr><th>分享作者</th><td>繁华云梦</td></tr>
          <tr><th>分享ID</th><td><pre>谁还没玩过这套？复制代码后点击创建卡组即可生成｜人类核爆｜MDAwQnwwMDAyfDEwQUYxNEEyMDg0MEI4MEE2RkQ1NDNFMUU=**</pre></td></tr>
        </table>
        <table class="klbqtable">
          <tr><th colspan="2"><center><b>纯码卡组</b></center></th></tr>
          <tr><th>分享ID</th><td><pre>MDAwQnwwMDAzfDYwMzA5QzI4Njg3ODIxMkYwOERFMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA=**</pre></td></tr>
        </table>
        <table class="klbqtable">
          <tr><th colspan="2"><center><b>垃圾文本卡组</b></center></th></tr>
          <tr><th>分享ID</th><td><pre>看不懂</pre></td></tr>
        </table>
        <table class="klbqtable">
          <tr><th colspan="2"><center><b>空白卡组</b></center></th></tr>
          <tr><th>分享ID</th><td></td></tr>
        </table>
        <table class="klbqtable">
          <tr><th colspan="2"><center><b>按钮卡组</b></center></th></tr>
          <tr><th>分享ID</th><td><script>(function() { var cardIdsParam = "10251008,10251009"; })();</script></td></tr>
        </table>
        </div>
        """.trimIndent()

        val decks = BioCardParsers.parseDecks(html, emptyMap())
        val byTitle = decks.associateBy { it.title }
        assertEquals(
            "MDAwQnwwMDAyfDEwQUYxNEEyMDg0MEI4MEE2RkQ1NDNFMUU=**",
            byTitle.getValue("(喵喵喵)人类核爆").shareId
        )
        // 纯码（缺提示语和卡组名）仍能取到码
        assertTrue(byTitle.getValue("纯码卡组").shareId.startsWith("MDAwQnww"), byTitle.getValue("纯码卡组").shareId)
        // 垃圾文本与空白不得产出假分享码
        assertEquals("", byTitle.getValue("垃圾文本卡组").shareId)
        assertEquals("", byTitle.getValue("空白卡组").shareId)
        // 按钮脚本形态：无静态码，回落到空（动态重建走 extractShareIdFromDynamicScript）
        assertEquals("", byTitle.getValue("按钮卡组").shareId)
    }

    @Test
    fun missingOrInvalidStaticShareCodeStaysEmptyEvenWhenDeckCardsAreKnown() {
        val payloadGarbage = Base64.getEncoder().encodeToString("not-deck-payload".toByteArray())
        for (cell in listOf("", "ThisIsNotADeckCode", payloadGarbage, "复制分享码")) {
            assertEquals("", parseShareCell(cell).shareId)
        }
        // A missing share-ID row is also valid content, not a request to manufacture a code.
        val html = """<div class="mw-parser-output"><table class="klbqtable">
            <tr><th colspan="2">无分享码卡组</th></tr>
            <tr><th>卡牌</th><td><span class="card-name">已知卡牌</span></td></tr>
            </table></div>"""
        val deck = BioCardParsers.parseDecks(html, fakeIndexes).single()
        assertEquals("", deck.shareId)
        assertEquals(listOf("已知卡牌"), deck.cardNames)
    }

    @Test
    fun staticShareUsesTheSameProtocolValidationAndPaddingAsImport() {
        val code = BioDeckShareCodecs.encodeShareCode(listOf(1, 4), "超弦体")
        assertEquals(code, parseShareCell("提示｜示例｜${code.removeSuffix("**").trimEnd('=')}**").shareId)
        for (payload in listOf("000A|0002|1", "000B|0009|1", "000B|0002|-1", "000B|0002|1|extra")) {
            val invalid = Base64.getEncoder().encodeToString(payload.toByteArray())
            assertEquals("", parseShareCell(invalid).shareId)
        }
    }

    @Test
    fun dynamicShareReconstructsOnlyExplicitFullyKnownIds() {
        val deck = parseShareCell("""<script>var cardIdsParam = "a,b";</script>""")
        assertEquals(BioDeckShareCodecs.encodeShareCode(listOf(1, 4), "超弦体"), deck.shareId)

        // Pick the explicitly supplied faction's map, not the surrounding heading's map.
        val crystal = parseShareCell("""<script>const cardIdsParam = 'c'; let factionParam = '晶源体';</script>""")
        assertEquals(BioDeckShareCodecs.encodeShareCode(listOf(8), "晶源体"), crystal.shareId)
        for (ids in listOf("", "unknown", "a,unknown", "a,", ",a", "a,,b")) {
            assertEquals("", parseShareCell("""<script>var cardIdsParam = "$ids";</script>""").shareId, ids)
        }
        assertEquals("", parseShareCell("""<script>var cardIdsParam = "a"; var factionParam = "晶源体";</script>""").shareId)
        assertEquals("", parseShareCell("""<script>var factionParam = "超弦体";</script>""").shareId)
        assertEquals("", parseShareCell("""<script>var cardIdsParam = "a";</script>""", emptyMap()).shareId)
        assertEquals("", parseShareCell("""<script>var cardIdsParam = "a";</script>""", mapOf("超弦体" to mapOf("a" to -1))).shareId)
        assertEquals("", parseShareCell("""<script>var cardIdsParam = "a,b";</script>""", mapOf("超弦体" to mapOf("a" to 1, "b" to 1))).shareId)
    }

    @Test
    fun dynamicParametersAreNotCombinedFromUnrelatedScriptElements() {
        val deck = parseShareCell("""<script>var cardIdsParam = "c";</script><script>var factionParam = "晶源体";</script>""")
        assertEquals("", deck.shareId)
    }

    @Test
    fun submissionSelectionDoesNotDropUnknownIdsOrCollidingIndexes() {
        val a = DeckCardOption("甲", "a", "精致", false, 1)
        val b = DeckCardOption("乙", "b", "精致", false, 2)
        assertEquals(listOf(a, b), BioDeckShareParsers.resolveSelectedCards(listOf("a", "b", "a"), listOf(a, b)))
        assertFailsWith<IllegalArgumentException> { BioDeckShareParsers.resolveSelectedCards(emptyList(), listOf(a)) }
        assertFailsWith<IllegalArgumentException> { BioDeckShareParsers.resolveSelectedCards(listOf("a", "missing"), listOf(a)) }
        assertFailsWith<IllegalArgumentException> { BioDeckShareParsers.resolveSelectedCards(listOf("a", "b"), listOf(a, b.copy(index = 1))) }
    }

    private val fakeIndexes = mapOf("超弦体" to mapOf("a" to 1, "b" to 4), "晶源体" to mapOf("c" to 8))

    private fun parseShareCell(cell: String, indexes: Map<String, Map<String, Int>> = fakeIndexes) =
        BioCardParsers.parseDecks(
            """<div class="mw-parser-output"><h2><span class="mw-headline">超弦体卡组</span></h2>
            <table class="klbqtable"><tr><th colspan="2">测试卡组</th></tr>
            <tr><th>分享ID</th><td>$cell</td></tr>
            <tr><th>卡牌</th><td><span class="card-name">已知卡牌</span></td></tr>
            </table></div>""", indexes
        ).single()
}
