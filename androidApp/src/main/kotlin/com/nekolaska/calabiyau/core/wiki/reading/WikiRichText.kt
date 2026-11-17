package com.nekolaska.calabiyau.core.wiki.reading

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI
import java.net.URLDecoder

data class GuideSpan(val text: String, val bold: Boolean = false, val url: String? = null)
data class GuideText(val spans: List<GuideSpan>) {
    val text: String get() = spans.joinToString("") { it.text }
}
data class GuideField(val label: String, val value: GuideText)

/** Small shared inline reader; page-specific structure and completeness stay in their parsers. */
object WikiRichTextParser {
    fun parse(element: Element, baseUrl: String): GuideText {
        val spans = mutableListOf<GuideSpan>()
        fun add(text: String, bold: Boolean, url: String?) {
            if (text.isNotEmpty()) spans += GuideSpan(text, bold, url)
        }
        fun walk(node: Node, bold: Boolean = false, url: String? = null) {
            if (node is TextNode) { add(node.wholeText.replace(Regex("\\s+"), " "), bold, url); return }
            if (node !is Element) return
            if (node.tagName() in setOf("script", "style", "img") || node.hasClass("smwtticon")) return
            if (node.tagName() == "br") { add("\n", bold, url); return }
            if (node.hasClass("smw-highlighter")) {
                val note = node.selectFirst(".smwttcontent")?.text()?.ifBlank { null } ?: node.attr("title")
                if (note.isNotBlank()) add("\n注：$note", false, null)
                return
            }
            val strong = bold || node.tagName() in setOf("b", "strong") || node.attr("style").contains("font-weight:bold")
            val link = if (node.tagName() == "a") safeUrl(node.attr("href"), baseUrl) else url
            node.childNodes().forEach { walk(it, strong, link) }
        }
        walk(element)
        while (spans.isNotEmpty() && spans.first().text.isBlank()) spans.removeAt(0)
        while (spans.isNotEmpty() && spans.last().text.isBlank()) spans.removeAt(spans.lastIndex)
        if (spans.isNotEmpty()) {
            spans[0] = spans[0].copy(text = spans[0].text.trimStart())
            spans[spans.lastIndex] = spans.last().copy(text = spans.last().text.trimEnd())
        }
        return GuideText(spans)
    }

    fun safeUrl(raw: String, baseUrl: String): String? = runCatching {
        var uri = URI(baseUrl).resolve(raw.trim())
        if (uri.host == "game.bilibili.com" && uri.path == "/linkfilter/") {
            val value = uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("url=") }?.substringAfter('=')
                ?: return null
            uri = URI(URLDecoder.decode(value, "UTF-8"))
        }
        if (raw.isBlank() || uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) null
        else uri.toASCIIString()
    }.getOrNull()
}
