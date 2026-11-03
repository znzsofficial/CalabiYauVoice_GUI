package com.nekolaska.calabiyau.feature.wiki.extraction.parser

import com.nekolaska.calabiyau.core.wiki.HtmlText
import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object ExtractionCatalogParsers {
    fun parse(html: String, kind: ExtractionCatalogKind): List<ExtractionEntry> {
        val document = Jsoup.parse(html)
        val entries = if (kind == ExtractionCatalogKind.DROPS) {
            var section = "掉落"
            val rows = mutableListOf<ExtractionEntry>()
            document.select("h2, .sec-drop-card").forEach { element ->
                if (element.tagName() == "h2") {
                    section = element.selectFirst(".mw-headline")?.text()?.trim()
                        ?: element.ownText().trim()
                } else parseDrop(element, section)?.let { rows += it }
            }
            // The summary table contains full pools, while cards only show the top weights.
            document.select("table.klbqtable").forEach { table -> rows += parseDropSummary(table) }
            rows
        } else {
            val prefix = if (kind == ExtractionCatalogKind.LOOT) "sec-card" else "sec-att-card"
            val qualityNames = document.select(".cardSelectOption[data-group=2]").mapNotNull {
                val level = it.attr("data-option").substringAfter('|').toIntOrNull() ?: return@mapNotNull null
                level to it.text().trim()
            }.toMap()
            document.select(".gallerygrid-item.$prefix").mapNotNull { parseItem(it, prefix, qualityNames) }
        }
        // Do not merge same-name items/variants or monsters sharing a drop pool.
        val usedKeys = mutableMapOf<String, Int>()
        val unique = entries.distinct().map { entry ->
            val occurrence = usedKeys.getOrDefault(entry.id, 0)
            usedKeys[entry.id] = occurrence + 1
            if (occurrence == 0) entry else entry.copy(id = "${entry.id}:variant-$occurrence")
        }
        return WikiParseLogger.finishList("ExtractionCatalog.${kind.name}", unique, html)
    }

    private fun parseItem(card: Element, prefix: String, qualities: Map<Int, String>): ExtractionEntry? {
        val name = card.selectFirst(".$prefix-name")?.text()?.trim().orEmpty()
        if (name.isBlank()) return null
        val rawId = card.attr("data-sort-id").trim()
        val category = card.attr("data-param1").trim()
        val quality = (card.attr("data-quality").ifBlank { card.attr("data-param2") }).toIntOrNull()
        val description = content(card.selectFirst(".$prefix-desc"), ".$prefix-title")
        val fields = buildList {
            content(card.selectFirst(".$prefix-value")).takeIf { it.isNotBlank() }?.let { add(ExtractionField("价值", it.removePrefix("价值").trim())) }
            content(card.selectFirst(".$prefix-detail"), ".$prefix-title").takeIf { it.isNotBlank() }?.let { add(ExtractionField("实际效果", it)) }
            content(card.selectFirst(".$prefix-compat")).takeIf { it.isNotBlank() }?.let { add(ExtractionField("适用", it.removePrefix("适用").trim())) }
            card.select(".$prefix-stats > span").forEach {
                val text = content(it)
                if (text.isNotBlank()) {
                    val label = when {
                        it.hasClass("$prefix-stats-fx") -> "使用效果"
                        text.startsWith("重量") -> "重量"
                        text.startsWith("售价") -> "售价"
                        text.startsWith("搜刮") -> "搜刮耗时"
                        text.startsWith("可叠") -> "叠加上限"
                        text.matches(Regex("\\d+\\s*[×xX]\\s*\\d+")) -> "占格"
                        else -> "属性"
                    }
                    val value = if (label in setOf("重量", "售价")) text.removePrefix(label).trim()
                        else if (label == "搜刮耗时") text.removePrefix("搜刮").trim()
                        else if (label == "叠加上限") text.removePrefix("可叠").trim() else text
                    add(ExtractionField(label, value))
                }
            }
            content(card.selectFirst(".$prefix-loot")).takeIf { it.isNotBlank() }?.let { add(ExtractionField("掉落信息", it)) }
        }
        val tags = card.select(".$prefix-tag").map { it.text().trim() }.filter { it.isNotBlank() } +
            card.attr("data-param3").split(',', '，').map { it.trim() }.filter { it.isNotBlank() }
        val image = card.selectFirst(".$prefix-img img")
        val imageUrl = image?.let {
            listOf(it.attr("data-src"), it.attr("src"), it.attr("srcset").substringBefore(',').trim().substringBefore(' '))
                .firstNotNullOfOrNull { candidate ->
                    candidate.takeIf { it.isNotBlank() && !it.startsWith("data:") }
                        ?.let(WikiImageUrls::originalFromThumbnail)
                        ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                }
        }
        return ExtractionEntry(
            id = rawId.ifBlank { "$prefix:$category:$quality:$name:${fields.hashCode()}" },
            name = name, category = category.ifBlank { "未分类" }, quality = quality,
            qualityLabel = qualities[quality] ?: when (quality) {
                1 -> "优秀"; 2 -> "精致"; 3 -> "卓越"; 4 -> "完美"; 5 -> "臻藏"
                else -> ""
            }, imageUrl = imageUrl, description = description, fields = fields, tags = tags.distinct(),
            numericValues = ExtractionSort.entries.filter { it != ExtractionSort.DEFAULT }.mapNotNull { sort ->
                card.attr("data-sort-${sort.attribute}").toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.let { sort to it }
            }.toMap()
        )
    }

    private fun parseDrop(card: Element, section: String): ExtractionEntry? {
        val name = content(card.selectFirst(".sec-drop-card__title"))
        if (name.isBlank()) return null
        val subtitle = content(card.selectFirst(".sec-drop-card__sub"))
        val badge = content(card.selectFirst(".sec-drop-card__tier"))
        val fields = buildList {
            if (subtitle.isNotBlank()) add(ExtractionField("容器信息", subtitle))
            for ((selector, label) in listOf(
                ".sec-drop-card__must" to "必掉物", ".sec-drop-card__list" to "掉落池内最高权重",
                ".sec-drop-card__poolsum" to "掉落池构成", ".sec-drop-card__foot" to "出现方式"
            )) content(card.selectFirst(selector)).takeIf { it.isNotBlank() }?.let { add(ExtractionField(label, it)) }
            card.select(".sec-drop-card__meta-item").forEach { meta ->
                val label = content(meta.selectFirst(".sec-drop-card__meta-label"))
                val value = content(meta.selectFirst(".sec-drop-card__meta-val"))
                if (label.isNotBlank() && value.isNotBlank()) add(ExtractionField(label, value))
            }
        }
        return ExtractionEntry(
            id = "$section:$name:$badge:$subtitle", name = name, category = section.ifBlank { "掉落" },
            fields = fields, tags = listOf(badge).filter { it.isNotBlank() }
            // data-tier is monster/container difficulty, NOT item rarity; no fake quality/image.
        )
    }

    private fun parseDropSummary(table: Element): List<ExtractionEntry> {
        val rows = table.select("tr").filter { it.closest("table") === table }
        val headers = rows.firstOrNull()?.select("> th, > td")?.map { content(it) }.orEmpty()
        val poolIndex = headers.indexOfFirst { it == "掉落池条目（完整）" || it == "掉落池条目（按稀有度分行）" }
        if (headers.firstOrNull() != "名称" || poolIndex < 0) return emptyList()
        val groupedByRarity = headers[poolIndex] == "掉落池条目（按稀有度分行）"
        val spans = arrayOfNulls<DropTableSpan>(headers.size)
        val groups = mutableListOf<DropTableGroup>()
        rows.drop(1).forEach { row ->
            // Expand merged cells before reading column positions. Carry only for the
            // declared rowspan, and group by the original name cell, not by name/tier:
            // several different Boss cores deliberately share both of those values.
            val expanded = MutableList<Element?>(headers.size) { index ->
                spans[index]?.let { span ->
                    if (--span.remaining == 0) spans[index] = null
                    span.element
                }
            }
            var column = 0
            var invalid = false
            row.select("> th, > td").forEach { cell ->
                while (column < expanded.size && expanded[column] != null) column++
                val width = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                val height = cell.attr("rowspan").toIntOrNull()?.coerceIn(1, rows.size) ?: 1
                if (column >= expanded.size || width > expanded.size - column ||
                    (column until column + width).any { expanded[it] != null }) {
                    invalid = true
                } else {
                    repeat(width) {
                        expanded[column] = cell
                        if (height > 1) spans[column] = DropTableSpan(cell, height - 1)
                        column++
                    }
                }
            }
            if (invalid || expanded.any { it == null }) return@forEach
            val nameCell = expanded.first()!!
            val pool = content(expanded[poolIndex])
            val previous = groups.lastOrNull()
            if (previous != null && previous.nameCell === nameCell) {
                previous.poolRows += pool
            } else {
                groups += DropTableGroup(nameCell, expanded.map { content(it) }, mutableListOf(pool))
            }
        }
        return groups.mapNotNull { group ->
            val values = group.values.toMutableList().apply { this[poolIndex] = group.poolRows.joinToString("\n") }
            val name = values.first()
            if (name.isBlank()) return@mapNotNull null
            val fields = headers.zip(values).drop(1).mapIndexedNotNull { index, (header, value) ->
                val label = if (index + 1 == poolIndex) "掉落池条目（完整）" else header
                value.takeIf { it.isNotBlank() }?.let { ExtractionField(label, it) }
            }
            val type = values.getOrNull(headers.indexOf("来源类别")).orEmpty()
            val subtype = values.getOrNull(headers.indexOf("子类")).orEmpty()
            val tier = values.getOrNull(headers.indexOf("掉落档次")).orEmpty()
            ExtractionEntry(id = "summary:$name:$type:$subtype:$tier", name = name,
                category = "总览表 · 完整掉落池", fields = fields,
                tags = listOf(type, subtype, tier.takeIf { it.isNotBlank() }?.let { "档次 $it" }.orEmpty()).filter { it.isNotBlank() },
                dropPoolGroups = if (groupedByRarity) group.poolRows.mapNotNull(::parseDropPoolGroup)
                    else parseLegacyDropPool(values[poolIndex]))
        }
    }

    private class DropTableSpan(val element: Element, var remaining: Int)
    private class DropTableGroup(val nameCell: Element, val values: List<String>, val poolRows: MutableList<String>)

    private fun parseDropPoolGroup(value: String): ExtractionDropPoolGroup? {
        if (value.isBlank() || value in listOf("—", "—：—", "—:—")) return null
        val separator = value.indexOfFirst { it == '：' || it == ':' }
        val label = value.takeIf { separator > 0 }?.substring(0, separator)?.trim().orEmpty()
        val code = Regex("""^Q(\d+)(?:\s+.*)?$""").matchEntire(label)?.groupValues?.get(1)?.toIntOrNull()
        if (code == null) return ExtractionDropPoolGroup("掉落池", listOf(value))
        val items = value.substring(separator + 1).split('、').map { it.trim() }.filter { it.isNotBlank() }
        return ExtractionDropPoolGroup(label, items, code)
    }

    private fun parseLegacyDropPool(value: String): List<ExtractionDropPoolGroup> {
        val items = Regex("""^\d+\s*项[：:](.*)$""", RegexOption.DOT_MATCHES_ALL).matchEntire(value.trim())
            ?.groupValues?.get(1)?.split('、')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        // Legacy full pools have no per-item rarity; retain their order and duplicates.
        return if (items.isEmpty()) emptyList() else listOf(ExtractionDropPoolGroup("全部掉落物", items))
    }

    private fun content(element: Element?, remove: String? = null): String {
        if (element == null) return ""
        val copy = element.clone()
        copy.select("style, script, .smwttcontent, .smwtticon").remove()
        if (remove != null) copy.select(remove).remove()
        copy.select("li").forEach { it.prependText("• ") }
        val doc = Jsoup.parseBodyFragment(copy.html())
        doc.outputSettings().prettyPrint(false)
        return HtmlText.clean(doc.body().html())
    }
}
