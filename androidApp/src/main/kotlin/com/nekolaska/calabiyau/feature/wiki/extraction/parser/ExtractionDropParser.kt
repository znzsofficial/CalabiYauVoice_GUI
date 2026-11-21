package com.nekolaska.calabiyau.feature.wiki.extraction.parser

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import org.jsoup.nodes.Element

/** Adapters for source cards, modern shared pools, and historical summary tables. */
internal object ExtractionDropParser {
    fun parse(root: Element, issues: MutableList<String>): List<ExtractionEntry> {
        val records = mutableListOf<DropParsedRecord>()
        var scope = 0
        var section = "掉落"
        var note = ""
        var sectionIntro = ""
        var subsectionIntro = ""
        var stageRules = ""
        val declaredStages = mutableMapOf<Int, Int>()
        val headings = mutableMapOf(scope to section)
        val sectionIntros = mutableMapOf<Int, String>()
        // Earlier summary-table pages repeat entire grids (with prose only on the second copy).
        val sectionedPools = root.selectFirst(".sec-drop-pool") != null
        root.select("h2, h3, .sec-drop-note, .sec-drop-card, .sec-drop-pool, table").forEach { element ->
            when {
                element.tagName() == "h2" -> {
                    scope++
                    section = content(element.selectFirst(".mw-headline") ?: element).ifBlank { "掉落" }
                    headings[scope] = section
                    note = ""
                    val intro = followingParagraph(element)
                    sectionIntros[scope] = intro
                    sectionIntro = if (sectionedPools) intro else ""
                    subsectionIntro = ""
                    stageRules = ""
                    if (section in setOf("首领战掉落 · 阶段递进", "Boss 战掉落 · 阶段递进")) {
                        // The threshold/difficulty qualification can follow the shared pool, not the table.
                        stageRules = generateSequence(element.nextElementSibling()) { it.nextElementSibling() }
                            .takeWhile { it.tagName() != "h2" }.filter { it.tagName() == "p" }
                            .map { content(it) }.filter { it.startsWith("「阈值」为首领战的阶段推进值") }.joinToString("\n")
                        Regex("""(\d+)\s*个阶段的物品与件数完全相同""").find(intro)?.groupValues?.get(1)
                            ?.toIntOrNull()?.let { declaredStages[scope] = it }
                    }
                }
                element.tagName() == "h3" -> subsectionIntro = if (sectionedPools) followingParagraph(element) else ""
                element.hasClass("sec-drop-note") -> note = content(element)
                element.hasClass("sec-drop-card") -> {
                    val card = parseCard(element, section, note)
                    if (card == null) issues += "drop-card: missing name" else records += DropParsedRecord(element,
                        card.copy(sectionIntroduction = paragraphs(sectionIntro, subsectionIntro, stageRules)), scope)
                }
                element.hasClass("sec-drop-pool") -> parsePool(element, section, note, issues)?.let {
                    records += DropParsedRecord(element, it.copy(description = paragraphs(sectionIntro, subsectionIntro, stageRules)), scope)
                }
                element.tagName() == "table" -> ExtractionDropTableParser.parse(element, issues).forEach {
                    val entry = if (it.type != ExtractionEntryType.DROP_STAGE) it else {
                        val block = element.parent()?.takeIf { parent -> parent.tagName() == "div" && parent.childrenSize() == 1 } ?: element
                        val preface = content(block.previousElementSibling()?.takeIf { sibling -> sibling.tagName() == "p" })
                        it.copy(description = paragraphs(sectionIntro, subsectionIntro, preface, stageRules))
                    }
                    records += DropParsedRecord(element, entry, scope)
                }
            }
        }
        // An entire missing row/table is still a partial response when the page announces N stages.
        declaredStages.forEach { (sectionScope, count) ->
            val inSection = records.filter { it.scope == sectionScope }.map { it.entry }
            for (type in listOf(ExtractionEntryType.DROP_SOURCE, ExtractionEntryType.DROP_STAGE)) {
                val numbers = inSection.filter { it.type == type }.map { it.name.substringBefore(" · ").removePrefix("阶段 ").toIntOrNull() }
                if (count < 1 || numbers.size != count || numbers.distinct().size != count || numbers.any { it == null || it !in 1..count }) {
                    issues += "drop-stage: expected $count distinct $type records"
                }
            }
        }
        return linkDropSharedPools(records, headings).also { linked ->
            validateDropCatalog(root, headings, sectionIntros, declaredStages.keys, records, linked, issues)
        }
    }

