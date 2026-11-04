package com.nekolaska.calabiyau.feature.wiki.extraction.parser

import org.jsoup.nodes.Element

internal data class ExtractionTableRow(val cells: List<Element?>, val malformed: Boolean)

/** Bounded HTML grid; original cell identity survives row/column spans. */
internal fun extractionTableRows(table: Element): List<ExtractionTableRow> {
    val rows = table.select("tr").filter { it.closest("table") === table }
    val spans = arrayOfNulls<TableSpan>(64)
    val remainingInGroup = IntArray(rows.size)
    for (index in rows.lastIndex - 1 downTo 0) {
        if (rows[index].parent() === rows[index + 1].parent()) remainingInGroup[index] = remainingInGroup[index + 1] + 1
    }
    var parent: Element? = null
    return rows.mapIndexed { rowIndex, row ->
        // rowspan cannot cross tbody/thead/tfoot boundaries.
        if (row.parent() !== parent) spans.fill(null)
        parent = row.parent()
        val cells = arrayOfNulls<Element>(spans.size)
        spans.forEachIndexed { index, span ->
            if (span != null) {
                cells[index] = span.cell
                if (--span.remaining <= 0) spans[index] = null
            }
        }
        var column = 0
        var malformed = false
        val nextSpans = spans.copyOf()
        row.children().filter { it.tagName() == "td" || it.tagName() == "th" }.forEach { cell ->
            while (column < cells.size && cells[column] != null) column++
            val width = cell.attr("colspan").ifBlank { "1" }.toIntOrNull()
            val rawHeight = cell.attr("rowspan").ifBlank { "1" }.toIntOrNull()
            if (width == null || width !in 1..cells.size || rawHeight == null || rawHeight < 0 ||
                column + width > cells.size || (column until column + width).any { cells[it] != null }) {
                malformed = true
            } else {
                val remainingRows = remainingInGroup[rowIndex]
                val height = if (rawHeight == 0) remainingRows + 1 else rawHeight
                if (height > remainingRows + 1) malformed = true
                repeat(width) {
                    cells[column] = cell
                    if (height > 1) nextSpans[column] = TableSpan(cell, minOf(height - 1, remainingRows))
                    column++
                }
            }
        }
        // Invalid new spans must never poison following records.
        if (malformed) spans.fill(null) else nextSpans.copyInto(spans)
        ExtractionTableRow(cells.take(cells.indexOfLast { it != null } + 1), malformed)
    }
}

private class TableSpan(val cell: Element, var remaining: Int)
