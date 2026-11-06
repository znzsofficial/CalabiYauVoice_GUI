package com.nekolaska.calabiyau.feature.wiki.extraction.parser

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import org.jsoup.nodes.Element

/** Adapters for source cards, modern shared pools, and historical summary tables. */
internal object ExtractionDropParser {
    fun parse(root: Element, issues: MutableList<String>): List<ExtractionEntry> {
        val entries = mutableListOf<ExtractionEntry>()
        var section = "掉落"
        var note = ""
        root.select("h2, .sec-drop-note, .sec-drop-card, .sec-drop-pool, table").forEach { element ->
            when {
                element.tagName() == "h2" -> {
                    section = content(element.selectFirst(".mw-headline") ?: element).ifBlank { "掉落" }
                    note = ""
                }
                element.hasClass("sec-drop-note") -> note = content(element)
                element.hasClass("sec-drop-card") -> {
                    val card = parseCard(element, section, note)
                    if (card == null) issues += "drop-card: missing name" else entries += card
                }
                element.hasClass("sec-drop-pool") -> parsePool(element, section, note, issues)?.let { entries += it }
                element.tagName() == "table" -> entries += ExtractionDropTableParser.parse(element, issues)
            }
        }
        return entries
    }

    private fun parseCard(card: Element, section: String, note: String): ExtractionEntry? {
        val name = content(card.selectFirst(".sec-drop-card__title"))
        if (name.isBlank()) return null
        val subtitle = content(card.selectFirst(".sec-drop-card__sub"))
        val badge = content(card.selectFirst(".sec-drop-card__tier"))
        val fields = buildList {
            if (subtitle.isNotBlank()) add(ExtractionField("来源信息", subtitle))
            // New Boss cards reuse __must for weighted material draws. CSS is not semantics.
            card.select(".sec-drop-card__must, .sec-drop-card__list, .sec-drop-card__poolsum, .sec-drop-card__foot").forEach { element ->
                val value = content(element)
                val sourceLabel = content(generateSequence(element.previousElementSibling()) { it.previousElementSibling() }
                    .firstOrNull { it.hasClass("sec-drop-card__label") })
                val label = when {
                    element.hasClass("sec-drop-card__must") -> sourceLabel.takeIf { it.isNotBlank() && it != "必掉" } ?: "必掉物"
                    element.hasClass("sec-drop-card__list") -> sourceLabel.ifBlank { "掉落池内最高权重" }
                    element.hasClass("sec-drop-card__poolsum") -> "掉落池构成"
                    else -> "出现方式"
                }
                if (value.isNotBlank()) add(ExtractionField(label, value))
            }
            card.select(".sec-drop-card__meta-item").forEach { meta ->
                val label = content(meta.selectFirst(".sec-drop-card__meta-label"))
                val value = content(meta.selectFirst(".sec-drop-card__meta-val"))
                if (label.isNotBlank() && value.isNotBlank()) add(ExtractionField(label, value))
            }
            if (note.isNotBlank()) add(ExtractionField("本节掉落说明", note))
        }
        return ExtractionEntry(
            id = "$section:$name:$badge:$subtitle", name = name, category = section,
            fields = fields, tags = listOf(badge, content(card.selectFirst(".sec-drop-card__grade"))).filter { it.isNotBlank() },
            type = ExtractionEntryType.DROP_SOURCE,
            containerId = Regex("""晶骸(?:容器|核心)\s+(\d+)""")
                .findAll(content(card.selectFirst(".sec-drop-card__foot"))).map { it.groupValues[1] }
                .distinct().toList().singleOrNull()
        )
    }

    private fun parsePool(pool: Element, section: String, note: String, issues: MutableList<String>): ExtractionEntry? {
        val heading = pool.selectFirst(".sec-drop-pool__head")
        val title = content(heading, "b, .sec-drop-pool__note")
        if (title.isBlank()) {
            issues += "shared-pool: missing heading"
            return null
        }
        val name = if (title == "本档共用掉落池") "$section · 共用掉落池" else title
        val groups = mutableListOf<ExtractionDropPoolGroup>()
        var label: String? = null
        var expectedCount: Int? = null
        // Includes collapsed descendants: the server HTML already contains their chips.
        pool.select(".sec-drop-pool__q, .sec-drop-pool__chips").filter { it.closest(".sec-drop-pool") === pool }.forEach { element ->
            if (element.hasClass("sec-drop-pool__q")) {
                if (label != null) issues += "shared-pool $name: group without items"
                label = content(element.selectFirst(".sec-drop-pool__tag")).ifBlank { null }
                expectedCount = count(content(element.selectFirst(".sec-drop-pool__cnt")))
                if (label == null) issues += "shared-pool $name: missing group label"
            } else {
                val items = element.select(".sec-drop-chip").map { content(it) }.filter { it.isNotBlank() }
                if (label == null) issues += "shared-pool $name: orphan chips"
                else {
                    if ((items.isEmpty() && expectedCount != 0) || (expectedCount != null && expectedCount != items.size)) {
                        issues += "shared-pool $name: group count mismatch"
                    }
                    groups += ExtractionDropPoolGroup(label, items, qualityCode(label))
                }
                label = null
                expectedCount = null
            }
        }
        if (label != null) issues += "shared-pool $name: group without items"
        val declaredCount = count(content(heading?.selectFirst("b")))
        if ((groups.isEmpty() && declaredCount != 0) || (declaredCount != null && declaredCount != groups.sumOf { it.items.size })) {
            issues += "shared-pool $name: total count mismatch"
        }
        val fields = buildList {
            content(heading?.selectFirst("b")).takeIf { it.isNotBlank() }?.let { add(ExtractionField("池规模", it)) }
            content(heading?.selectFirst(".sec-drop-pool__note")).takeIf { it.isNotBlank() }?.let { add(ExtractionField("池内顺序", it)) }
            if (note.isNotBlank()) add(ExtractionField("本节掉落说明", note))
        }
        return ExtractionEntry(
            id = "pool:${pool.id().ifBlank { "$section:$title" }}", name = name,
            category = section, fields = fields, dropPoolGroups = groups,
            type = ExtractionEntryType.DROP_POOL, sourceCategory = section
        )
    }

    private fun count(value: String): Int? = Regex("""^(\d+)\s*项$""").matchEntire(value)?.groupValues?.get(1)?.toIntOrNull()
}
