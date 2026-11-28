package com.nekolaska.calabiyau.feature.wiki.extraction.parser

import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import org.jsoup.nodes.Element

internal object ExtractionItemParser {
    private val gridSize = Regex("""\d+\s*[×xX]\s*\d+""")

    fun parse(card: Element, prefix: String, qualities: Map<Int, String>, legacyLoot: Boolean): ExtractionEntry? {
        val name = card.selectFirst(".$prefix-name")?.text()?.trim().orEmpty()
        if (name.isBlank()) return null
        val rawId = card.attr("data-sort-id").trim()
        val category = card.attr("data-param1").trim()
        val quality = (card.attr("data-quality").ifBlank { card.attr("data-param2") }).toIntOrNull()
        val description = itemProse(card.selectFirst(".$prefix-desc"), ".$prefix-title")
        val fields = buildList {
            content(card.selectFirst(".$prefix-value")).takeIf { it.isNotBlank() }?.let { add(ExtractionField("价值", it.removePrefix("价值").trim())) }
            itemProse(card.selectFirst(".$prefix-detail"), ".$prefix-title").takeIf { it.isNotBlank() }?.let { add(ExtractionField("实际效果", it)) }
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
                        gridSize.matches(text) -> "占格"
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
        val chips = card.select(".$prefix-tag")
        val paramTags = card.attr("data-param3").split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }
        val materialTags = when {
            prefix != "sec-card" -> emptyList()
            !legacyLoot -> paramTags.distinct()
            else -> (paramTags + chips.filter { !it.hasAttr("data-tag") || it.attr("data-tag") == "贵重" }
                .filterNot { it.hasClass("sec-qtag") }.flatMap { splitTags(it.text()) }.filter { it in knownMaterialTags }).distinct()
        }
        // The new template prints comma-separated material names as one caption with ideographic commas.
        // Expand just that chip; keep punctuation inside usage/trade labels as authored.
        val tags = chips.flatMap { chip ->
            val label = chip.text().trim()
            val parts = splitTags(label)
            if (!chip.hasAttr("data-tag") && parts.size > 1 && parts.all { it in materialTags }) parts
            else listOf(label)
        }.filter { it.isNotBlank() } + paramTags
        val imageUrl = card.selectFirst(".$prefix-img img")?.let {
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
            materialTags = materialTags, qualityLabel = qualities[quality]?.takeIf { it.isNotBlank() } ?: when (quality) {
                1 -> "优秀"; 2 -> "精致"; 3 -> "卓越"; 4 -> "完美"; 5 -> "臻藏"
                null -> ""; else -> "Q$quality"
            }, imageUrl = imageUrl, description = description, fields = fields, tags = tags.distinct(),
            numericValues = ExtractionSort.entries.filter { it != ExtractionSort.DEFAULT }.mapNotNull { sort ->
                card.attr("data-sort-${sort.attribute}").toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.let { sort to it }
            }.toMap()
        )
    }

    private fun splitTags(value: String) = value.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
}
