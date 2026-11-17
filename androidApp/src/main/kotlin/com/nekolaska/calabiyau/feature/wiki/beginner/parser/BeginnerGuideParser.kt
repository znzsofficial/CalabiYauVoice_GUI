package com.nekolaska.calabiyau.feature.wiki.beginner.parser

import com.nekolaska.calabiyau.feature.wiki.beginner.model.*
import com.nekolaska.calabiyau.core.wiki.reading.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Native reading document, with source order and inline emphasis/links intact. */
object BeginnerGuideParser {
    fun parse(html: String): BeginnerGuideParseResult {
        val root = Jsoup.parse(html).selectFirst(".mw-parser-output")
            ?: return BeginnerGuideParseResult(BeginnerGuidePage(), listOf("缺少正文"))
        val issues = mutableListOf<String>()
        if (root.select(".error, .scribunto-error").isNotEmpty()) issues += "Wiki模板错误"
        root.select("style, script, #toc, .toc, .mw-editsection, .klbq-breadcrumb-bar, .printfooter, .navbox, .close").remove()
        val sections = mutableListOf<GuideSection>()
        var title = ""
        var subsection = ""
        var blocks = mutableListOf<GuideBlock>()
        val keys = mutableMapOf<String, Int>()
        val tables = mutableMapOf<Pair<String, String>, MutableList<Int>>()
        val tabLabels = mutableListOf<String>()
        fun id(kind: String, value: String): String {
            val base = listOf(title, subsection, kind, value).joinToString("") { "${it.length}:$it" }
            val occurrence = keys.getOrDefault(base, 0)
            keys[base] = occurrence + 1
            return "$base#$occurrence"
        }
        fun flush() {
            if (title.isNotBlank()) sections += GuideSection(title, title, blocks.toList())
            blocks = mutableListOf()
        }
        fun paragraph(element: Element, depth: Int = 0, marker: String? = null, notice: Boolean = false) {
            val copy = element.clone()
            copy.select("ul, ol").remove()
            val text = richText(copy)
            if (title.isNotBlank() && text.text.isNotBlank()) {
                blocks += GuideBlock.Paragraph(id("text", text.text), text, depth, marker, notice)
            }
        }
        fun walk(element: Element, depth: Int = 0, marker: String? = null) {
            when {
                element.tagName() == "h2" -> {
                    flush()
                    title = element.text().trim()
                    subsection = ""
                }
                element.tagName() in setOf("h3", "h4", "h5", "h6") -> {
                    subsection = element.text().trim()
                    blocks += GuideBlock.Heading(id("heading", subsection), subsection)
                }
                element.hasClass("resp-tabs") -> {
                    val labels = element.selectFirst(".resp-tabs-list")?.children().orEmpty().map { it.text() }
                    val panes = element.selectFirst(".resp-tabs-container")?.children().orEmpty()
                        .filter { it.hasClass("resp-tab-content") }
                    if (labels.isEmpty() || labels.size != panes.size) issues += "下载平台标签与正文不匹配"
                    panes.forEachIndexed { index, pane ->
                        val label = labels.getOrNull(index).orEmpty()
                        tabLabels += label
                        blocks += GuideBlock.Heading(id("platform", label), label)
                        walk(pane)
                    }
                }
                element.tagName() == "table" -> {
                    val parsed = table(element, issues)
                    tables.getOrPut(title to subsection) { mutableListOf() }.add(parsed.size)
                    parsed.forEach { (name, fields) -> blocks += GuideBlock.Record(id("row", name.text), name, fields) }
                }
                element.hasClass("alert") -> paragraph(element, notice = true)
                element.tagName() in setOf("ul", "ol") -> element.children().forEachIndexed { index, child ->
                    walk(child, depth + 1, if (element.tagName() == "ol") "${index + 1}." else "•")
                }
                element.tagName() == "li" -> {
                    paragraph(element, depth, marker)
                    element.children().filter { it.tagName() in setOf("ul", "ol") }.forEach { walk(it, depth) }
                }
                element.tagName() in setOf("p", "dt", "dd", "blockquote") -> paragraph(element, depth)
                else -> {
                    // Tab panes mix direct inline nodes with lists; emit each run once in document order.
                    val run = Element("span")
                    fun emit() { if (run.childNodeSize() > 0) { paragraph(run, depth); run.empty() } }
                    element.childNodes().forEach { node ->
                        if (node is Element && node.tagName() in blockTags) { emit(); walk(node, depth) }
                        else run.appendChild(node.clone())
                    }
                    emit()
                }
            }
        }
        walk(root)
        flush()
        val expectedSections = listOf("我适合入坑吗", "我该下哪个版本", "下载与安装", "配置需求")
        expectedSections.forEach { name ->
            if (sections.count { it.title == name && it.blocks.isNotEmpty() } != 1) issues += "$name：章节缺失或重复"
        }
        if (sections.map { it.id }.distinct().size != sections.size) issues += "章节名称重复"
        // Revision 120197 baselines: additions pass; legitimate removals require source review.
        mapOf(("我该下哪个版本" to "") to 4, ("配置需求" to "PC 端") to 8, ("配置需求" to "移动端") to 5)
            .forEach { (context, minimum) ->
                val counts = tables[context].orEmpty()
                if (counts.size != 1 || counts.singleOrNull()?.let { it >= minimum } != true) issues += "$context：表格不完整"
            }
        if (tabLabels.size != tabLabels.distinct().size || !tabLabels.containsAll(listOf("PC国服", "PC国际服", "移动端"))) {
            issues += "下载平台不完整"
        }
        val intro = sections.firstOrNull { it.title == "我适合入坑吗" }
        if (intro?.blocks?.filterIsInstance<GuideBlock.Paragraph>()?.size?.let { it >= 22 } != true) issues += "入坑说明不完整"
        if (intro?.blocks?.filterIsInstance<GuideBlock.Heading>()?.map { it.title }
                ?.containsAll(listOf("作为射击游戏", "作为 PvP")) != true) issues += "入坑说明标题不完整"
        val versions = sections.firstOrNull { it.title == "我该下哪个版本" }?.blocks.orEmpty()
            .filterIsInstance<GuideBlock.Paragraph>()
        if (versions.size < 2 || versions.none { it.notice }) issues += "版本互通说明不完整"
        if (sections.firstOrNull { it.title == "配置需求" }?.blocks.orEmpty().none { it is GuideBlock.Paragraph }) {
            issues += "配置来源说明缺失"
        }
        val downloads = sections.firstOrNull { it.title == "下载与安装" }?.blocks.orEmpty()
        if (downloads.filterIsInstance<GuideBlock.Paragraph>().size < 13 ||
            downloads.filterIsInstance<GuideBlock.Paragraph>().flatMap { it.content.spans }.count { it.url != null } < 4) {
            issues += "下载说明或链接不完整"
        }
        return BeginnerGuideParseResult(BeginnerGuidePage(sections), issues.distinct())
    }

