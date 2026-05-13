package com.nekolaska.calabiyau.feature.wiki.meme.parser

import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.feature.wiki.meme.model.MemeEntry
import com.nekolaska.calabiyau.feature.wiki.meme.model.MemeOfficialIssue
import com.nekolaska.calabiyau.feature.wiki.meme.model.MemePage
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object MemeParsers {

    fun parsePage(html: String): MemePage {
        val document = Jsoup.parse(html)
        document.select("#toc, .mw-editsection, style, script, sup.reference, .references, .mw-references-wrap").remove()
        val content = document.selectFirst(".mw-parser-output") ?: document.body()
        return MemePage(
            officialIssues = parseOfficialIssues(content),
            editorEntries = parseEditorEntries(content)
        )
    }

    private fun parseOfficialIssues(content: Element): List<MemeOfficialIssue> {
        val officialHeading = content.select("h2").firstOrNull { it.headingText() == "官方编写" } ?: return emptyList()
        val elements = elementsUntilNextH2(officialHeading)
        val tabTitles = elements.flatMap { element ->
            element.select(".tab-nav a").map { it.cleanText() }
        }.filter { it.isNotBlank() }
        val images = elements.flatMap { element ->
            element.select(".tab-content img, img").mapNotNull { img -> img.imageUrl() }
        }
        return images.mapIndexed { index, imageUrl ->
            MemeOfficialIssue(
                title = tabTitles.getOrNull(index).orEmpty().ifBlank { "官方编写 ${index + 1}" },
                imageUrl = imageUrl
            )
        }
    }

    private fun parseEditorEntries(content: Element): List<MemeEntry> {
        val editorHeading = content.select("h2").firstOrNull { it.headingText() == "编辑编写" } ?: return emptyList()
        val elements = elementsUntilNextH2(editorHeading)
        val entries = mutableListOf<MemeEntry>()
        var currentTitle: String? = null
        val descriptionParts = mutableListOf<String>()

        fun flush() {
            val title = currentTitle?.trim(' ', '：', ':').orEmpty()
            val description = descriptionParts.joinToString("\n")
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
            if (title.isNotBlank() && description.isNotBlank()) entries += MemeEntry(title, description)
            descriptionParts.clear()
        }

        elements.forEach { element ->
            when (element.tagName()) {
                "dl" -> {
                    val title = element.selectFirst("dt")?.cleanText().orEmpty()
                    if (title.isNotBlank()) {
                        flush()
                        currentTitle = title
                    }
                }
                "p" -> {
                    val text = element.cleanText()
                    if (currentTitle != null && text.isNotBlank()) descriptionParts += text
                }
                "ul", "ol" -> {
                    if (currentTitle != null) {
                        element.select("li").map { it.cleanText() }.filter { it.isNotBlank() }.forEach { descriptionParts += it }
                    }
                }
            }
        }
        flush()
        return entries
    }

    private fun elementsUntilNextH2(heading: Element): List<Element> {
        return generateSequence(heading.nextElementSibling()) { it.nextElementSibling() }
            .takeWhile { it.tagName() != "h2" }
            .toList()
    }

    private fun Element.headingText(): String = selectFirst(".mw-headline")?.cleanText().orEmpty().ifBlank { cleanText() }

    private fun Element.cleanText(): String {
        val copy = clone()
        copy.select("style,script,.mw-editsection").remove()
        return copy.text().replace(Regex("\\s+"), " ").trim()
    }

    private fun Element.imageUrl(): String? {
        val src = attr("src").takeIf { it.isNotBlank() } ?: return null
        return WikiImageUrls.originalFromThumbnail(src)
    }
}
