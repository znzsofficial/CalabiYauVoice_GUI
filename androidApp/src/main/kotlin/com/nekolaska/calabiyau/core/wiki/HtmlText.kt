package com.nekolaska.calabiyau.core.wiki

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Shared Jsoup-based HTML-to-text helpers used by the wiki parsers. */
object HtmlText {

    /**
     * Converts an HTML fragment to plain text: `<br>`/`<p>` become line breaks,
     * entities are decoded, tags are dropped and blank lines collapse.
     */
    fun clean(raw: String): String {
        if (raw.isBlank()) return ""
        return textWithLineBreaks(raw)
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    /** Full text of an HTML fragment with `<br>`/`<p>` preserved as line breaks. */
    fun textWithLineBreaks(html: String): String =
        textWithLineBreaks(Jsoup.parse(html).body())

    /** Full text of an element with `<br>`/`<p>` preserved as line breaks. */
    fun textWithLineBreaks(element: Element): String {
        val builder = StringBuilder()
        fun appendNode(node: Node) {
            when (node) {
                is TextNode -> builder.append(node.wholeText)
                is Element -> {
                    if (node.tagName().equals("br", ignoreCase = true)) {
                        builder.append('\n')
                    } else {
                        node.childNodes().forEach(::appendNode)
                        if (node.tagName().equals("p", ignoreCase = true)) builder.append('\n')
                    }
                }
            }
        }
        element.childNodes().forEach(::appendNode)
        return builder.toString()
    }
}
