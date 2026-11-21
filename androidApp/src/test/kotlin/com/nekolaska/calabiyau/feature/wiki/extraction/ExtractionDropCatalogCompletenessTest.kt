package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.cache.fetchValidatedCachePayload
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionCatalogKind
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionCatalogParsers
import com.nekolaska.calabiyau.feature.wiki.extraction.source.ExtractionCatalogRemoteSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

class ExtractionDropCatalogCompletenessTest {
    private val kind = ExtractionCatalogKind.DROPS
    private fun payload(html: String) = buildJsonObject { putJsonObject("parse") { putJsonObject("text") { put("*", html) } } }.toString()
    private fun valid(payload: String) = ExtractionCatalogRemoteSource.isValidPayload(payload, kind)
    private fun parse(html: String) = ExtractionCatalogParsers.parseResult(html, kind)
    private val mapPage = """<div class="mw-parser-output"><h2>地图场景容器（2 个）</h2>
        <p>物品清单相同的容器归为一组，组下方是该组的完整物品清单；各容器的概率并不相同。</p>
        <div class="sec-drop-card"><span class="sec-drop-card__title">弹药仓</span><div class="sec-drop-card__meta-item"><b class="sec-drop-card__meta-label">掉落池规模</b><span class="sec-drop-card__meta-val">2 项</span></div></div>
        <div class="sec-drop-card"><span class="sec-drop-card__title">标准装备箱</span></div>
        <div class="sec-drop-pool"><div class="sec-drop-pool__head">共用物品清单 · 弹药仓、标准装备箱<b>2 项</b></div>
        <div class="sec-drop-pool__q"><span class="sec-drop-pool__tag">Q1 优秀</span><span class="sec-drop-pool__cnt">2 项</span></div>
        <div class="sec-drop-pool__chips"><span class="sec-drop-chip">轻型弹药</span><span class="sec-drop-chip">重型弹药</span></div></div></div>"""

    @Test fun structuralLossNeverPublishesEvenWhenTheRemainingPoolIsInternallyConsistent() = runBlocking {
        val good = payload(mapPage)
        assertTrue(valid(good))
        val fallback = OfflineCache.CacheResult(good, true, 1800L)
        val damaged = listOf(
            Jsoup.parse(mapPage).apply { select(".sec-drop-pool").remove() },
            Jsoup.parse(mapPage).apply { selectFirst(".sec-drop-pool")!!.replaceWith(Element("strong").addClass("error").text("模板展开失败")) },
            Jsoup.parse(mapPage).apply { selectFirst(".sec-drop-card")!!.remove() },
            Jsoup.parse(mapPage).apply { selectFirst(".sec-drop-chip")!!.remove(); select(".sec-drop-pool__cnt, .sec-drop-pool__head b").forEach { it.text("1 项") } },
            Jsoup.parse(mapPage).apply { selectFirst(".sec-drop-card__title")!!.text("标准装备箱") },
            Jsoup.parse(mapPage).apply { selectFirst(".sec-drop-pool__head")!!.text("物品清单 · 标准装备箱").appendElement("b").text("2 项") },
            Jsoup.parse(mapPage).apply { selectFirst(".sec-drop-card")!!.appendElement("span").addClass("scribunto-error").text("Lua 错误") }
        )
        for ((index, doc) in damaged.withIndex()) {
            val fresh = payload(doc.outerHtml())
            val read = fetchValidatedCachePayload(online = true, network = { fresh }, read = { fallback },
                publish = { fail("published damaged drop catalog $index") }, validate = ::valid)
            assertEquals(fallback, read)
        }
    }

    @Test fun aSourceSizeWithoutAnExplicitPageListPromiseDoesNotInventAMissingPool() {
        val original = """<h2>首领（Boss）</h2><div class="sec-drop-card"><div class="sec-drop-card__title">莉莉丝 ／ KONG</div>
            <div class="sec-drop-card__meta-item"><span class="sec-drop-card__meta-label">掉落池规模</span><span class="sec-drop-card__meta-val">166 项</span></div></div>
            <h2>据点箱与空投</h2><p>空投的逐项掉落尚未解析，这里只给件数。</p><div class="sec-drop-card"><div class="sec-drop-card__title">空投</div></div>"""
        val result = parse(original)
        assertTrue(result.canCache, result.issues.toString())
        assertTrue(result.entries.all { it.sharedPoolId == null })
        // On older pages the source's count can refer to weighted entries, not the number of distinct item chips.
        val legacy = mapPage.replace("共用物品清单 ·", "共用掉落池 ·").replace("meta-val\">2 项", "meta-val\">25 项")
            .replace("物品清单相同的容器归为一组，组下方是该组的完整物品清单", "共用同一掉落池的容器归为一组，组下方即该组的完整掉落池")
        assertTrue(valid(payload(legacy)))
    }

    @Test fun realNovember21CatalogRejectsLossOfEachPromisedPoolAndTheReviewMutations() {
        val dir = System.getenv("WIKI_NOV21_AUDIT_DIR")?.let(::File)
        assumeTrue("Set WIKI_NOV21_AUDIT_DIR to the November 21 captures", dir != null)
        val original = Jsoup.parse(File(dir!!, "drops.html").readText(Charsets.UTF_8))
        val pools = original.select(".sec-drop-pool")
        assertTrue(pools.isNotEmpty())
        // No error node or broken group left to detect: remove each whole pool independently.
        for (index in pools.indices) {
            val copy = original.clone()
            copy.select(".sec-drop-pool")[index].remove()
            assertFalse(valid(payload(copy.outerHtml())), "published without ${pools[index].selectFirst(".sec-drop-pool__head")!!.text()}")
        }
        val lostCard = original.clone().apply { select(".sec-drop-card").single { it.selectFirst(".sec-drop-card__title")!!.text() == "弹药仓" }.remove() }
        assertFalse(valid(payload(lostCard.outerHtml())))
        val smaller = original.clone().apply {
            val pool = getElementById("sec-cpool-1")!!
            pool.selectFirst(".sec-drop-chip")!!.remove()
            val groupCount = pool.selectFirst(".sec-drop-pool__cnt")!!
            groupCount.text("${groupCount.text().substringBefore(' ').toInt() - 1} 项")
            pool.selectFirst(".sec-drop-pool__head b")!!.text("485 项")
        }
        val result = parse(smaller.outerHtml())
        assertTrue(result.issues.any { "486 项" in it && "485" in it }, result.issues.toString())
        assertFalse(valid(payload(smaller.outerHtml())))
    }
}
