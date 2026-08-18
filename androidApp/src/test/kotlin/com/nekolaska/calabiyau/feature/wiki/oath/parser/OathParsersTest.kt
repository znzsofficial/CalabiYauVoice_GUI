package com.nekolaska.calabiyau.feature.wiki.oath.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OathParsersTest {

    @Test
    fun assignsTablesByPosition() {
        val page = OathParsers.parseHtml(
            """
            <div class="mw-parser-output">
              <h2><span id="简介" class="mw-headline">简介</span></h2>
              <p>第一段</p>
              <p>第二段</p>
              <table>
                <tr><th>等级</th><th>名称</th><th>所需</th><th>累计</th></tr>
                <tr><td>1</td><td>相识</td><td>100</td><td>100</td></tr>
              </table>
              <table>
                <tr><th>礼物</th><th>角色</th><th>描述</th><th>效果</th></tr>
                <tr><td>蛋糕</td><td>米雪儿·李</td><td>生日蛋糕</td><td>+10</td></tr>
              </table>
              <table>
                <tr><th>名称</th><th>描述</th><th>稀有度</th><th>米雪儿·李</th><th>奥黛丽</th></tr>
                <tr>
                  <td><img src="https://x/thumb/a/ab/flower.png/40px-flower.png"/>花</td>
                  <td>一朵花</td>
                  <td>4 精良</td>
                  <td>10</td>
                  <td>5</td>
                </tr>
              </table>
              <table>
                <tr><th>名称</th><th>描述</th><th>稀有度</th><th>米雪儿·李</th></tr>
                <tr><td>限时花</td><td>活动</td><td>3 优秀</td><td>8</td></tr>
              </table>
              <table>
                <tr><th>名称</th><th>描述</th></tr>
                <tr><td>信物</td><td>羁绊信物</td></tr>
              </table>
            </div>
            """.trimIndent()
        )

        assertEquals("第一段\n第二段", page.summary)
        assertEquals(listOf("1"), page.levels.map { it.level })
        assertEquals("相识", page.levels.single().name)
        assertEquals("蛋糕", page.birthdayGifts.single().name)
        assertEquals("米雪儿·李", page.birthdayGifts.single().character)

        assertEquals(listOf("常驻礼物", "活动礼物"), page.favorGifts.map { it.source })
        val favor = page.favorGifts[0]
        assertEquals("花", favor.name)
        assertEquals("精良", favor.rarity)
        assertEquals(mapOf("米雪儿·李" to "10", "奥黛丽" to "5"), favor.favorByCharacter)
        assertEquals("https://x/a/ab/flower.png", favor.imageUrl)
        assertEquals("优秀", page.favorGifts[1].rarity)

        assertEquals("米雪儿·李", page.bondSections.single().character)
        assertEquals("信物", page.bondSections.single().items.single().name)
    }

    @Test
    fun missingFirstTableShiftsLaterTables() {
        val page = OathParsers.parseHtml(
            """
            <div class="mw-parser-output">
              <table>
                <tr><th>礼物</th><th>角色</th><th>描述</th><th>效果</th></tr>
                <tr><td>蛋糕</td><td>米雪儿·李</td><td>生日蛋糕</td><td>+10</td></tr>
              </table>
            </div>
            """.trimIndent()
        )

        assertEquals("蛋糕", page.levels.single().level)
        assertTrue(page.birthdayGifts.isEmpty())
        assertTrue(page.favorGifts.isEmpty())
    }
}
