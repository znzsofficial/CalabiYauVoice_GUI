package com.nekolaska.calabiyau.feature.wiki.mechanics

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.cache.fetchValidatedCachePayload
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.mechanics.model.*
import com.nekolaska.calabiyau.feature.wiki.mechanics.parser.MechanicsParser
import com.nekolaska.calabiyau.feature.wiki.mechanics.source.MechanicsRemoteSource
import com.nekolaska.calabiyau.feature.wiki.mechanics.api.MechanicsApi
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

class MechanicsTest {
    // Synthetic complete structure; actual full captures are audited separately below.
    private fun stringify() = """
        <div class="mw-parser-output"><div class="klbq-breadcrumb-bar">忽略面包屑</div>
        <p>弦化系统</p><ul><li>减伤33%<ul><li>加算公式</li><li>独立乘区<span class="smw-highlighter" title="20%"><span class="smwttcontent">20%</span></span></li></ul></li><li>机动性</li></ul>
        ${listOf("纸片人", "爬墙", "飘飞", "官方教学视频").mapIndexed { index, title -> """
        <h2>$title</h2><p>说明 <a href="#历史更新改动">历史</a></p>
        <script>const source = 'https://bili-proxy.biligame.com/www/blackboard/newplayer.html?bvid=BV1Ug4y1w7NJ&p=${index + 1}&autoplay=0';
        const mobile = 'https://www.bilibili.com/blackboard/html5mobileplayer.html?bvid=BV1Ug4y1w7NJ&p=${index + 1}&autoplay';</script>""" }.joinToString("")}
        <h2>历史更新改动</h2><div class="alert alert-warning">2025年3月25日更新</div>
        <ul>${(1..6).joinToString("") { "<li>历史改动$it</li>" }}</ul></div>
    """.trimIndent()

    private fun augmentation(): String = buildString {
        append("<div class='mw-parser-output'><h2>系统简介</h2><p>升级系统</p><ul><li>完整网络</li><li>枪械网络</li></ul>")
        append("<p><a class='image' href='/klbq/文件:网络.png'><img alt='网络.png' src='https://example.com/thumb/a.png'></a></p>")
        append("<h2>升级项目</h2><ul>")
        repeat(7) { append("<li>升级规则$it</li>") }; append("</ul><h2>能量点获得详情</h2>")
        listOf("爆破模式" to 15, "极限推进模式" to 7, "弦区争夺模式" to 6, "超弦爆破模式" to 13).forEach { (name, count) ->
            append("<h3>$name</h3><table><tr><th>项目</th>")
            append(when (name) { "爆破模式" -> "<th>一般爆破模式</th><th>排位赛模式</th>"; "超弦爆破模式" -> "<th>获得点数</th>"; else -> "<th>增加点数</th>" })
            append("</tr>")
            repeat(count) { append("<tr><td>项目$it</td><td>180</td>${if (name == "爆破模式") "<td>250</td>" else ""}</tr>") }
            append("</table>")
        }
        append("<h4>爆破模式对局补充</h4><ul>")
        repeat(8) { append("<li>补充$it</li>") }; append("</ul><h2>更新改动历史</h2><ul>")
        repeat(8) { append("<li>历史$it</li>") }; append("</ul></div>")
    }

    private fun roles(): String = buildString {
        append("<div class='mw-parser-output'><p>六种定位</p>")
        listOf("突击" to 4, "决斗" to 4, "守护" to 4, "支援" to 3, "先锋" to 4, "控场" to 5).forEach { (role, count) ->
            append("<h2><span class='mw-headline'>$role<style>.role{color:red}</style></span></h2><ul><li>定位职责：职责</li><li>定位角色：</li></ul><div class='nav-chara'>")
            repeat(count) { append("<div><a href='/klbq/${role}$it' title='${role}$it'><img src='https://example.com/$it.png'></a><a href='/klbq/${role}$it'>显示名$it</a></div>") }
            append("</div>")
        }
        append("<h2>更新改动历史</h2><ul><li>调整</li></ul><dl><dd>详情<a class='image' href='/klbq/文件:更新.png'><img alt='更新.png' src='https://example.com/update.png'></a></dd></dl>")
        append("<table class='navbox'><tr><td>导航不可混入正文</td></tr></table><div class='leftPic'><img src='https://example.com/mascot.png'></div><div class='resourceLoader'>MediaWiki:LineCapture.js</div></div>")
    }

    private fun payload(kind: MechanicsKind, html: String) = buildJsonObject {
        putJsonObject("parse") { put("title", kind.pageName); putJsonObject("text") { put("*", html) } }
    }.toString()

