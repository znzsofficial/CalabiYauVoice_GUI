package com.nekolaska.calabiyau.core.wiki

import org.jsoup.nodes.Element

/** Jsoup has already parsed the tag. In particular, <h10> and <header> are not headings. */
internal fun Element.htmlHeadingLevel(): Int? {
    val tag = normalName()
    return if (tag.length == 2 && tag[0] == 'h' && tag[1] in '1'..'6') tag[1] - '0' else null
}

private val whitespaceRun = Regex("""\s+""")

/** Do not trim here: adjacent inline spans may need the space at either end. */
internal fun String.collapseWikiWhitespace(): String = replace(whitespaceRun, " ")

/** An unfinished HTML comment hides the rest of the wikitext, including apparent table/section markers. */
internal fun String.withoutWikiComments(): String {
    var open = indexOf("<!--")
    if (open < 0) return this
    return buildString {
        var from = 0
        while (open >= 0) {
            append(this@withoutWikiComments, from, open)
            val close = this@withoutWikiComments.indexOf("-->", open + 4)
            if (close < 0) return@buildString
            from = close + 3
            open = this@withoutWikiComments.indexOf("<!--", from)
        }
        append(this@withoutWikiComments, from, this@withoutWikiComments.length)
    }
}
