package com.nekolaska.calabiyau.core.wiki

internal data class WikiTableDeclaration(val columns: Int, val records: Int)

/** Consume an inline wikitext table after its `{|` line. Count rows, not the number of cell lines.
 * These readers support rectangular article tables; an unclosed or nested declaration is not evidence of completeness. */
internal fun readWikiTableDeclaration(lines: Iterator<String>): WikiTableDeclaration? {
    var columns = 0
    var records = 0
    var hasData = false
    for (raw in lines) {
        val line = raw.trimStart()
        when {
            line.startsWith("|}") -> return WikiTableDeclaration(columns, records + if (hasData) 1 else 0)
            line.startsWith("{|") -> return null
            line.startsWith("|-") -> {
                if (hasData) records++
                hasData = false
            }
            line.startsWith('!') -> columns += line.split("!!").size
            line.startsWith('|') && !line.startsWith("|+") -> hasData = true
        }
    }
    return null
}
