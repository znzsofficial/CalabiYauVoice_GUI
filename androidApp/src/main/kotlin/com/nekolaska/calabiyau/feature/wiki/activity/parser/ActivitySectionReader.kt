package com.nekolaska.calabiyau.feature.wiki.activity.parser

import com.nekolaska.calabiyau.core.wiki.HtmlText
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityDetailBlock
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityDetailBlock.*
import org.jsoup.nodes.Element

/** Render one heading's siblings. null = unusable markup; empty = no body (e.g. only styles).
 * A WikiSection is evidence-backed and points to this particular heading, never an empty shell. */
internal class ActivitySectionReader(private val title: String, private val anchor: String) {
    private val wiki = WikiSection(title, anchor)
    // A table in a hidden/non-article wrapper cannot vouch for a missing visible reward table.
    val tables = mutableListOf<Element>()

    fun read(nodes: List<Element>): List<ActivityDetailBlock>? {
        val blocks = mutableListOf<ActivityDetailBlock>()
        for (node in nodes) if (!append(node, blocks)) return null
        return blocks
    }

    private fun link(out: MutableList<ActivityDetailBlock>): Boolean {
        if (title.isBlank() || anchor.isBlank()) return false
        if (wiki !in out) out += wiki
        return true
    }

    private fun append(node: Element, out: MutableList<ActivityDetailBlock>): Boolean {
        fun paragraph(el: Element) { HtmlText.detail(el).takeIf(String::isNotBlank)?.let { out += Paragraph(it) } }
        when {
            node.normalName() == "p" -> {
                paragraph(node)
                for (img in node.select("> a.image > img, > img")) out += image(img) ?: return false
            }
            node.hasClass("thumb") -> {
                val img = node.selectFirst(".thumbinner > a.image > img") ?: return false
                out += image(img, node.selectFirst(".thumbcaption")?.text()) ?: return false
            }
            node.hasClass("gallery") -> {
                val boxes = node.select("> li.gallerybox").ifEmpty { return false }
                for (box in boxes) {
                    val img = box.selectFirst(".thumb a.image img") ?: return false
                    out += image(img, box.selectFirst(".gallerytext")?.text()) ?: return false
                }
            }
            node.normalName() in setOf("ul", "ol", "dl") -> node.children().forEach(::paragraph)
            node.hasClass("poem") -> node.select("> p").forEach(::paragraph)
            node.`is`(ActivityDetailTables.SELECTOR) -> return appendTable(node, out)
            node.hasClass("resp-tabs") -> return appendTabs(node, out)
            node.hasClass("main-line-wrap") -> {
                val tabs = node.select("> .resp-tabs").ifEmpty { return false }
                for (tab in tabs) if (!appendTabs(tab, out)) return false
            }
            node.hasClass("row") -> { // e.g. the parallel free and paid anniversary reward tables
                val tables = node.select("> div > table").ifEmpty { return false }
                for (table in tables) if (!table.`is`(ActivityDetailTables.SELECTOR) || !appendTable(table, out)) return false
            }
            node.hasClass("playerBox") -> return mediaUrl(node.attr("data-music")) != null && link(out)
            node.hasClass("media-audio") -> return mediaUrl(node.attr("data-file")) != null && link(out)
            node.hasClass("klbq-story-table") || node.hasClass("text-conversation") -> {
                // The speaker, selectable replies and alternate routes may be identified only by images/CSS.
                return node.select("tr > td").any(Element::hasText) && link(out)
            }
            node.selectFirst("> table.klbq-story-table") != null -> {
                for (child in node.children()) if (!append(child, out)) return false
            }
            node.hasClass("alert-info") -> {
                node.select(".close").remove()
                paragraph(node)
            }
            node.normalName() == "div" && node.attr("style").replace(" ", "").startsWith("display:none") -> Unit
            node.hasClass("klbq-story-bg") -> Unit // story backdrop, not body evidence
            node.normalName() == "big" -> paragraph(node)
            node.hasText() || node.selectFirst("img, table, audio, video") != null -> return false
        }
        return true
    }

    private fun appendTable(table: Element, out: MutableList<ActivityDetailBlock>): Boolean {
        tables.add(table)
        val rows = ActivityDetailTables.rows(table).map(ActivityDetailTables::cells)
        if (rows.isEmpty() || rows.any { it.isEmpty() } || rows.none { row -> row.any { it.normalName() == "td" } }) return false
        val merged = rows.any { row -> row.any { cell ->
            listOf("colspan", "rowspan").any { attr -> cell.hasAttr(attr) && cell.attr(attr) != "1" }
        } }
        if (!merged && rows.any { it.size != rows.first().size }) return false
        if (rows.any { row -> row.none { it.hasText() || it.selectFirst("img[src]") != null } }) return false
        if (merged || rows.any { row -> row.any { !it.hasText() && it.selectFirst("img[src]") != null } }) return link(out)
        table.selectFirst("> caption")?.text()?.takeIf(String::isNotBlank)?.let { out += Heading(it) }
        rows.forEachIndexed { index, row ->
            val values = row.map(HtmlText::detail)
            val header = row.all { it.normalName() == "th" } || (index == 0 && values.size == 2 &&
                values[1] == "奖励" && values[0] in setOf("阶段", "累登天数"))
            out += TableRow(values, header)
        }
        return true
    }

    private fun appendTabs(tabs: Element, out: MutableList<ActivityDetailBlock>): Boolean {
        val labels = tabs.select("> .resp-tabs-list > li").map { it.text() }
        val panes = tabs.select("> .resp-tabs-container > .resp-tab-content")
        val singleChat = labels.isEmpty() && panes.size == 1 && panes[0].parent()?.hasClass("KaPhone") == true
        if (panes.isEmpty() || (!singleChat && labels.size != panes.size) || labels.any(String::isBlank)) return false
        val contents = panes.map { pane -> read(pane.children())?.takeIf { it.isNotEmpty() } ?: return false }
        // One original-section action for complex conversations / image tables, not one per tab.
        if (contents.any { wiki in it }) return link(out)
        for ((i, body) in contents.withIndex()) {
            labels.getOrNull(i)?.let { out += Heading(it) }
            out += body
        }
        return true
    }

    private fun mediaUrl(raw: String): String? = (if (raw.startsWith("//")) "https:$raw" else raw)
        .takeIf { it.startsWith("https://") && it.length > 8 }

    private fun image(img: Element, caption: String? = null): Image? = mediaUrl(img.attr("src"))?.let {
        Image(caption.orEmpty().ifBlank { img.attr("alt") }, it)
    }
}
