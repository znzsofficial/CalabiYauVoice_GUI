package com.nekolaska.calabiyau.feature.wiki.extraction.parser

import com.nekolaska.calabiyau.core.wiki.HtmlText
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal fun content(element: Element?, remove: String? = null): String {
    if (element == null) return ""
    val copy = element.clone()
    copy.select("style, script, .smwttcontent, .smwtticon, .mw-editsection").remove()
    if (remove != null) copy.select(remove).remove()
    copy.select("li").forEach { it.prependText("• ") }
    val doc = Jsoup.parseBodyFragment(copy.html())
    doc.outputSettings().prettyPrint(false)
    return HtmlText.clean(doc.body().html())
}

internal fun headerKey(value: String): String = value.replace('（', '(').replace('）', ')')
    .filterNot { it.isWhitespace() || it == '\u00a0' }

internal fun qualityCode(label: String): Int? = Regex("""^Q(\d+)(?:\s+.*)?$""")
    .matchEntire(label)?.groupValues?.get(1)?.toIntOrNull()
