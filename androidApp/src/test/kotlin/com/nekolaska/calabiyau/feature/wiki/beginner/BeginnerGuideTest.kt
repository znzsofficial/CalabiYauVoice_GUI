package com.nekolaska.calabiyau.feature.wiki.beginner

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.cache.fetchValidatedCachePayload
import com.nekolaska.calabiyau.feature.wiki.beginner.api.BeginnerGuideApi
import com.nekolaska.calabiyau.feature.wiki.beginner.model.*
import com.nekolaska.calabiyau.feature.wiki.beginner.parser.BeginnerGuideParser
import com.nekolaska.calabiyau.feature.wiki.beginner.source.BeginnerGuideRemoteSource
import com.nekolaska.calabiyau.feature.wiki.hub.WikiRoute
import com.nekolaska.calabiyau.feature.wiki.hub.decodeRoute
import com.nekolaska.calabiyau.feature.wiki.hub.encode
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assume.assumeTrue
import java.io.File
import java.net.URI
import kotlin.test.*

class BeginnerGuideTest {
    private val html = javaClass.getResource("/fixtures/beginner/guide.html")!!.readText()
    private val expanded = javaClass.getResource("/fixtures/beginner/guide-120494.html")!!.readText()
    // Verified 120474 delta: the four chapters and TOC stayed; only the notice title disappeared.
    private val historical by lazy { html.replace("<span style=\"font-weight:bold;\">先弄清楚这两件事</span><br>", "") }
    private fun payload(content: String, version2: Boolean = false, revision: Long? = null) = buildJsonObject {
        putJsonObject("parse") {
            put("title", BEGINNER_GUIDE_PAGE)
            revision?.let { put("revid", it) }
            if (version2) put("text", content) else putJsonObject("text") { put("*", content) }
        }
    }.toString()

    @Test fun preservesSourceSectionsTablesEmphasisNestedListsAndAllDownloadPanes() {
        val parsed = BeginnerGuideParser.parse(html)
        assertTrue(parsed.canCache, parsed.issues.joinToString())
        assertEquals(listOf("我适合入坑吗", "我该下哪个版本", "下载与安装", "配置需求"), parsed.page.sections.map { it.title })
        val all = parsed.page.sections.flatMap { it.blocks }
        assertEquals(17, all.filterIsInstance<GuideBlock.Record>().size)
        assertEquals(all.size, all.map { it.id }.distinct().size)
        val paragraphs = all.filterIsInstance<GuideBlock.Paragraph>()
        assertEquals(8, paragraphs.count { it.depth == 2 })
        assertEquals(1, paragraphs.count { it.content.text == "有朋友一起，或者能接受单排" })
        assertTrue(paragraphs.first().content.spans.any { it.bold && it.text == "PvP 射击" })
        assertTrue(paragraphs.single { it.notice }.content.text.contains("数据不互通"))
        assertFalse(paragraphs.any { it.content.text.contains('×') })
        val links = paragraphs.flatMap { it.content.spans }.mapNotNull { it.url }
        assertEquals(listOf("https://klbq.idreamsky.com/?nav=home", "https://store.steampowered.com/app/1282270/",
            "https://www.strinova.com/", "https://klbqm.idreamsky.com/?nav=home"), links)
        val system = all.filterIsInstance<GuideBlock.Record>().single { it.title.text == "操作系统" }
        assertEquals("最低配置 / 推荐配置", system.fields.single().label)
        assertEquals(1, Regex("商店页还写着").findAll(system.fields.single().value.text).count())
        assertTrue(system.fields.single().value.text.contains("实际得 Win10 以上"))
        val gpu = all.filterIsInstance<GuideBlock.Record>().single { it.title.text == "显卡" }
        assertEquals("Nvidia GeForce GT 730\nAMD R7 240", gpu.fields.first().value.text)
    }

