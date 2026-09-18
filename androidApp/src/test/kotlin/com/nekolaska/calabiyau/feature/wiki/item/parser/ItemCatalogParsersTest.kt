package com.nekolaska.calabiyau.feature.wiki.item.parser

import com.nekolaska.calabiyau.feature.wiki.item.model.Quality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItemCatalogParsersTest {

    @Test
    fun parsesKlbqItemCards() {
        val items = ItemCatalogParsers.parseItems(
            """
            <div class="gallerygrid">
              <div class="gallerygrid-item klbq-item-card" data-param1="货币" data-param2="3">
                <div class="klbq-item-card__imagebox"><a href="/klbq/理想币" title="理想币"><img alt="道具图标 2.png" src="https://patchwiki.biligame.com/images/klbq/thumb/2/23/coin.png/120px-coin.png"/></a></div>
                <div class="klbq-item-card__captionbox">
                  <div class="klbq-item-card__name"><a href="/klbq/理想币">理想币</a></div>
                  <div class="klbq-item-card__desc">常驻货币，可山角色和部分道具兑换。</div>
                </div>
              </div>
              <div class="gallerygrid-item klbq-item-card" data-param1="" data-param2="9">
                <div class="klbq-item-card__name">无名卡</div>
              </div>
            </div>
            """.trimIndent()
        )

        assertEquals(2, items.size)
        assertEquals("理想币", items[0].name)
        assertEquals("货币", items[0].category)
        assertEquals(Quality.SUPERIOR, items[0].quality)
        assertEquals("卓越", items[0].qualityName)
        assertEquals("常驻货币，可山角色和部分道具兑换。", items[0].description)
        assertEquals(
            "https://patchwiki.biligame.com/images/klbq/2/23/coin.png",
            items[0].iconUrl
        )
        // 无品质字段的卡片仍应收录：分类回退"其他"，品质与文案为空
        assertEquals("无名卡", items[1].name)
        assertEquals("其他", items[1].category)
        assertEquals(null, items[1].quality)
        assertEquals("", items[1].qualityName)
        assertEquals("", items[1].description)
    }

    @Test
    fun duplicateCardsAreDeduplicated() {
        val card = """
            <div class="gallerygrid-item klbq-item-card" data-param1="礼盒礼包" data-param2="4">
              <div class="klbq-item-card__imagebox"><img src="https://patchwiki.biligame.com/images/klbq/e/ed/card.png"/></div>
              <div class="klbq-item-card__name">武器外观自选体验卡</div>
              <div class="klbq-item-card__desc">自选一款武器外观。</div>
            </div>
        """.trimIndent()
        val items = ItemCatalogParsers.parseItems("<div class=\"gallerygrid\">$card$card</div>")

        // 页面重复卡不得产生重复行（曾导致 LazyColumn key 冲突崩溃）
        assertEquals(1, items.size)
    }

    @Test
    fun newCardStructureTakesPrecedenceOverLegacyTable() {
        val items = ItemCatalogParsers.parseItems(
            """
            <div class="gallerygrid-item klbq-item-card" data-param1="货币" data-param2="3">
              <div class="klbq-item-card__name">新结构道具</div>
            </div>
            <table id="CardSelectTr">
              <tr class="divsort" data-param1="消耗品" data-param2="4">
                <td><b>旧结构道具</b></td><td>完美</td><td>应被忽略</td>
              </tr>
            </table>
            """.trimIndent()
        )

        assertEquals(listOf("新结构道具"), items.map { it.name })
    }

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
