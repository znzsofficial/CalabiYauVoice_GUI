package com.nekolaska.calabiyau.feature.wiki.extraction.parser

import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import org.jsoup.Jsoup

object ExtractionCatalogParsers {
    fun parse(html: String, kind: ExtractionCatalogKind): List<ExtractionEntry> = parseResult(html, kind).entries

    fun parseResult(html: String, kind: ExtractionCatalogKind): ExtractionParseResult {
        val document = Jsoup.parse(html)
        val issues = mutableListOf<String>()
        val entries = if (kind == ExtractionCatalogKind.DROPS) {
            ExtractionDropParser.parse(document, issues)
        } else {
            val prefix = if (kind == ExtractionCatalogKind.LOOT) "sec-card" else "sec-att-card"
            val qualityNames = document.select(".cardSelectOption[data-group=2]").mapNotNull {
                val level = it.attr("data-option").substringAfter('|').toIntOrNull() ?: return@mapNotNull null
                level to it.text().trim()
            }.toMap()
            document.select(".gallerygrid-item.$prefix").mapNotNull { card ->
                ExtractionItemParser.parse(card, prefix, qualityNames).also {
                    if (it == null) issues += "$prefix: missing name"
                }
            }
        }
        // Never merge different records just because their names or pools match.
        val usedKeys = mutableMapOf<String, Int>()
        val unique = entries.map { entry ->
            val labels = mutableMapOf<String, Int>()
            entry.copy(dropPoolGroups = entry.dropPoolGroups.map { group ->
                val occurrence = labels.getOrDefault(group.label, 0)
                labels[group.label] = occurrence + 1
                group.copy(key = "${group.label}:$occurrence")
            })
        }.distinct().map { entry ->
            val occurrence = usedKeys.getOrDefault(entry.id, 0)
            usedKeys[entry.id] = occurrence + 1
            if (occurrence == 0) entry else entry.copy(id = "${entry.id}:variant-$occurrence")
        }
        if (issues.isNotEmpty()) WikiParseLogger.warnMalformed("ExtractionCatalog.${kind.name}", issues.take(8).joinToString("; "), html)
        WikiParseLogger.finishList("ExtractionCatalog.${kind.name}", unique, html)
        return ExtractionParseResult(unique, issues)
    }
}
