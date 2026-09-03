package com.nekolaska.calabiyau.feature.wiki.item.parser

import com.nekolaska.calabiyau.feature.wiki.item.model.Quality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItemCatalogParsersTest {

    @Test
    fun parsesNamedRowsAndSkipsHeader() {
        val items = ItemCatalogParsers.parseItems(
            """
            <table id="CardSelectTr">
              <tr><th>名称</th><th>品质</th><th>简介</th></tr>
              <tr class="divsort" data-param1="消耗品" data-param2="4">
                <td>
                  <a class="image"><img src="https://patchwiki.biligame.com/images/klbq/thumb/a/ab/coin.png/70px-coin.png"/></a>
                  <b>金币</b>
                </td>
                <td><span class="quality-badge" data-quality="4"><span style="display: none">4</span>完美</span></td>
                <td>通用货币</td>
              </tr>
              <tr class="divsort" data-param1="">
                <td>无名</td>
                <td>精致</td>
                <td>无粗体名称应跳过</td>
              </tr>
            </table>
            """.trimIndent()
        )

        assertEquals(1, items.size)
        assertEquals("金币", items.single().name)
        assertEquals("消耗品", items.single().category)
        assertEquals(Quality.PERFECT, items.single().quality)
        assertEquals("完美", items.single().qualityName)
        assertEquals("通用货币", items.single().description)
        assertEquals(
            "https://patchwiki.biligame.com/images/klbq/a/ab/coin.png",
            items.single().iconUrl
        )
    }

    @Test
    fun emptyTableReturnsEmpty() {
        assertTrue(ItemCatalogParsers.parseItems("<table id='CardSelectTr'></table>").isEmpty())
    }
}