    private val blockTags = setOf("div", "p", "ul", "ol", "li", "table", "h2", "h3", "h4", "h5", "h6", "dl", "dt", "dd", "blockquote", "section")

    internal fun richText(element: Element): GuideText = WikiRichTextParser.parse(element, BEGINNER_GUIDE_URL)

    internal fun safeUrl(raw: String): String? = WikiRichTextParser.safeUrl(raw, BEGINNER_GUIDE_URL)

    private fun table(element: Element, issues: MutableList<String>): List<Pair<GuideText, List<GuideField>>> {
        val rows = element.select("tr").filter { it.closest("table") == element }
        val header = rows.firstOrNull()?.children().orEmpty()
        val labels = header.map { it.text().trim() }
        val identityLabel = if ("版本" in labels) "版本" else "项目"
        val expected = if (identityLabel == "版本") setOf("版本", "游玩平台", "登录方式", "说明") else setOf("项目", "最低配置", "推荐配置")
        if (labels.toSet() != expected || labels.size != expected.size || header.any { it.tagName() != "th" }) {
            issues += "无法识别的表头"; return emptyList()
        }
        data class Cell(val text: GuideText, val columns: IntRange)
        data class Carry(val cell: Cell, val end: Int)
        val pending = mutableMapOf<Int, Carry>()
        val result = mutableListOf<Pair<GuideText, List<GuideField>>>()
        rows.drop(1).forEachIndexed { rowIndex, row ->
            val occupied = mutableMapOf<Int, Cell>()
            pending.filterValues { it.end >= rowIndex }.forEach { (column, carry) -> occupied[column] = carry.cell }
            pending.entries.removeAll { it.value.end <= rowIndex }
            var column = 0
            row.children().filter { it.tagName() in setOf("td", "th") }.forEach { cell ->
                while (column in occupied) column++
                fun span(name: String, maximum: Int): Int? = if (!cell.hasAttr(name)) 1 else cell.attr(name).toIntOrNull()?.takeIf { it in 1..maximum }
                val colspan = span("colspan", labels.size)
                val rowspan = span("rowspan", rows.size - 1 - rowIndex)
                if (colspan == null || rowspan == null || column + colspan > labels.size) { issues += "非法合并单元格"; return@forEach }
                val columns = column until column + colspan
                val value = Cell(richText(cell), columns)
                if (value.text.text.isBlank()) issues += "空白表格字段"
                columns.forEach { c ->
                    if (c in occupied) issues += "合并单元格重叠"
                    occupied[c] = value
                    if (rowspan > 1) pending[c] = Carry(value, rowIndex + rowspan - 1)
                }
                column += colspan
            }
            if (occupied.keys != labels.indices.toSet()) { issues += "表格行缺少字段"; return@forEachIndexed }
            val identityColumn = labels.indexOf(identityLabel)
            val name = occupied.getValue(identityColumn)
            if (name.columns.count() != 1) issues += "条目名称跨列"
            val fields = labels.indices.filter { it != identityColumn }.map { occupied.getValue(it) }.distinct().map { cell ->
                GuideField(cell.columns.joinToString(" / ") { labels[it] }, cell.text)
            }
            result += name.text to fields
        }
        return result
    }
}
