package com.nekolaska.calabiyau.core.wiki

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Shared Jsoup-based HTML-to-text helpers used by the wiki parsers. */
object HtmlText {
    private val ignoredTags = setOf("script", "style")

    /**
     * Converts an HTML fragment to plain text: breaks and block elements separate lines,
     * entities are decoded, non-breaking spaces normalized and object placeholders removed.
     */
    fun clean(raw: String): String {
        if (raw.isBlank()) return ""
        return textWithLineBreaks(raw)
            .replace('\u00A0', ' ')
            .replace("\uFFFC", "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    /** Full text of an HTML fragment with breaks and block boundaries preserved. */
    fun textWithLineBreaks(html: String): String =
        textWithLineBreaks(Jsoup.parse(html).body())

    /** Full text of an element with breaks and block boundaries preserved. */
    fun textWithLineBreaks(element: Element): String {
        val builder = StringBuilder()
        fun appendNode(node: Node) {
            when (node) {
                is TextNode -> builder.append(node.wholeText)
                is Element -> {
                    if (node.tagName() in ignoredTags) return
                    if (node.tagName().equals("br", ignoreCase = true)) {
                        builder.append('\n')
                    } else {
                        if (node.isBlock && builder.isNotEmpty() && builder.last() != '\n') builder.append('\n')
                        node.childNodes().forEach(::appendNode)
                        if (node.isBlock) builder.append('\n')
                    }
                }
            }
        }
        element.childNodes().forEach(::appendNode)
        return builder.toString()
    }
}
