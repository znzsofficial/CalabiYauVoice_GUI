package com.nekolaska.calabiyau.feature.wiki.extraction.equipment

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.api.EquipmentApi
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.parser.EquipmentFormatException
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.parser.EquipmentParser
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.source.EquipmentRemoteSource
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class ExtractionEquipmentTest {
    // Two grades from each gallery are sufficient to exercise the two different grouping contracts.
    // No production check depends on November's 180/16 counts or requires six grades per suit.
    private fun page(kind: EquipmentKind): String {
        val gun = kind == EquipmentKind.GUNS
        val category = if (gun) "狙击枪" else "适应性超弦装甲"
        val first = if (gun) 4004030 else 4074011
        fun card(code: Int, id: Int, amount: Int) = """
            <div class="gallerygrid-item sec-card" data-param1="$category" data-sort-id="$id" data-quality="$code" data-param2="$code"
              data-sort-default="${if (gun) 1 else id}" data-sort-value="1000" data-sort-time="300" data-sort-rate="0.001" data-sort-weight="4500"
              ${if (gun) "data-sort-dmg='$amount'" else "data-sort-armor='$amount' data-sort-hp='0'"}>
              <div class="sec-card-name">${if (gun) "伏闪" else category}</div><div class="sec-card-value">价值 1,000</div>
              <div class="sec-card-img">${if (code == 8) "<img src='//patchwiki.biligame.com/test.png'>" else "<a class='new'>未上传</a>"}</div>
              <div class="sec-card-desc"><span class="sec-card-title">描述</span>第 $code 档<br>装置</div>
              <div class="sec-card-stats">${if (gun) "<span>伤害 $amount</span><span>射速 39</span><span>弹匣 5 发</span><span>后坐力控制 100</span><span>散射控制 100</span><span>操控速度 38</span>" else "<span>护甲 $amount</span><span>占格 3×2</span><span>重量 4,500</span>"}</div>
              <div class="sec-card-loot">出现率 0.100% · 常见于 <b>来源$code</b></div>
              <div class="sec-card-tags"><span class="sec-card-tag sec-qtag" data-q="$code">${if (code == 8) "臻藏" else "传说"}</span>
                <span class="sec-card-tag">${if (gun) category else "标准防具"}</span><span class="sec-card-tag">${if (gun) "狙击弹药" else "防具"}</span>
                ${if (gun) "<span class='sec-card-tag' data-tag='用途'>售价 500 · 搜刮 0.30s</span>" else ""}</div>
            </div>"""
        return """<li class="cardSelectOption" data-group="1" data-option="1|$category">$category</li>
            <li class="cardSelectOption" data-group="2" data-option="2|8">臻藏</li><li class="cardSelectOption" data-group="2" data-option="2|5">传说</li>
            <p>共 <b>2</b> ${kind.countUnit}</p><div class="gallerygrid ${kind.gallery}">${card(8, first, 155)}${card(5, first + 3, 175)}</div>"""
    }

    private fun envelope(kind: EquipmentKind, html: String) = buildJsonObject {
        putJsonObject("parse") { put("title", kind.pageName); putJsonObject("text") { put("*", html) } }
    }.toString()

    @Test fun variantOwnsItsArtworkSourceStatsAndSearchMatch() {
        val kind = EquipmentKind.GUNS
        val html = page(kind)
        val page = EquipmentParser.parse(kind, html)
        val variants = page.series.single().variants
        assertEquals(listOf("Q8 臻藏", "Q5 传说"), variants.map { it.quality.label })
        assertEquals("38", (variants.first().stats as EquipmentStats.Gun).handling)
        assertEquals("https://patchwiki.biligame.com/test.png", variants.first().imageUrl)
        val result = page.rows("来源5", "狙击枪", null, EquipmentSort.DAMAGE).single().variant
        assertEquals(variants.last(), result); assertEquals("175", result.attributes["伤害"])
        assertNull(result.imageUrl); assertEquals("第 5 档\n装置", result.description)
        assertTrue(page.rows("来源5", null, 8, EquipmentSort.DEFAULT).isEmpty())
        val distinct = html.replaceFirst("data-sort-default=\"1\"", "data-sort-default=\"2\"")
        assertEquals(2, EquipmentParser.parse(kind, distinct).series.size, "A matching name alone cannot merge different gun groups")
    }

    @Test fun armorUsesExplicitSuitZeroHealthAndOriginalDimensions() {
        val result = EquipmentParser.parse(EquipmentKind.ARMOR, page(EquipmentKind.ARMOR))
        val suit = result.series.single()
        assertEquals("适应性超弦装甲", suit.category)
        val selected = result.rows("", null, 8, EquipmentSort.HP).single().variant
        val stats = selected.stats as EquipmentStats.Armor
        assertEquals("155", stats.armor); assertEquals("0", stats.health); assertNull(stats.pveDefense)
        assertEquals("3×2", stats.size); assertEquals("4,500", stats.weight)
        assertTrue(result.rows("", null, 1, EquipmentSort.ARMOR).isEmpty())
    }

    @Test fun roundedWikiMoneyRefreshesOldCacheAndKeepsExactValue() = runBlocking {
        for (kind in EquipmentKind.entries) {
            val old = page(kind)
            val cached = OfflineCache.CacheResult(envelope(kind, old), true, 1234)
            for ((raw, caption) in listOf(19844L to "2万", 33600L to "3.4万", 5330812L to "533万", 199990000L to "2亿")) {
                val fresh = envelope(kind, old.replace("data-sort-value=\"1000\"", "data-sort-value=\"$raw\"").replace("价值 1,000", "价值 $caption"))
                for (previous in listOf(null, cached)) {
                    var stored: String? = null
                    val result = EquipmentRemoteSource.validatedFetch(true, false, { EquipmentRemoteSource.decode(kind, it) },
                        network = { fresh }, read = { previous }, publish = { stored = it })
                    assertEquals(fresh, stored)
                    val model = EquipmentRemoteSource.decode(kind, assertNotNull(result).payload)
                    val row = model.rows(raw.toString(), null, 8, EquipmentSort.VALUE).single().variant
                    assertEquals(raw, row.numericValue); assertEquals(caption, row.value)
                }
            }
            val bad = envelope(kind, old.replace("data-sort-value=\"1000\"", "data-sort-value=\"19844\"").replace("价值 1,000", "价值 2.1万"))
            assertEquals(cached, EquipmentRemoteSource.validatedFetch(true, false, { EquipmentRemoteSource.decode(kind, it) },
                network = { bad }, read = { cached }, publish = { fail("Published a contradictory price") }))
        }
    }

    @Test fun missingMixedAndTruncatedPagesNeverReplaceEitherValidatedCache() = runBlocking {
        assertNotEquals(EquipmentKind.GUNS.cacheKey, EquipmentKind.ARMOR.cacheKey)
        for (kind in EquipmentKind.entries) {
            val html = page(kind)
            val other = EquipmentKind.entries.single { it != kind }
            val good = OfflineCache.CacheResult(envelope(kind, html), true, 1234)
            val bad = listOf("{\"error\":{\"code\":\"missingtitle\"}}", envelope(other, html), envelope(kind, page(other)),
                envelope(kind, html + page(other)), envelope(kind, html.replace("<b>2</b>", "<b>3</b>")),
                envelope(kind, html.substringBeforeLast("</div>")), envelope(kind, html.replaceFirst("data-q=\"8\"", "data-q=\"5\"")),
                envelope(kind, html.replaceFirst(if (kind == EquipmentKind.GUNS) "操控速度 38" else "占格 3×2", "")))
            for (fresh in bad) {
                val cached = EquipmentRemoteSource.validatedFetch(true, false, { EquipmentRemoteSource.decode(kind, it) },
                    network = { fresh }, read = { good }, publish = { fail("Overwrote $kind cache") })
                assertEquals(good, cached)
            }
            val rejected = EquipmentApi.load(kind) {
                EquipmentRemoteSource.validatedFetch(true, false, { EquipmentRemoteSource.decode(kind, it) },
                    network = { bad.last() }, read = { null }, publish = { fail("Published damaged page") })
                null
            }
            assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(rejected).kind)
            assertNull(EquipmentRemoteSource.validatedFetch(false, false, { EquipmentRemoteSource.decode(kind, it) },
                network = { fail("Offline fetch reached network") }, read = { null }, publish = { fail() }))
            assertFailsWith<EquipmentFormatException> { EquipmentRemoteSource.decode(kind, bad.first()) }
        }
    }
}
