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

/** Drop-table keys use compact text; item descriptions and effect fields keep their original paragraphs. */
internal fun itemProse(element: Element?, remove: String? = null): String {
    val copy = element?.clone() ?: return ""
    copy.select(".smwttcontent, .smwtticon, .mw-editsection").remove()
    if (remove != null) copy.select(remove).remove()
    copy.select("li").forEach { it.prependText("• ") }
    return HtmlText.detail(copy)
}

internal fun headerKey(value: String): String = value.replace('（', '(').replace('）', ')')
    .filterNot { it.isWhitespace() || it == '\u00a0' }

private val poolItemCount = Regex("""(\d+)\s*项""")
private val qualityLabel = Regex("""^Q(\d+)(?:\s+.*)?$""")

internal fun declaredPoolItemCount(text: String): Int? = poolItemCount.matchEntire(text)?.groupValues?.get(1)?.toIntOrNull()

internal fun qualityCode(label: String): Int? = qualityLabel.matchEntire(label)?.groupValues?.get(1)?.toIntOrNull()
