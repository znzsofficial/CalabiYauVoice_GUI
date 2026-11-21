package com.nekolaska.calabiyau.feature.wiki.beginner.parser

import com.nekolaska.calabiyau.feature.wiki.beginner.model.*
import com.nekolaska.calabiyau.core.wiki.reading.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Native reading document, with source order and inline emphasis/links intact. */
object BeginnerGuideParser {
    @JvmOverloads
    fun parse(html: String, revision: Long? = null): BeginnerGuideParseResult {
        val root = Jsoup.parse(html).selectFirst(".mw-parser-output")
            ?: return BeginnerGuideParseResult(BeginnerGuidePage(), listOf("缺少正文"))
        val issues = mutableListOf<String>()
        if (root.select(".error, .scribunto-error").isNotEmpty()) issues += "Wiki模板错误"
        val tocTitles = declaredChapters(root)
        root.select("style, script, #toc, .toc, .mw-editsection, .klbq-breadcrumb-bar, .printfooter, .navbox, .close").remove()
        val sections = mutableListOf<GuideSection>()
        var title = ""
        var subsection = ""
        var blocks = mutableListOf<GuideBlock>()
        val keys = mutableMapOf<String, Int>()
        val tables = mutableListOf<GuideTable>()
        val downloadPanes = mutableListOf<String>()
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
                    if (labels.isEmpty() || labels.any { it.isBlank() } || labels.size != panes.size) issues += "下载平台标签与正文不匹配"
                    if (title == DOWNLOAD_SECTION) downloadPanes += labels
                    panes.forEachIndexed { index, pane ->
                        val label = labels.getOrNull(index).orEmpty()
                        blocks += GuideBlock.Heading(id("platform", label), label)
                        walk(pane)
                    }
                }
                element.tagName() == "table" -> {
                    val schema = GuideTable.forSection(title, subsection)
                    if (schema == null) issues += "$title/$subsection：未知表格"
                    else {
                        tables += schema
                        val parsed = table(element, schema, issues)
                        val identities = parsed.map { it.first.text.trim() }
                        if (identities.size < schema.minRows || identities.distinct().size != identities.size) {
                            issues += "$title/$subsection：表格条目不足或重复"
                        }
                        parsed.forEach { (name, fields) -> blocks += GuideBlock.Record(id("row", name.text), name, fields) }
                    }
                }
                element.hasClass("alert") -> paragraph(element, notice = true)
                element.tagName() in setOf("ul", "ol") -> element.children().forEachIndexed { index, child ->
                    walk(child, depth + 1, if (element.tagName() == "ol") "${index + 1}." else "•")
                }
                element.tagName() == "li" -> {
                    paragraph(element, depth, marker)
                    element.children().filter { it.tagName() in setOf("ul", "ol") }.forEach { walk(it, depth) }
                }
                element.tagName() == "dt" -> {
                    val term = richText(element)
                    if (term.text.isBlank()) issues += "空白定义标题"
                    else blocks += GuideBlock.Term(id("term", term.text), term)
                }
                element.tagName() in setOf("p", "blockquote") -> paragraph(element, depth)
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
        issues += checkGuideContent(sections, tables, revision, tocTitles, downloadPanes)
        return BeginnerGuideParseResult(BeginnerGuidePage(sections), issues.distinct())
    }

    private val blockTags = setOf("div", "p", "ul", "ol", "li", "table", "h2", "h3", "h4", "h5", "h6", "dl", "dt", "dd", "blockquote", "section")

    /** Only the generated top-level TOC, with numbered local links to matching article headings, is legacy evidence. */
    private fun declaredChapters(root: Element): List<String>? {
        val tocs = root.children().filter { it.id() == "toc" && it.hasClass("toc") }
        if (tocs.isEmpty()) return null
        val toc = tocs.singleOrNull() ?: return emptyList()
        val list = toc.children().singleOrNull { it.tagName() == "ul" } ?: return emptyList()
        return list.children().mapIndexed { index, li ->
            val link = li.children().singleOrNull { it.tagName() == "a" } ?: return emptyList()
            val name = link.selectFirst(".toctext")?.text() ?: return emptyList()
            val href = link.attr("href")
            val target = root.getElementById(href.removePrefix("#"))
            if (!li.hasClass("toclevel-1") || link.selectFirst(".tocnumber")?.text() != (index + 1).toString() ||
                !href.startsWith('#') || target?.parent()?.tagName() != "h2" || target.text() != name) return emptyList()
            name
        }
    }

    internal fun richText(element: Element): GuideText = WikiRichTextParser.parse(element, BEGINNER_GUIDE_URL)

    internal fun safeUrl(raw: String): String? = WikiRichTextParser.safeUrl(raw, BEGINNER_GUIDE_URL)

    private fun table(element: Element, schema: GuideTable, issues: MutableList<String>): List<Pair<GuideText, List<GuideField>>> {
        val rows = element.select("tr").filter { it.closest("table") == element }
        val header = rows.firstOrNull()?.children().orEmpty()
        val labels = header.map { it.text().trim() }
        if (labels.toSet() != schema.columns.toSet() || labels.size != schema.columns.size || header.any {
                it.tagName() != "th" || listOf("rowspan", "colspan").any { attr -> it.hasAttr(attr) && it.attr(attr) != "1" }
            }) {
            issues += "${schema.section}/${schema.subsection}：无法识别的表头"; return emptyList()
        }
        val identityColumn = labels.indexOf(schema.columns.first())
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
