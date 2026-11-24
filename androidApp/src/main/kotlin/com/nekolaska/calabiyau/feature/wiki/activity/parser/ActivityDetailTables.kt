package com.nekolaska.calabiyau.feature.wiki.activity.parser

import org.jsoup.nodes.Element

/** An empty reward slot can be intentional. Compare its presence, position and kind against the
 * same revision rather than accepting every row that still has a day number. */
private data class Slot(val header: Boolean, val filled: Boolean, val iconCounts: List<String?>)
private data class TableShape(val caption: String, val rows: List<List<Slot>>)

/** zip() alone would silently accept a truncated row or table. */
private inline fun <A, B> List<A>.matchesInOrder(other: List<B>, match: (A, B) -> Boolean): Boolean =
    size == other.size && indices.all { match(this[it], other[it]) }

private fun Slot.matches(cell: Element): Boolean {
    if (header != (cell.normalName() == "th")) return false
    if (filled != (cell.hasText() || cell.selectFirst("img[src]") != null)) return false
    return iconCounts.isEmpty() || iconCounts == cell.select(".item-icon").map { icon ->
        icon.selectFirst(".item-icon__img[data-count]")?.attr("data-count")?.takeIf(String::isNotBlank)
    }
}

internal object ActivityDetailTables {
    const val SELECTOR = "table.klbqtable, table.wikitable"
    private val wikiTableTemplate = Regex("""(?i)^\{\{\s*wikitable\s*\|""")
    private val itemIcon = Regex("""\{\{道具图标\|([^{}]*)\}\}""")

    fun rows(table: Element) = table.select("> tr, > thead > tr, > tbody > tr, > tfoot > tr")
    fun cells(row: Element) = row.select("> th, > td")

    fun matches(source: String, tables: List<Element>): Boolean {
        val declared = mutableListOf<TableShape>()
        val lines = source.lineSequence().map(String::trim).iterator()
        for (line in lines) {
            val template = wikiTableTemplate.containsMatchIn(line)
            if (!template && !line.startsWith("{|")) continue
            declared += readShape(lines, template) ?: return false
        }
        if (declared.isEmpty()) return true // Transclusions can generate tables of their own.
        return declared.matchesInOrder(tables) { expected, table ->
            // Two equally long reward tracks are ambiguous without their free/paid captions.
            val sameCaption = tables.size == 1 || expected.caption == table.selectFirst("> caption")?.text().orEmpty()
            sameCaption && expected.rows.matchesInOrder(rows(table)) { slots, row ->
                slots.matchesInOrder(cells(row), Slot::matches)
            }
        }
    }

    /** Both {| ... |} and {{Wikitable ... }} use MediaWiki row/cell syntax. Preserve mixed
     * th/td rows, multiline values, and inline cells, including empty template arguments. */
    private fun readShape(lines: Iterator<String>, template: Boolean): TableShape? {
        val rows = mutableListOf<List<Slot>>()
        val row = mutableListOf<Slot>()
        var caption = ""
        fun counts(value: String) = itemIcon.findAll(value).map { icon -> icon.groupValues[1].split('|')
            .find { it.substringBefore('=').trim() == "数量" }?.substringAfter('=')?.trim()?.takeIf(String::isNotBlank) }.toList()
        fun slot(header: Boolean, text: String) = Slot(header, text.isNotBlank(), counts(text))
        fun flush() { if (row.isNotEmpty()) { rows += row.toList(); row.clear() } }
        for (line in lines) {
            val closesTable = if (template) line == "}}" else line.startsWith("|}")
            when {
                closesTable -> { flush(); return TableShape(caption, rows) }
                line.startsWith("{|") || wikiTableTemplate.containsMatchIn(line) -> return null
                line.startsWith("|-") -> flush()
                line.startsWith("|+") -> caption = line.drop(2).trim()
                line.isEmpty() -> Unit
                line.startsWith('|') || line.startsWith('!') -> {
                    val mark = line.first()
                    var start = 1
                    var depth = 0
                    var i = start
                    while (i + 1 < line.length) {
                        val c = line[i]
                        if (c == line[i + 1]) {
                            when (c) {
                                '{', '[' -> { depth++; i++ }
                                '}', ']' -> { depth--; i++ }
                                mark if depth == 0 -> {
                                    row += slot(mark == '!', line.substring(start, i))
                                    start = i + 2; i++
                                }
                            }
                        }
                        i++
                    }
                    row += slot(mark == '!', line.substring(start))
                }
                row.isNotEmpty() -> row[row.lastIndex] = row.last().let { it.copy(filled = true, iconCounts = it.iconCounts + counts(line)) }
            }
        }
        return null // Truncated declaration, including a template table missing its own close.
    }
}
