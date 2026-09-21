package com.nekolaska.calabiyau.feature.wiki.bio.parser

import data.SharedJson
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            "MDAwQnwwMDAyfDEwQUYxNEEyMDg0MEI4MEE2RkQ1NDNFMUU=",
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
}