    @Test fun readsIntroNestedListsNotesAndDeduplicatesDesktopMobileVideoEmbeds() {
        val parsed = MechanicsParser.parse(MechanicsKind.STRINGIFY, stringify())
        assertTrue(parsed.canCache, parsed.issues.toString())
        assertEquals(6, parsed.page.sections.size)
        val blocks = parsed.page.sections.flatMap { it.blocks }
        assertEquals(4, blocks.filterIsInstance<MechanicsBlock.Video>().size)
        assertEquals(2, blocks.filterIsInstance<MechanicsBlock.Text>().count { it.depth == 2 })
        assertTrue(blocks.filterIsInstance<MechanicsBlock.Text>().any { it.content.text == "独立乘区\n注：20%" })
        val link = blocks.filterIsInstance<MechanicsBlock.Text>().flatMap { it.content.spans }.first { it.url != null }.url!!
        assertEquals("https://wiki.biligame.com/klbq/弦化#历史更新改动", java.net.URI(link).toString().let { java.net.URLDecoder.decode(it, "UTF-8") })
        assertEquals(blocks.size, blocks.map { it.id }.distinct().size)
        assertFalse(blocks.filterIsInstance<MechanicsBlock.Text>().any { it.content.text.contains("const source") })
    }

    @Test fun rejectsMissingVideoAndPartialText() {
        val document = Jsoup.parse(stringify())
        document.select("script")[1].remove()
        assertFalse(MechanicsParser.parse(MechanicsKind.STRINGIFY, document.html()).canCache)
        document.selectFirst("li")!!.remove()
        assertFalse(MechanicsParser.parse(MechanicsKind.STRINGIFY, document.html()).canCache)
    }

    @Test fun modeTableColumnsStaySemanticAcrossReordering() {
        val document = Jsoup.parse(augmentation())
        document.select("table").first()!!.select("tr").forEach { row -> row.appendChild(row.child(1)) }
        val parsed = MechanicsParser.parse(MechanicsKind.AUGMENTATION, document.html())
        assertTrue(parsed.canCache, parsed.issues.toString())
        val records = parsed.page.sections.flatMap { it.blocks }.filterIsInstance<MechanicsBlock.Record>()
        assertEquals(41, records.size)
        assertEquals("180", records.first().fields.single { it.label == "一般爆破模式" }.value.text)
        assertEquals("250", records.first().fields.single { it.label == "排位赛模式" }.value.text)
        val image = parsed.page.sections.flatMap { it.blocks }.filterIsInstance<MechanicsBlock.Image>().single()
        assertEquals("https://example.com/thumb/a.png", image.thumbnail)
        assertTrue(image.fileUrl!!.contains("/klbq/"))
    }

    @Test fun tableTruncationWrongHeadersAndUnsupportedSpansAreRejected() {
        listOf<(org.jsoup.nodes.Document) -> Unit>(
            { it.select("table").last()!!.remove() },
            { it.select("table").first()!!.select("tr").last()!!.remove() },
            { it.selectFirst("td")!!.attr("rowspan", "999") },
            { it.select("th")[1].text("其他模式") },
            { it.selectFirst("td")!!.empty() }
        ).forEach { change ->
            val broken = Jsoup.parse(augmentation()).also(change).html()
            assertFalse(MechanicsParser.parse(MechanicsKind.AUGMENTATION, broken).canCache)
        }
    }

    @Test fun characterIdentityComesFromLinkNotDisplayAliasAndMissingImagesKeepCards() {
        val parsed = MechanicsParser.parse(MechanicsKind.ROLES, roles())
        assertTrue(parsed.canCache, parsed.issues.toString())
        val characters = parsed.page.sections.flatMap { it.blocks }.filterIsInstance<MechanicsBlock.Character>()
        assertEquals(24, characters.size)
        assertEquals("突击0", characters.first().name)
        assertEquals("显示名0", characters.first().label)
        assertEquals(1, parsed.page.sections.flatMap { it.blocks }.filterIsInstance<MechanicsBlock.Image>().size)
        val withoutImages = Jsoup.parse(roles()).apply { select(".nav-chara img").remove() }
        assertTrue(MechanicsParser.parse(MechanicsKind.ROLES, withoutImages.html()).canCache)
        val missing = Jsoup.parse(roles()).apply { selectFirst(".nav-chara > div")!!.remove() }
        assertFalse(MechanicsParser.parse(MechanicsKind.ROLES, missing.html()).canCache)
        val duplicate = Jsoup.parse(roles()).apply { select(".nav-chara")[1].select("a").take(2).forEach { it.attr("href", "/klbq/突击0") } }
        assertFalse(MechanicsParser.parse(MechanicsKind.ROLES, duplicate.html()).canCache)
        val external = Jsoup.parse(roles()).apply { select(".nav-chara a").take(2).forEach { it.attr("href", "https://example.com/klbq/突击0") } }
        assertFalse(MechanicsParser.parse(MechanicsKind.ROLES, external.html()).canCache)
    }

