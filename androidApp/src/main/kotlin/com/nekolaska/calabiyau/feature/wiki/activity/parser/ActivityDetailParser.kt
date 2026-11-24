package com.nekolaska.calabiyau.feature.wiki.activity.parser

import com.nekolaska.calabiyau.core.wiki.htmlHeadingLevel
import com.nekolaska.calabiyau.core.wiki.withoutWikiComments
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityDetailBlock
import com.nekolaska.calabiyau.feature.wiki.activity.model.ActivityDetailBlock.Heading
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal object ActivityDetailParser {
    private val heading = Regex("""(?m)^(={2,6})[ \t]*([^=\r\n]+?)[ \t]*\1[ \t]*\r?$""")
    private val legacySet = Regex("""^\{\{#set\s*:([^{}]*)\}\}""")
    private val subpage = Regex("""\{\{[:/][^{}|]+\}\}""")
    private val thumbFile = Regex("""(?i)\[\[(?:file|文件):[^\]\n]+?\|thumb(?:\||\]\])""")

    private class Section(val level: Int = 0, val title: String = "", val anchor: String = "") {
        val nodes = mutableListOf<Element>()
        var source: String? = null
    }

    fun parse(html: String, pageTitle: String, wikitext: String? = null): List<ActivityDetailBlock> {
        val article = Jsoup.parse(html).selectFirst(".mw-parser-output") ?: return emptyList()
        val source = wikitext?.withoutWikiComments()
        if (source != null && source.isBlank()) return emptyList()
        val banner = article.selectFirst("> .klbq-event-header")
        val beforeBody = if (banner != null) {
            if (banner.selectFirst(".klbq-event-header__title")?.text() != pageTitle) return emptyList()
            banner
        } else {
            // Old anniversary summaries have no banner. A title in arbitrary prose, an HTML-only
            // cache or a breadcrumb alone cannot identify one as an activity article.
            val fields = source?.trimStart()?.let(legacySet::find)?.groupValues?.get(1)?.split('|') ?: return emptyList()
            val name = fields.singleOrNull { it.substringBefore('=').trim() == "活动名" }?.substringAfter('=')?.trim()
            if (name != pageTitle) return emptyList()
            val crumb = article.selectFirst("> .klbq-breadcrumb-bar") ?: return emptyList()
            if (crumb.selectFirst(".current-page .mw-selflink")?.text() != pageTitle ||
                crumb.selectFirst("a[title=活动]") == null) return emptyList()
            crumb
        }

        val toc = article.select(".toc .toctext").eachText()
        article.select("style, script, .resourceLoader, .toc, .mw-editsection, .smwtticon, .smwttcontent").remove()
        if (!expandIconLabels(article)) return emptyList()
        val sections = mutableListOf(Section())
        for (node in beforeBody.nextElementSiblings()) {
            val level = node.htmlHeadingLevel()
            if (level != null && level >= 2) {
                val headline = node.selectFirst(".mw-headline")
                sections += Section(level, headline?.text() ?: node.text(), headline?.id().orEmpty().ifBlank { node.id() })
            } else sections.last().nodes.add(node)
        }
        val tocHeadings = sections.drop(1).iterator()
        if (!toc.all { title -> tocHeadings.asSequence().any { it.title == title } }) return emptyList()
        if (source != null) {
            val declarations = heading.findAll(source).toList()
            if (banner == null && declarations.isEmpty()) return emptyList()
            sections.first().source = source.take(declarations.firstOrNull()?.range?.first ?: source.length)
            val rendered = sections.drop(1).iterator()
            for ((index, decl) in declarations.withIndex()) {
                val part = rendered.asSequence().firstOrNull {
                    it.level == decl.groupValues[1].length && it.title == decl.groupValues[2].trim()
                } ?: return emptyList()
                part.source = source.substring(decl.range.last + 1, declarations.getOrNull(index + 1)?.range?.first ?: source.length)
            }
        }

        val blocks = mutableListOf<ActivityDetailBlock>()
        for ((index, part) in sections.withIndex()) {
            val reader = ActivitySectionReader(part.title, part.anchor)
            val body = reader.read(part.nodes) ?: return emptyList()
            val declared = part.source
            if (declared != null && (!ActivityDetailTables.matches(declared, reader.tables) ||
                    thumbFile.findAll(declared).count() > part.nodes.count { it.hasClass("thumb") })) return emptyList()
            if (index != 0) {
                // A parent can introduce transcluded subchapters. A sibling, TOC entry, heading
                // label or empty widget never supplies body evidence for an empty leaf.
                if (body.isEmpty() && ((sections.getOrNull(index + 1)?.level ?: 0) <= part.level ||
                        (!declared.isNullOrBlank() && !subpage.matches(declared.trim())))) return emptyList()
                blocks += Heading(part.title)
            }
            blocks += body
        }
        return blocks
    }

    // Quantities and decoration types are rendered by CSS pseudo-elements, also inside rule lists.
    private fun expandIconLabels(article: Element): Boolean {
        for (icon in article.select(".item-icon")) {
            val label = icon.selectFirst(".item-icon__text")?.takeIf(Element::hasText) ?: return false
            val type = label.attr("data-type")
            val count = icon.selectFirst(".item-icon__img[data-count]")?.attr("data-count").orEmpty()
            if (type.isNotBlank()) label.prependText("$type：")
            if (count.isNotBlank()) label.appendText(" ×$count")
        }
        return true
    }
}
