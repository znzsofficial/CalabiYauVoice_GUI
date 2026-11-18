package com.nekolaska.calabiyau.feature.wiki.decoration.parser

import com.nekolaska.calabiyau.core.wiki.HtmlText
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/** Readable markup shared by both sources; struck prices must never look like current offers. */
internal object DecorationText {
    fun read(element: Element?): String = element?.html()?.let(::read).orEmpty()

    fun description(element: Element?): String = element?.html()?.let { parse(it, true, false) }.orEmpty()

    fun read(raw: String): String = parse(raw, false)

    fun description(raw: String): String = parse(raw, true)

    private fun parse(raw: String, keepLines: Boolean, literalLines: Boolean = keepLines): String {
        val wiki = raw.replace(Regex("\\[\\[([^]\\n]+)]]")) {
            it.groupValues[1].substringAfterLast('|').substringBefore('#')
        }.replace(Regex("\\[(https?://\\S+)\\s+([^]]+)]"), "$2")
            .replace("'''", "").replace("''", "")
        val body = Jsoup.parseBodyFragment(wiki).body()
        body.select("del, s, strike").reversed().forEach { deleted ->
            deleted.replaceWith(TextNode("【已失效：${HtmlText.clean(deleted.html())}】"))
        }
        return if (keepLines) HtmlText.detail(body, preserveTextLines = literalLines) else HtmlText.clean(body.html())
    }
}
