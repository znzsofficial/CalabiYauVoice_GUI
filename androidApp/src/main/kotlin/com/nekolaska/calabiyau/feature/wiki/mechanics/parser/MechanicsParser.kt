package com.nekolaska.calabiyau.feature.wiki.mechanics.parser

import com.nekolaska.calabiyau.core.wiki.reading.*
import com.nekolaska.calabiyau.feature.wiki.mechanics.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

object MechanicsParser {
    fun parse(kind: MechanicsKind, html: String): MechanicsParseResult {
        val root = Jsoup.parse(html).selectFirst(".mw-parser-output")
            ?: return MechanicsParseResult(MechanicsPage(), listOf("缺少正文"))
        val issues = mutableListOf<String>()
        if (root.select(".error, .scribunto-error").isNotEmpty()) issues += "Wiki模板错误"
        root.select("style, #toc, .toc, .mw-editsection, .klbq-breadcrumb-bar, .navbox, .printfooter, .catlinks, .leftPic, .resourceLoader").remove()
        val sections = mutableListOf<MechanicsSection>()
        var title = "概览"
        var subsection = ""
        var blocks = mutableListOf<MechanicsBlock>()
        val identities = mutableMapOf<String, Int>()
        val counts = mutableMapOf<String, MutableList<Int>>()
        fun id(type: String, value: String): String {
            val base = listOf(title, subsection, type, value).joinToString("") { "${it.length}:$it" }
            val occurrence = identities.getOrDefault(base, 0)
            identities[base] = occurrence + 1
            return "$base#$occurrence"
        }
        fun flush() {
            if (blocks.isNotEmpty()) sections += MechanicsSection(title, title, blocks.toList())
            blocks = mutableListOf()
        }
        fun text(element: Element, depth: Int, marker: String?) {
            val content = WikiRichTextParser.parse(element, kind.url)
            if (content.text.isNotBlank()) blocks += MechanicsBlock.Text(id("text", content.text), content, depth, marker)
        }
        fun image(element: Element) {
            val img = element.selectFirst("img") ?: return
            val label = img.attr("alt").ifBlank { "Wiki 配图" }
            val thumbnail = WikiRichTextParser.safeUrl(img.attr("data-src").ifBlank { img.attr("src") }, kind.url)
            val link = element.takeIf { it.tagName() == "a" } ?: img.closest("a")
            val fileUrl = link?.attr("href")?.let { WikiRichTextParser.safeUrl(it, kind.url) }
            blocks += MechanicsBlock.Image(id("image", label), label, thumbnail, fileUrl)
        }
        fun videos(element: Element) {
            // Read only the literal BV/page pair emitted by the Wiki's Biliplayer widget; never execute JS.
            val raw = if (element.tagName() == "script") element.data() else element.attr("src")
            val matches = Regex("bvid=(BV[A-Za-z0-9]{10})&(?:amp;)?p=([1-9][0-9]*)").findAll(raw)
                .map { it.groupValues[1] to it.groupValues[2] }.distinct().toList()
            matches.forEach { (bv, part) ->
                val url = "https://www.bilibili.com/video/$bv?p=$part"
                if (blocks.filterIsInstance<MechanicsBlock.Video>().none { it.url == url }) {
                    blocks += MechanicsBlock.Video(id("video", url), url, "$title · P$part")
                }
            }
        }
        fun characters(element: Element) {
            val cards = element.children().filter { it.tagName() == "div" }
            val names = mutableSetOf<String>()
            cards.forEach { card ->
                val links = card.select("a[href]")
                val link = links.lastOrNull { it.text().isNotBlank() } ?: links.firstOrNull()
                val path = link?.attr("href")?.let { WikiRichTextParser.safeUrl(it, kind.url) }?.let { URI(it) }
                    ?.takeIf { it.host == "wiki.biligame.com" && it.rawQuery == null && it.rawFragment == null }?.path
                val name = path?.takeIf { it.startsWith("/klbq/") }?.removePrefix("/klbq/")?.replace('_', ' ')
                if (name.isNullOrBlank() || '/' in name || ':' in name || !names.add(name)) {
                    issues += "$title：角色身份缺失或重复"; return@forEach
                }
                val img = card.selectFirst("img")
                val portrait = img?.attr("src")?.let { WikiRichTextParser.safeUrl(it, kind.url) }
                blocks += MechanicsBlock.Character(id("character", name), name, link.text().ifBlank { name }, portrait)
            }
        }
        fun table(element: Element) {
            val rows = element.select("tr").filter { it.closest("table") == element }
            val headers = rows.firstOrNull()?.children().orEmpty().map { it.text().trim() }
            val expected = when (subsection) {
                "爆破模式" -> setOf("项目", "一般爆破模式", "排位赛模式")
                "极限推进模式", "弦区争夺模式" -> setOf("项目", "增加点数")
                "超弦爆破模式" -> setOf("项目", "获得点数")
                else -> emptySet()
            }
            if (headers.toSet() != expected || headers.size != expected.size || expected.isEmpty()) {
                issues += "$subsection：未知表头"; return
            }
            var validRows = 0
            rows.drop(1).forEach { row ->
                val cells = row.children().filter { it.tagName() in setOf("th", "td") }
                if (cells.size != headers.size || cells.any { (it.hasAttr("rowspan") && it.attr("rowspan") != "1") ||
                    (it.hasAttr("colspan") && it.attr("colspan") != "1") }) {
                    issues += "$subsection：不完整或未知合并行"; return@forEach
                }
                val values = cells.map { WikiRichTextParser.parse(it, kind.url) }
                if (values.any { it.text.isBlank() }) { issues += "$subsection：空白字段"; return@forEach }
                val nameIndex = headers.indexOf("项目")
                val name = values[nameIndex]
                blocks += MechanicsBlock.Record(id("record", name.text), name,
                    headers.indices.filter { it != nameIndex }.map { GuideField(headers[it], values[it]) })
                validRows++
            }
            counts.getOrPut(subsection) { mutableListOf() }.add(validRows)
        }
        fun walk(element: Element, depth: Int = 0, marker: String? = null) {
            when {
                element.tagName() == "h2" -> { flush(); title = element.text().trim(); subsection = "" }
                element.tagName() in setOf("h3", "h4", "h5", "h6") || element.hasClass("alert") -> {
                    if (element.tagName() == "h3") subsection = element.text().trim()
                    val label = element.text().trim()
                    if (label.isNotBlank()) blocks += MechanicsBlock.Heading(id("heading", label), label)
                }
                element.tagName() == "script" || element.tagName() == "iframe" -> videos(element)
                element.hasClass("biliplayerW-dom") -> Unit
                element.hasClass("nav-chara") && kind == MechanicsKind.ROLES -> characters(element)
                element.tagName() == "table" -> table(element)
                element.tagName() == "img" || (element.tagName() == "a" && element.hasClass("image")) -> image(element)
                element.tagName() in setOf("ul", "ol") -> element.children().forEachIndexed { index, child ->
                    walk(child, depth + 1, if (element.tagName() == "ol") "${index + 1}." else "•")
                }
                else -> {
                    val run = Element("span")
                    var emitted = false
                    fun emit() {
                        if (run.childNodeSize() > 0) {
                            text(run, depth, if (!emitted) marker else null)
                            if (run.text().isNotBlank()) emitted = true
                            run.empty()
                        }
                    }
                    element.childNodes().forEach { node ->
                        val boundary = node is Element && (node.tagName() in blockTags || node.hasClass("image") ||
                            node.tagName() == "img" || node.selectFirst("img, script, iframe") != null)
                        if (boundary) { emit(); walk(node, depth) } else run.appendChild(node.clone())
                    }
                    emit()
                }
            }
        }
        walk(root)
        flush()
        if (sections.map { it.id }.distinct().size != sections.size) issues += "重复章节"
        fun minimumText(section: String, min: Int) {
            val found = sections.singleOrNull { it.title == section }
            if (found?.blocks?.count { it is MechanicsBlock.Text }?.let { it >= min } != true) issues += "$section：正文不完整"
        }
        when (kind) {
            MechanicsKind.STRINGIFY -> {
                minimumText("概览", 5)
                listOf("纸片人", "爬墙", "飘飞").forEach { minimumText(it, 1) }
                minimumText("历史更新改动", 6)
                listOf("纸片人", "爬墙", "飘飞", "官方教学视频").forEach { name ->
                    if (sections.singleOrNull { it.title == name }?.blocks?.any { it is MechanicsBlock.Video } != true) issues += "$name：缺少视频"
                }
            }
            MechanicsKind.ROLES -> {
                minimumText("概览", 1)
                mapOf("突击" to 4, "决斗" to 4, "守护" to 4, "支援" to 3, "先锋" to 4, "控场" to 5).forEach { (role, min) ->
                    minimumText(role, 2)
                    if (sections.singleOrNull { it.title == role }?.blocks?.count { it is MechanicsBlock.Character }?.let { it >= min } != true) issues += "$role：角色名单不完整"
                }
                val names = sections.flatMap { it.blocks }.filterIsInstance<MechanicsBlock.Character>().map { it.name }
                if (names.size != names.distinct().size) issues += "角色定位冲突"
                minimumText("更新改动历史", 2)
            }
            MechanicsKind.AUGMENTATION -> {
                minimumText("系统简介", 3)
                minimumText("升级项目", 7)
                minimumText("能量点获得详情", 8)
                minimumText("更新改动历史", 8)
                mapOf("爆破模式" to 15, "极限推进模式" to 7, "弦区争夺模式" to 6, "超弦爆破模式" to 13).forEach { (name, min) ->
                    if (counts[name]?.singleOrNull()?.let { it >= min } != true) issues += "$name：能量表不完整"
                }
            }
        }
        return MechanicsParseResult(MechanicsPage(sections), issues.distinct())
    }

    private val blockTags = setOf("div", "section", "p", "ul", "ol", "li", "dl", "dt", "dd", "table", "h2", "h3", "h4", "h5", "h6", "script", "iframe")
}