    @Test fun rejectsPartialSectionsTablesFieldsDownloadPanesAndMalformedSpans() {
        val changes: List<(Document) -> Unit> = listOf(
            { it.select("h2").last()!!.remove() },
            { it.select("table").last()!!.remove() },
            { it.select("table")[1].select("tr").last()!!.remove() },
            { it.select("table").first()!!.select("tr")[2].child(1).remove() },
            { it.selectFirst("td[colspan]")!!.attr("colspan", "2000") },
            { it.selectFirst("td[colspan]")!!.attr("rowspan", "2000") },
            { it.select(".resp-tab-content").last()!!.remove() },
            { it.select(".resp-tab-content")[1].select("li").last()!!.remove() },
            { it.selectFirst("h3 + p + ul > li")!!.remove() },
            { it.selectFirst(".alert")!!.remove() },
            { it.selectFirst("h3")!!.remove() },
            { it.select("table")[0].select("tr").forEach { row -> row.child(2).remove() } },
            { it.selectFirst("td")!!.empty() }
        )
        changes.forEachIndexed { index, damage ->
            val broken = Jsoup.parse(html).also(damage).html()
            assertFalse(BeginnerGuideParser.parse(broken).canCache, "damage $index")
            assertNotNull(BeginnerGuideRemoteSource.validationIssue(payload(broken)), "payload $index")
            assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(BeginnerGuideApi.parseSource(OfflineCache.CacheResult(payload(broken), true, 1L))).kind)
        }
    }

    @Test fun semanticColumnsAndBoundedRowspansKeepTheirLabels() {
        val document = Jsoup.parse(html)
        val table = document.select("table").first()!!
        table.select("tr").forEach { row -> row.appendChild(row.child(1)) }
        val rows = table.select("tr")
        rows[1].child(1).attr("rowspan", "2")
        rows[2].child(1).remove()
        val parsed = BeginnerGuideParser.parse(document.html())
        assertTrue(parsed.canCache, parsed.issues.joinToString())
        val versions = parsed.page.sections[1].blocks.filterIsInstance<GuideBlock.Record>()
        assertEquals("QQ、微信", versions[1].fields.single { it.label == "登录方式" }.value.text)
        assertEquals("启动器", versions[1].fields.single { it.label == "游玩平台" }.value.text)
    }

    @Test fun allowsAdditionsAndPreservesUnknownTextWithoutDuplicateKeys() {
        val document = Jsoup.parse(html)
        document.selectFirst("h3")!!.after("<p>新增说明</p><p>新增说明</p>")
        document.select("table").first()!!.selectFirst("tbody")!!.append("<tr><th>新版本</th><td>平台</td><td>账号</td><td>说明</td></tr>")
        val parsed = BeginnerGuideParser.parse(document.html())
        assertTrue(parsed.canCache, parsed.issues.joinToString())
        val repeated = parsed.page.sections.flatMap { it.blocks }.filterIsInstance<GuideBlock.Paragraph>().filter { it.content.text == "新增说明" }
        assertEquals(2, repeated.map { it.id }.distinct().size)
    }

    @Test fun urlsRejectExecutableSchemesAndKeepReadableText() {
        listOf("javascript:alert(1)", "data:text/html,x", "file:///tmp/a", "https://user:pass@example.com/",
            "http://game.bilibili.com/linkfilter/?url=javascript%3Aalert(1)").forEach { assertNull(BeginnerGuideParser.safeUrl(it)) }
        assertEquals("https://example.com/a", BeginnerGuideParser.safeUrl("//example.com/a"))
        assertTrue(BeginnerGuideParser.safeUrl("/klbq/新人入坑指南")!!.startsWith("https://wiki.biligame.com/klbq/"))
        val text = BeginnerGuideParser.richText(Jsoup.parse("<p><a href='javascript:alert(1)'>说明</a></p>").selectFirst("p")!!)
        assertEquals("说明", text.text)
        assertNull(text.spans.single().url)
    }

    @Test fun expandedGuideKeepsAllFiveTablesListsSystemLinksAndAuthorAnswersInSourceOrder() {
        val result = BeginnerGuideParser.parse(expanded)
        assertTrue(result.canCache, result.issues.joinToString())
        val sections = result.page.sections
        assertEquals(listOf("这是一款怎样的游戏", "我适合入坑吗", "我该下哪个版本", "下载与安装", "配置需求",
            "开始游戏前需要了解什么", "游戏设置建议", "常见问题"), sections.map { it.title })
        val old = BeginnerGuideParser.parse(html).page.sections
        listOf(0, 2, 3).forEach { assertEquals(old[it], sections[it + 1]) }
        assertEquals(old[1].blocks.filterNot { it is GuideBlock.Paragraph && it.notice },
            sections[2].blocks.filterNot { it is GuideBlock.Paragraph && it.notice })
        val notice = sections[2].blocks.filterIsInstance<GuideBlock.Paragraph>().single { it.notice }.content
        assertTrue(notice.text.startsWith("腾讯服和创梦服"))
        assertEquals(listOf("数据不互通", "另一个游戏"), notice.spans.filter { it.bold }.map { it.text })
        assertEquals(listOf(0, 0, 4, 0, 13, 4, 6, 0), sections.map { it.blocks.count { block -> block is GuideBlock.Record } })
        val allKeys = sections.flatMap { it.blocks }.map { it.id }
        assertEquals(allKeys.distinct(), allKeys)

        val overview = sections.first().blocks.filterIsInstance<GuideBlock.Paragraph>()
        assertEquals(10, overview.size)
        assertEquals(listOf("FPS（第一人称射击）：", "TPS（第三人称射击）：", "弦化机制：", "超弦体技能：", "立体战场："),
            overview.filter { it.marker != null }.map { p -> p.content.spans.first { it.bold }.text })
        val systems = sections[5].blocks
        assertEquals(listOf("弦化", "超弦体", "武器装备"), systems.filterIsInstance<GuideBlock.Heading>().map { it.title })
        assertEquals("新玩家需要了解：", systems.filterIsInstance<GuideBlock.Term>().single().content.text)
        val systemParagraphs = systems.filterIsInstance<GuideBlock.Paragraph>()
        assertEquals(14, systemParagraphs.count { it.marker != null })
        val links = systemParagraphs.flatMap { it.content.spans }.filter { it.url != null }
        assertEquals(listOf("弦化", "角色筛选", "武器筛选"), links.map { it.text })
        links.forEach { assertEquals(URI("https://wiki.biligame.com/klbq/${it.text}").toASCIIString(), it.url) }

        val dom = Jsoup.parse(expanded)
        // Independent cell-for-cell oracle for both new shapes. No rowspan or colspan in either source table.
        listOf(3 to 5, 4 to 6).forEach { (tableIndex, sectionIndex) ->
            val rows = dom.select("table")[tableIndex].select("tr")
            val headers = rows.first()!!.children().eachText()
            val records = sections[sectionIndex].blocks.filterIsInstance<GuideBlock.Record>()
            assertEquals(rows.drop(1).map { it.children().eachText() }, records.map { r -> listOf(r.title.text) + r.fields.map { it.value.text } })
            records.forEach { assertEquals(headers.drop(1), it.fields.map { field -> field.label }) }
        }
        assertContains((sections[6].blocks.last() as GuideBlock.Paragraph).content.text, "没有适用于所有人的唯一最佳设置")
        val faq = sections.last().blocks
        assertEquals(16, faq.size)
        val pairs = dom.select("dl").drop(1).map { it.text() to it.nextElementSibling()!!.text() }
        assertEquals(pairs, faq.chunked(2).map { (q, a) ->
            assertIs<GuideBlock.Term>(q).content.text to assertIs<GuideBlock.Paragraph>(a).content.text
        })
    }

    @Test fun recognizesReorderedAdviceAndEquipmentColumnsAndRichDefinitionTerms() {
        val document = Jsoup.parse(expanded)
        document.select("table").takeLast(2).forEach { table -> table.select("tr").forEach { it.appendChild(it.child(0)) } }
        val question = document.select("dt").last()!!
        question.html("移动端支持<b>模拟器</b>吗？")
        val answer = question.parent()!!.nextElementSibling()!!
        question.after(answer.tagName("dd")) // MediaWiki also permits dl > dt + dd.
        val result = BeginnerGuideParser.parse(document.html())
        assertTrue(result.canCache, result.issues.joinToString())
        val sections = result.page.sections
        assertEquals(listOf("主武器", "副武器", "近战武器", "战术道具"), sections[5].blocks.filterIsInstance<GuideBlock.Record>().map { it.title.text })
        val sensitivity = sections[6].blocks.filterIsInstance<GuideBlock.Record>().first()
        assertEquals("鼠标灵敏度", sensitivity.title.text)
        assertEquals("建议检查的内容", sensitivity.fields.single().label)
        val lastPair = sections.last().blocks.takeLast(2)
        assertEquals("模拟器", assertIs<GuideBlock.Term>(lastPair.first()).content.spans.single { it.bold }.text)
        assertEquals("移动端官方不支持模拟器登录。", assertIs<GuideBlock.Paragraph>(lastPair.last()).content.text)
    }

    @Test fun requiredLinksMatchCurrentWikiArticleIdentityAndRetainTheActualHref() {
        val title = "角色筛选"
        val encoded = URI(null, null, "/$title", null).toASCIIString().drop(1)
        fun withHref(href: String) = Jsoup.parse(expanded).apply { selectFirst("a[title=$title]")!!.attr("href", href) }.html()
        listOf("/klbq/$title", "/klbq/${encoded.lowercase()}", "/klbq/index.php?title=$title",
            "/klbq/index.php?title=$encoded", "https://wiki.biligame.com:443/klbq/index.php?action=view&title=$encoded").forEach { href ->
            val result = BeginnerGuideParser.parse(withHref(href))
            assertTrue(result.canCache, "$href: ${result.issues}")
            val links = result.page.sections.flatMap { it.blocks }.filterIsInstance<GuideBlock.Paragraph>().flatMap { it.content.spans }
            assertEquals(URI(BEGINNER_GUIDE_URL).resolve(href).toASCIIString(), links.single { it.text == title && it.url != null }.url)
        }
        listOf("https://example.com/klbq/$title", "https://wiki.biligame.com:444/klbq/$title", "/other/$title",
            "/klbq/武器筛选", "/klbq/$title?action=edit", "/klbq/$title?diff=2", "/klbq/index.php?title=$title&action=history",
            "/klbq/index.php?title=$title&oldid=120474", "/klbq/index.php?title=$title&title=其他", "/klbq/%25${encoded.drop(1)}"
        ).forEach { href -> assertFalse(BeginnerGuideParser.parse(withHref(href)).canCache, href) }
    }

    @Test fun editorialChangesKeepRewordedContentAndNewSourceOrderPublishable() {
        val doc = Jsoup.parse(expanded)
        val notice = "两个国服账号的进度分别保存，但可以组队。移动版与 PC 版各自结算赛季。"
        doc.selectFirst(".alert")!!.text(notice)
        val overview = doc.select("h2").first()!!
        val overviewBody = overview.nextElementSiblings().takeWhile { it.tagName() != "h2" }
        val root = overview.parent()!!
        (listOf(overview) + overviewBody).forEach { root.appendChild(it) }
        listOf(".resp-tabs-list", ".resp-tabs-container").forEach { selector ->
            val parent = doc.selectFirst(selector)!!
            parent.prependChild(parent.children().last()!!)
        }
        val equipment = doc.select("table")[3].selectFirst("tbody")!!
        val renamed = equipment.child(1)
        renamed.child(0).text("主要武器")
        equipment.appendChild(renamed)

        val edited = doc.html()
        assertNull(BeginnerGuideRemoteSource.validationIssue(payload(edited)))
        val sections = BeginnerGuideParser.parse(edited).page.sections
        assertEquals("这是一款怎样的游戏", sections.last().title)
        assertEquals(notice, sections.single { it.title == "我该下哪个版本" }.blocks.filterIsInstance<GuideBlock.Paragraph>().single { it.notice }.content.text)
        assertEquals(listOf("移动端", "PC国服", "PC国际服"), sections.single { it.title == "下载与安装" }.blocks.filterIsInstance<GuideBlock.Heading>().map { it.title })
        assertEquals(listOf("副武器", "近战武器", "战术道具", "主要武器"), sections.single { it.title == "开始游戏前需要了解什么" }.blocks.filterIsInstance<GuideBlock.Record>().map { it.title.text })
    }

    @Test fun truncatedOrMislabeledExpandedResponsesCannotMasqueradeAsCompleteGuide() {
        fun Document.removeChapter(name: String) {
            val heading = select("h2").single { it.text() == name }
            heading.nextElementSiblings().takeWhile { it.tagName() != "h2" }.forEach { it.remove() }
            heading.remove()
        }
        val added = listOf("这是一款怎样的游戏", "开始游戏前需要了解什么", "游戏设置建议", "常见问题")
        val changes: List<(Document) -> Unit> = added.map { name -> { doc: Document -> doc.removeChapter(name) } } + listOf<(Document) -> Unit>(
            { doc -> added.forEach { doc.removeChapter(it) } }, // A superficially intact four-chapter result, but not revision 120197.
            { it.selectFirst("li")!!.remove() },
            { it.select("h3").last()!!.remove() },
            { it.select("dt").first()!!.remove() },
            { it.selectFirst("a[title=角色筛选]")!!.attr("href", "/klbq/武器筛选") },
            { it.select("table")[3].select("tr").last()!!.remove() },
            { it.select("table")[3].selectFirst("th")!!.text("项目") },
            { it.select("table")[3].selectFirst("td")!!.nextElementSibling()!!.empty() },
            { it.select("table")[3].select("tr")[2].child(0).text("主武器") }, // Enough rows, duplicate identity.
            { it.select("table")[4].select("tr").last()!!.remove() },
            { it.select("table")[4].select("tr")[1].child(0).remove() },
            { it.select("table")[4].selectFirst("th")!!.attr("colspan", "2") },
            { doc -> doc.select("table")[3].replaceWith(doc.select("table")[0].clone()) }, // Valid known schema in the wrong section.
            { it.select("small").last()!!.remove() },
            { it.select("dt")[2].remove() },
            { it.select("dl")[2].nextElementSibling()!!.remove() }, // Plenty of answers overall, this question has none.
            { it.select("dl").last()!!.nextElementSibling()!!.empty() }
        )
        changes.forEachIndexed { index, damage ->
            val broken = Jsoup.parse(expanded).also(damage).html()
            assertNotNull(BeginnerGuideRemoteSource.validationIssue(payload(broken)), "damage $index")
        }
    }

    @Test fun validatesBothEnvelopesAndRejectsWrongPage() {
        listOf(false, true).forEach { v2 -> listOf(html, expanded).forEach { assertNull(BeginnerGuideRemoteSource.validationIssue(payload(it, v2))) } }
        assertNotNull(BeginnerGuideRemoteSource.validationIssue(payload(html).replace(BEGINNER_GUIDE_PAGE, "其他页面")))
        assertNotNull(BeginnerGuideRemoteSource.validationIssue("{\"error\":{}}"))
    }

    @Test fun revisionAndVerifiedDirectoryIdentifyBothLegacyLayoutsButNeverDowngradeExpanded() {
        assertFalse("先弄清楚这两件事" in historical)
        val noToc = Jsoup.parse(historical).apply { select("#toc").remove() }.html()
        val brokenToc = Jsoup.parse(historical).apply { selectFirst("#toc a")!!.attr("href", "#配置需求") }.html()
        assertNull(BeginnerGuideRemoteSource.validationIssue(payload(historical))) // actual pre-revid disk format
        assertNull(BeginnerGuideRemoteSource.validationIssue(payload(noToc, revision = 120474)))
        listOf(payload(noToc), payload(brokenToc), payload(historical, revision = 120494),
            payload(historical, revision = -1)).forEach { raw ->
            assertNotNull(BeginnerGuideRemoteSource.validationIssue(raw))
            assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(BeginnerGuideApi.parseSource(OfflineCache.CacheResult(raw, true, 0))).kind)
        }
    }

    @Test fun badFreshRevisionCannotReplaceHistoricalDiskAndBothReadPathsPreserveThePage() = runBlocking {
        val legacyDisk = payload(historical) // text only, as written by released clients
        var disk: String? = legacyDisk
        val cached = assertIs<ApiResult.Success<BeginnerGuidePage>>(BeginnerGuideApi.parseSource(OfflineCache.CacheResult(legacyDisk, true, 456L)))
        assertEquals(4, cached.value.sections.size)
        suspend fun fetch(raw: String) = BeginnerGuideApi.loadSource {
            BeginnerGuideRemoteSource.fetchValidated(network = { raw }) { validate, network ->
                fetchValidatedCachePayload(true, network, { disk?.let { OfflineCache.CacheResult(it, true, 456L) } }, { disk = it }, validate)
            }
        }
        val badLink = Jsoup.parse(expanded).apply { selectFirst("a[title=角色筛选]")!!.attr("href", "/klbq/index.php?title=角色筛选&action=edit") }.html()
        val broken = payload(Jsoup.parse(expanded).apply { select("table").last()!!.remove() }.html(), revision = 120494)
        listOf(broken, payload(historical, revision = 120494), payload(badLink, revision = 120494), legacyDisk).forEach { bad ->
            assertEquals(cached, fetch(bad))
            assertEquals(legacyDisk, disk)
            disk = null
            assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(fetch(bad)).kind)
            assertNull(disk)
            disk = legacyDisk
        }
        val fresh = payload(historical, version2 = true, revision = 120474)
        val loaded = assertIs<ApiResult.Success<BeginnerGuidePage>>(fetch(fresh))
        assertFalse(loaded.isOffline)
        assertEquals(cached.value, loaded.value)
        assertEquals(fresh, disk)
        assertEquals(cached, BeginnerGuideApi.parseSource(OfflineCache.CacheResult(disk!!, true, 456L)))
        assertEquals(ErrorKind.NETWORK, assertIs<ApiResult.Error>(BeginnerGuideApi.parseSource(null)).kind)
    }

    @Test fun cancellationPropagates(): Unit = runBlocking {
        assertFailsWith<CancellationException> { BeginnerGuideApi.loadSource { throw CancellationException() } }
    }

    @Test fun nativeRouteRoundTripsWithoutChangingOldGuideRoutes() {
        assertEquals(WikiRoute.BeginnerGuide, decodeRoute(WikiRoute.BeginnerGuide.encode()))
        assertEquals(WikiRoute.PcGameSettings, decodeRoute("pcGameSettings"))
        assertEquals(WikiRoute.GameTips, decodeRoute("gameTips"))
    }

    @Test fun fullLiveCaptureMatchesContentFixture() {
        val file = System.getenv("BEGINNER_GUIDE_AUDIT_FILE")?.let(::File)
        assumeTrue("Set BEGINNER_GUIDE_AUDIT_FILE to full parse HTML", file?.isFile == true)
        assertFullCapture(file!!, html)
    }

    @Test fun fullNov21CaptureMatchesExpandedFixture() {
        val dir = System.getenv("WIKI_NOV21_AUDIT_DIR")
        assumeTrue("Set WIKI_NOV21_AUDIT_DIR to the Nov 21 capture directory", !dir.isNullOrBlank())
        assertFullCapture(File(dir!!, "beginner.html"), expanded)
    }

    @Test fun fullHistorical120474ResponseWithAndWithoutEnvelopeRevision() {
        val dir = System.getenv("WIKI_NOV21_AUDIT_DIR")
        assumeTrue("Set WIKI_NOV21_AUDIT_DIR to the Nov 21 capture directory", !dir.isNullOrBlank())
        val review = File(dir!!, "review-1121")
        val fullHtml = File(review, "120474.html")
        assertFullCapture(fullHtml, historical)
        val expected = BeginnerGuideParser.parse(historical).page
        val raw = File(review, "historical-120474.json").readText(Charsets.UTF_8)
        listOf(raw, payload(fullHtml.readText(Charsets.UTF_8))).forEach { archived ->
            assertNull(BeginnerGuideRemoteSource.validationIssue(archived))
            assertEquals(expected, assertIs<ApiResult.Success<BeginnerGuidePage>>(
                BeginnerGuideApi.parseSource(OfflineCache.CacheResult(archived, true, 456L))).value)
        }
    }

    private fun assertFullCapture(file: File, fixture: String) {
        val actual = BeginnerGuideParser.parse(file.readText(Charsets.UTF_8))
        assertTrue(actual.canCache, actual.issues.joinToString())
        val expected = BeginnerGuideParser.parse(fixture)
        assertTrue(expected.canCache, expected.issues.joinToString())
        // Includes stable IDs, rich spans/URLs, labels, list markers, every table cell and their order.
        assertEquals(expected.page, actual.page)
    }
}