    private fun followingParagraph(heading: Element) = content(heading.nextElementSibling()?.takeIf { it.tagName() == "p" })
    private fun paragraphs(vararg parts: String) = parts.filter { it.isNotBlank() }.distinct().joinToString("\n")

    private fun parseCard(card: Element, section: String, note: String): ExtractionEntry? {
        val name = content(card.selectFirst(".sec-drop-card__title"))
        if (name.isBlank()) return null
        val subtitle = content(card.selectFirst(".sec-drop-card__sub"))
        val badge = content(card.selectFirst(".sec-drop-card__tier"))
        val fields = buildList<ExtractionField> {
            if (subtitle.isNotBlank()) add(ExtractionField("来源信息", subtitle))
            // New Boss cards reuse __must for weighted material draws. CSS is not semantics.
            card.select(".sec-drop-card__must, .sec-drop-card__list, .sec-drop-card__poolsum, .sec-drop-card__foot").forEach { element ->
                val value = content(element, "a.new[title^=文件:]") // Unuploaded icon, not part of the item name.
                val sourceLabel = content(generateSequence(element.previousElementSibling()) { it.previousElementSibling() }
                    .takeWhile { !it.hasClass("sec-drop-card__must") && !it.hasClass("sec-drop-card__list") }
                    .firstOrNull { it.hasClass("sec-drop-card__label") })
                val label = when {
                    element.hasClass("sec-drop-card__must") -> if (sourceLabel == "必掉") "必掉物" else sourceLabel.ifBlank { "掉落物品" }
                    element.hasClass("sec-drop-card__list") -> sourceLabel.ifBlank { "掉落池内最高权重" }
                    element.hasClass("sec-drop-card__poolsum") -> "掉落池构成"
                    else -> if (value.startsWith("与 ") && value.endsWith("共用同一掉落池")) "共用说明" else "出现方式"
                }
                if (value.isNotBlank()) add(ExtractionField(label, value))
            }
            card.select(".sec-drop-card__meta-item").forEach { meta ->
                val label = content(meta.selectFirst(".sec-drop-card__meta-label"))
                val value = content(meta.selectFirst(".sec-drop-card__meta-val"))
                if (label.isNotBlank() && value.isNotBlank()) add(ExtractionField(label, value))
            }
            if (none { it.label == "掉落件数" } && tierPoolDeclaration.containsMatchIn(note)) {
                Regex("""(?:^|[。\n])\s*件数\s*[：:]\s*([^。\n]+)""").findAll(note).singleOrNull()?.let {
                    add(ExtractionField("掉落件数", it.groupValues[1].trim()))
                }
            }
            if (note.isNotBlank()) add(ExtractionField("本节掉落说明", note))
        }
        return ExtractionEntry(
            id = "$section:$name:$badge:$subtitle", name = name, category = section,
            fields = fields, tags = listOf(badge, content(card.selectFirst(".sec-drop-card__grade"))).filter { it.isNotBlank() },
            type = ExtractionEntryType.DROP_SOURCE,
            dropCountState = dropCountState(fields.singleOrNull { it.label == "掉落件数" }?.value.orEmpty()),
            containerId = Regex("""晶骸(?:容器|核心)\s+(\d+)""")
                .findAll(content(card.selectFirst(".sec-drop-card__foot"))).map { it.groupValues[1] }
                .distinct().toList().singleOrNull(),
            dropImages = card.select(".sec-drop-card__list li, .sec-drop-card__must li")
                .mapNotNull(::parseWeightedItemImage).distinct()
        )
    }

