package com.nekolaska.calabiyau.feature.wiki.game.parser

import com.nekolaska.calabiyau.core.wiki.htmlHeadingLevel
import com.nekolaska.calabiyau.core.wiki.readWikiTableDeclaration
import com.nekolaska.calabiyau.core.wiki.withoutWikiComments
import com.nekolaska.calabiyau.feature.wiki.game.model.FieldGuideBlock
import com.nekolaska.calabiyau.feature.wiki.game.model.FieldGuideChapter
import org.jsoup.nodes.Document

/** Source order matters: identical subsection labels can recur under different parents. */
private sealed interface FieldGuideMark {
    data class Heading(val level: Int, val title: String) : FieldGuideMark
    data class Table(val columns: Int, val records: Int) : FieldGuideMark
}

private val heading = Regex("""(={2,6})\s*([^=]+?)\s*\1""")

private fun declaredFieldGuide(wikitext: String, isChapter: (String) -> Boolean): List<FieldGuideMark>? {
    val outline = mutableListOf<FieldGuideMark>()
    var inChapter = false
    val lines = wikitext.withoutWikiComments().lineSequence().iterator()
    for (raw in lines) {
        val line = raw.trim()
        val match = heading.matchEntire(line)
        if (match != null) {
            val level = match.groupValues[1].length
            val title = match.groupValues[2].trim()
            if (level == 2) inChapter = isChapter(title)
            if (inChapter) outline += FieldGuideMark.Heading(level, title)
        } else if (inChapter && line.startsWith("{|")) {
            val table = readWikiTableDeclaration(lines) ?: return null
            outline += FieldGuideMark.Table(table.columns, table.records)
        }
    }
    return outline
}

internal fun completeFieldGuide(wikitext: String, document: Document, isChapter: (String) -> Boolean, chapters: List<FieldGuideChapter>): Boolean {
    val expected = declaredFieldGuide(wikitext, isChapter) ?: return false
    val article = document.selectFirst(".mw-parser-output") ?: return false
    val actual = mutableListOf<FieldGuideMark>()
    var inChapter = false
    for (node in article.children()) {
        val level = node.htmlHeadingLevel()
        if (level != null && level >= 2) {
            val title = node.selectFirst(".mw-headline")?.text().orEmpty()
            if (level == 2) inChapter = isChapter(title)
            if (inChapter) actual += FieldGuideMark.Heading(level, title)
        } else if (inChapter && node.normalName() == "table" && (node.hasClass("klbqtable") || node.hasClass("wikitable"))) {
            val rows = node.select("tr").filter { it.closest("table") == node }
            actual += FieldGuideMark.Table(rows.firstOrNull()?.childrenSize() ?: 0, rows.size - 1)
        }
    }
    if (actual != expected) return false
    // A malformed rectangular table is a link in the tolerant reader. It is not a published record.
    val expectedChapters = expected.filterIsInstance<FieldGuideMark.Heading>().filter { it.level == 2 }
    if (expectedChapters.map { it.title } != chapters.map { it.title }) return false
    val expectedRows = expected.filterIsInstance<FieldGuideMark.Table>().sumOf { it.records }
    return chapters.none { c -> c.blocks.all { it.kind == FieldGuideBlock.Kind.HEADING } } &&
        chapters.sumOf { c -> c.blocks.count { it.kind == FieldGuideBlock.Kind.RECORD } } == expectedRows
}