    @Test fun freshInvalidDataNeverReplacesGoodCacheAndIsParseErrorWithoutFallback() = runBlocking {
        val kind = MechanicsKind.AUGMENTATION
        val good = payload(kind, augmentation())
        var disk = good
        val broken = payload(kind, "<div class='mw-parser-output'>空</div>")
        val loaded = MechanicsApi.loadSource(kind) {
            MechanicsRemoteSource.fetchValidated(kind) { validate ->
                fetchValidatedCachePayload(true, { broken }, { OfflineCache.CacheResult(disk, true, 55L) }, { disk = it }, validate)
            }
        }
        assertTrue(assertIs<ApiResult.Success<*>>(loaded).isOffline)
        assertEquals(55L, loaded.cacheAgeMs)
        assertEquals(good, disk)
        val failed = MechanicsApi.loadSource(kind) {
            MechanicsRemoteSource.fetchValidated(kind) { validate ->
                fetchValidatedCachePayload(true, { broken }, { null }, { error("Bad write") }, validate)
            }
        }
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(failed).kind)
        assertNotNull(MechanicsRemoteSource.validationIssue(MechanicsKind.STRINGIFY, good))
        assertEquals(ErrorKind.NETWORK, assertIs<ApiResult.Error>(MechanicsApi.parseSource(kind, null)).kind)
    }

    @Test fun cancellationPropagates(): Unit = runBlocking {
        assertFailsWith<CancellationException> { MechanicsApi.loadSource(MechanicsKind.STRINGIFY) { throw CancellationException() } }
    }

    @Test fun fullThreePageCapturesPreserveCurrentStructureAndKeySourceValues() {
        val dir = System.getenv("MECHANICS_AUDIT_DIR")?.let(::File)
        assumeTrue("Set MECHANICS_AUDIT_DIR to the three real HTML captures", dir?.isDirectory == true)
        val captures = mapOf(MechanicsKind.STRINGIFY to "stringify", MechanicsKind.ROLES to "roles", MechanicsKind.AUGMENTATION to "augmentation")
        captures.forEach { (kind, name) ->
            val html = File(dir!!, "$name.html").readText(Charsets.UTF_8)
            val result = MechanicsParser.parse(kind, html)
            assertTrue(result.canCache, "$kind: ${result.issues}")
            assertNull(MechanicsRemoteSource.validationIssue(kind, payload(kind, html)))
            val blocks = result.page.sections.flatMap { it.blocks }
            assertEquals(blocks.size, blocks.map { it.id }.distinct().size)
            when (kind) {
                MechanicsKind.STRINGIFY -> {
                    assertEquals(4, blocks.filterIsInstance<MechanicsBlock.Video>().size)
                    assertEquals(listOf("1", "2", "3", "1"), blocks.filterIsInstance<MechanicsBlock.Video>().map { it.url.substringAfter("?p=") })
                    assertTrue(blocks.filterIsInstance<MechanicsBlock.Text>().any { it.content.text.contains("减伤对近战武器、焚焰者无效") })
                }
                MechanicsKind.ROLES -> {
                    assertEquals(24, blocks.filterIsInstance<MechanicsBlock.Character>().size)
                    assertEquals("奥黛丽", blocks.filterIsInstance<MechanicsBlock.Character>().single { it.name == "奥黛丽·格罗夫" }.label)
                    assertEquals(2, blocks.filterIsInstance<MechanicsBlock.Image>().size)
                }
                MechanicsKind.AUGMENTATION -> {
                    assertEquals(41, blocks.filterIsInstance<MechanicsBlock.Record>().size)
                    assertEquals(4, blocks.filterIsInstance<MechanicsBlock.Image>().size)
                    assertTrue(blocks.filterIsInstance<MechanicsBlock.Record>().any { it.title.text == "时间" && it.fields.single().value.text == "每5秒增加25" })
                }
            }
            val offline = assertIs<ApiResult.Success<*>>(MechanicsApi.parseSource(kind, WikiHtmlPageSourceResult(html, true, 123L)))
            assertEquals(123L, offline.cacheAgeMs)
        }
    }
}