    private fun parsePool(pool: Element, section: String, note: String, issues: MutableList<String>): ExtractionEntry? {
        val heading = pool.selectFirst(".sec-drop-pool__head")
        val title = dropPoolTitle(heading)
        if (title.isBlank()) {
            issues += "shared-pool: missing heading"
            return null
        }
        val name = if (title == "本档共用掉落池") "$section · 共用掉落池" else title
        val groups = mutableListOf<ExtractionDropPoolGroup>()
        val images = mutableListOf<ExtractionDropImage>()
        var label: String? = null
        var expectedCount: Int? = null
        // Includes collapsed descendants: the server HTML already contains their chips.
        pool.select(".sec-drop-pool__q, .sec-drop-pool__chips").filter { it.closest(".sec-drop-pool") === pool }.forEach { element ->
            if (element.hasClass("sec-drop-pool__q")) {
                if (label != null) issues += "shared-pool $name: group without items"
                label = content(element.selectFirst(".sec-drop-pool__tag")).ifBlank { null }
                val declared = element.selectFirst(".sec-drop-pool__cnt")
                expectedCount = count(content(declared))
                if (declared != null && expectedCount == null) issues += "shared-pool $name: invalid group count"
                if (label == null) issues += "shared-pool $name: missing group label"
            } else {
                val items = element.select(".sec-drop-chip").map { content(it) }.filter { it.isNotBlank() }
                if (label == null) issues += "shared-pool $name: orphan chips"
                else {
                    if ((items.isEmpty() && expectedCount != 0) || (expectedCount != null && expectedCount != items.size)) {
                        issues += "shared-pool $name: group count mismatch"
                    }
                    groups += ExtractionDropPoolGroup(label, items, qualityCode(label))
                    element.select(".sec-drop-chip").forEach { chip ->
                        val itemName = content(chip)
                        if (itemName.isNotBlank()) itemImageUrl(chip)?.let {
                            images += ExtractionDropImage(itemName, label, it)
                        }
                    }
                }
                label = null
                expectedCount = null
            }
        }
        if (label != null) issues += "shared-pool $name: group without items"
        val declaredCount = count(content(heading?.selectFirst("b")))
        if (heading?.selectFirst("b") != null && declaredCount == null) issues += "shared-pool $name: invalid total count"
        if ((groups.isEmpty() && declaredCount != 0) || (declaredCount != null && declaredCount != groups.sumOf { it.items.size })) {
            issues += "shared-pool $name: total count mismatch"
        }
        val fields = buildList {
            content(heading?.selectFirst("b")).takeIf { it.isNotBlank() }?.let { add(ExtractionField("池规模", it)) }
            content(heading?.selectFirst(".sec-drop-pool__note")).takeIf { it.isNotBlank() }?.let { add(ExtractionField("物品池说明", it)) }
            if (note.isNotBlank()) add(ExtractionField("本节掉落说明", note))
        }
        return ExtractionEntry(
            id = "pool:${pool.id().ifBlank { "$section:$title" }}", name = name,
            category = section, fields = fields, dropPoolGroups = groups,
            type = ExtractionEntryType.DROP_POOL, sourceCategory = section, dropImages = images.distinct()
        )
    }

    private fun count(value: String): Int? = Regex("""^(\d+)\s*项$""").matchEntire(value)?.groupValues?.get(1)?.toIntOrNull()

    /** The snapshot binds each icon to a single weighted list row; alt is a filename, not an item name. */
    private fun parseWeightedItemImage(row: Element): ExtractionDropImage? {
        if (row.select("li").any { it !== row }) return null
        val url = itemImageUrl(row) ?: return null
        val text = row.text().trim()
        val weighted = Regex("""^(.+?)\s*·\s*\d+(?:\.\d+)?%$""").matchEntire(text) ?: return null
        val rawName = weighted.groupValues[1].trim()
        val quality = Regex("""^(.+?)（([★]{1,5})\s*(优秀|精致|卓越|完美|臻藏)）$""").matchEntire(rawName)
        val name = quality?.groupValues?.get(1)?.trim() ?: rawName
        // Unknown annotations or multiple inline items are not a reliable name binding.
        if (name.isBlank() || name.any { it in "（）×｜" }) return null
        val qualityLabel = quality?.let {
            val code = it.groupValues[2].length
            val label = it.groupValues[3]
            if (listOf("优秀", "精致", "卓越", "完美", "臻藏")[code - 1] != label) return null
            "Q$code $label"
        }
        return ExtractionDropImage(name, qualityLabel, url)
    }

    private fun itemImageUrl(element: Element): String? {
        val image = element.select("img").singleOrNull() ?: return null
        return listOf(image.attr("data-src"), image.attr("src"), image.attr("srcset").substringBefore(',').trim().substringBefore(' '))
            .firstNotNullOfOrNull { raw ->
                raw.takeIf { it.isNotBlank() && !it.startsWith("data:") }
                    ?.let(WikiImageUrls::originalFromThumbnail)
                    ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            }
    }
}
