package com.nekolaska.calabiyau.feature.wiki.imprint.parser

import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.feature.wiki.imprint.model.ImprintItem
import com.nekolaska.calabiyau.feature.wiki.imprint.model.ImprintPage
import com.nekolaska.calabiyau.feature.wiki.imprint.model.ImprintSection
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object ImprintParsers {

    private const val WIKI_URL = "https://wiki.biligame.com/klbq/%E5%8D%B0%E8%BF%B9"
    private val levels = listOf("等级1印迹", "等级2印迹", "等级3印迹", "等级4印迹")
    private val skippedSections = setOf("目录")

    fun parseHtml(html: String): ImprintPage {
        val document = Jsoup.parse(html)
        val content = document.selectFirst(".mw-parser-output") ?: document.body()
        return ImprintPage(
            title = "印迹",
            notice = parseNotice(content),
            wikiUrl = WIKI_URL,
            sections = parseSections(content)
        )
    }

    private fun parseNotice(content: Element): String {
        val text = content.selectFirst(".heimu-toggle, .ambox, .note, p")?.cleanText().orEmpty()
        return text.takeIf { it.contains("印迹") || it.contains("公测") }.orEmpty()
    }

    private fun parseSections(content: Element): List<ImprintSection> {
        return content.select("h2").mapNotNull { heading ->
            val character = heading.selectFirst(".mw-headline")?.cleanText().orEmpty()
            if (character.isBlank() || character in skippedSections) return@mapNotNull null
            val elements = generateSequence(heading.nextElementSibling()) { it.nextElementSibling() }
                .takeWhile { it.tagName() != "h2" }
                .toList()
            val imprints = elements.flatMap(::parseImprints).ifEmpty { return@mapNotNull null }
            ImprintSection(character, imprints)
        }
    }

    private fun parseImprints(element: Element): List<ImprintItem> {
        return element.select("li.gallerybox").mapNotNull { item ->
            val text = item.cleanText()
            if (text.isBlank() || levels.any { text == it }) return@mapNotNull null
            val name = item.selectFirst("b, strong")?.cleanText().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            val quote = Regex("【([^】]+)】").find(text)?.groupValues?.getOrNull(1).orEmpty()
            val obtainMethod = text.substringAfter("获得方式：", "")
                .replace(Regex("^\\s*[:：]\\s*"), "")
                .trim()
            ImprintItem(
                name = name,
                quote = quote,
                obtainMethod = obtainMethod,
                level = parseLevelFromImage(item) ?: parseLevelFromName(name),
                imageUrl = item.firstImageUrl()
            )
        }
    }

    private fun parseLevelFromImage(element: Element): Int? {
        val src = element.selectFirst("img")?.attr("alt").orEmpty()
            .ifBlank { element.selectFirst("img")?.attr("src").orEmpty() }
        return Regex("335\\d+([1-4])").find(src)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun parseLevelFromName(name: String): Int? {
        return Regex("等级\\s*([1-4])").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun Element.cleanText(): String {
        val copy = clone()
        copy.select("style,script,.mw-editsection").remove()
        return copy.text().replace(Regex("\\s+"), " ").trim()
    }

    private fun Element.firstImageUrl(): String? {
        val src = selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() } ?: return null
        return WikiImageUrls.originalFromThumbnail(src)
    }
}
