package com.nekolaska.calabiyau.feature.wiki.meow.parser

import com.nekolaska.calabiyau.feature.wiki.meow.model.MeowLanguageGroup
import com.nekolaska.calabiyau.feature.wiki.meow.model.MeowLanguageSection
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object MeowLanguageParsers {

    fun parseSections(html: String): List<MeowLanguageSection> {
        val document = Jsoup.parse(html)
        document.select("#toc, .mw-editsection, sup.reference, .references, .mw-references-wrap, .alert").remove()
        val rootChildren = document.selectFirst(".mw-parser-output")?.children().orEmpty()
        if (rootChildren.isEmpty()) return emptyList()

        val sections = mutableListOf<MeowLanguageSection>()
        var currentTitle: String? = null
        val currentIntro = mutableListOf<String>()
        val currentGroups = mutableListOf<MeowLanguageGroup>()

        fun flush() {
            val title = currentTitle?.trim().orEmpty()
            if (title.isBlank()) {
                currentIntro.clear()
                currentGroups.clear()
                return
            }
            if (currentIntro.isNotEmpty() || currentGroups.isNotEmpty()) {
                sections += MeowLanguageSection(
                    title = title,
                    intro = currentIntro.distinct(),
                    groups = currentGroups.toList()
                )
            }
            currentIntro.clear()
            currentGroups.clear()
        }

        rootChildren.forEach { element ->
            when {
                element.tagName() == "h2" -> {
                    flush()
                    currentTitle = element.selectFirst(".mw-headline")?.text()?.trim()
                        ?: element.text().trim()
                }

                currentTitle != null && element.hasClass("CatLanguage") -> {
                    currentGroups += parseCatLanguageGroups(element)
                }

                currentTitle != null && currentGroups.isEmpty() -> {
                    currentIntro += parseIntroLines(element)
                }
            }
        }

        flush()
        return sections.filter { it.intro.isNotEmpty() || it.groups.isNotEmpty() }
    }

    private fun parseCatLanguageGroups(container: Element): List<MeowLanguageGroup> {
        val groups = mutableListOf<MeowLanguageGroup>()
        var title: String? = null
        var lines = mutableListOf<String>()

        fun flush() {
            val groupTitle = title?.normalizeText().orEmpty()
            val groupLines = lines.map { it.normalizeText() }.filter { it.isNotBlank() }.distinct()
            if (groupTitle.isNotBlank() && groupLines.isNotEmpty()) {
                groups += MeowLanguageGroup(groupTitle, groupLines)
            }
            lines = mutableListOf()
        }

        container.children().forEach { child ->
            when (child.tagName()) {
                "dl" -> {
                    flush()
                    title = child.selectFirst("dt")?.text()?.trim().orEmpty()
                }

                "ul" -> {
                    child.select("li").forEach { li ->
                        lines += li.text()
                    }
                }
            }
        }

        flush()
        return groups
    }

    private fun parseIntroLines(element: Element): List<String> {
        if (element.tagName() == "p") return listOfNotNull(element.text().normalizeText().takeIf { it.isNotBlank() })
        if (element.tagName() == "ul") return element.select("li").mapNotNull { li ->
            li.text().normalizeText().takeIf { it.isNotBlank() }
        }
        return emptyList()
    }

    private fun String.normalizeText(): String = trim()
        .replace(Regex("\\s+"), " ")
        .trim(' ', '：')
}
