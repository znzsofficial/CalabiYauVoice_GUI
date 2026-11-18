package com.nekolaska.calabiyau.core.wiki

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Shared Jsoup-based HTML-to-text helpers used by the wiki parsers. */
object HtmlText {
    private val ignoredTags = setOf("script", "style")

    /** Catalog prose: keep authored breaks (including adjacent br), but not HTML source indentation. */
    fun detail(element: Element?, preserveTextLines: Boolean = false): String {
        if (element == null) return ""
        val out = StringBuilder()
        var space = false
        fun boundary() {
            space = false
            if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
        }
        fun visit(node: Node, literalLines: Boolean) {
            when (node) {
                is TextNode -> for (c in node.wholeText.replace("\r\n", "\n").replace('\r', '\n')) {
                    when {
                        c == '\uFFFC' -> Unit
                        literalLines && c == '\n' -> { space = false; out.append('\n') }
                        c in " \t\n\u000c\u00a0" -> space = true
                        else -> {
                            if (space && out.isNotEmpty() && out.last() != '\n') out.append(' ')
                            space = false; out.append(c)
                        }
                    }
                }
                is Element -> when (node.normalName()) {
                    "br" -> { space = false; out.append('\n') }
                    in ignoredTags -> Unit
                    else -> {
                        if (node.isBlock) boundary()
                        val keep = literalLines || node.normalName() == "pre"
                        for (child in node.childNodes()) visit(child, keep)
                        if (node.isBlock) boundary()
                    }
                }
            }
        }
        visit(element, preserveTextLines)
        return out.toString().trim()
    }

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
