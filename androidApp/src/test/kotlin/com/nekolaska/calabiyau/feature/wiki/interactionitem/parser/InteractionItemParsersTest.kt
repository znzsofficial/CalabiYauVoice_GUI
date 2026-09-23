package com.nekolaska.calabiyau.feature.wiki.interactionitem.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InteractionItemParsersTest {

    @Test
    fun parsesRowsAndSkipsHeaderAndShortRows() {
        val html = """
        <div class="mw-parser-output">
          <h2><span class="mw-headline" id="互动道具列表">互动道具列表</span></h2>
          <table class="klbqtable">
            <tr><th>名称</th><th>品质</th><th>介绍</th><th>获得方式</th></tr>
            <tr>
              <th><img src="https://patchwiki.biligame.com/images/klbq/thumb/a/ab/icon.png/60px-icon.png" />增压信标</th>
              <td><span class="quality-badge" data-quality="4">完美</span></td>
              <td>放置后为周围队友提供增益。</td>
              <td>商城购买</td>
            </tr>
            <tr>
              <td>侦察无人机</td>
              <td><span class="quality-badge" data-quality="2">精致</span></td>
              <td>标记视野内的敌人。</td>
              <td>活动兑换</td>
              <td>多余列</td>
            </tr>
            <tr><td>只有一列</td></tr>
          </table>
        </div>
        """.trimIndent()

        val items = InteractionItemParsers.parseItems(html)

        assertEquals(2, items.size)
        assertEquals("增压信标", items[0].name)
        assertNotNull(items[0].quality)
        assertEquals("完美", items[0].qualityName)
        assertEquals("放置后为周围队友提供增益。", items[0].description)
        assertEquals("商城购买", items[0].obtainMethod)
        // 缩略图还原为原图
        assertEquals(
            "https://patchwiki.biligame.com/images/klbq/a/ab/icon.png",
            items[0].iconUrl
        )
        assertEquals("侦察无人机", items[1].name)
        assertEquals("活动兑换", items[1].obtainMethod)
    }

    @Test
    fun fallsBackToAnyKlbqTableWhenHeadingMissing() {
        val html = """
        <table class="klbqtable">
          <tr><th>名称</th><th>品质</th><th>介绍</th><th>获得方式</th></tr>
          <tr>
            <td>护盾发生器</td>
            <td><span class="quality-badge" data-quality="5">传说</span></td>
            <td>生成临时护盾。</td>
            <td>赛季奖励</td>
          </tr>
        </table>
        """.trimIndent()

        val items = InteractionItemParsers.parseItems(html)
        assertEquals(1, items.size)
        assertEquals("护盾发生器", items.single().name)
        assertEquals("传说", items.single().qualityName)
    }

    @Test
    fun emptyHtmlReturnsEmptyList() {
        assertTrue(InteractionItemParsers.parseItems("<div></div>").isEmpty())
    }
}
