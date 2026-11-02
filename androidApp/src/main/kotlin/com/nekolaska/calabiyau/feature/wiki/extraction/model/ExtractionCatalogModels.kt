package com.nekolaska.calabiyau.feature.wiki.extraction.model

const val EXTRACTION_MODE_PAGE = "战斗模式/弦溯战线"

enum class ExtractionCatalogKind(val title: String, val pageSuffix: String) {
    LOOT("战利品图鉴", "物品图鉴"),
    ATTACHMENTS("配件图鉴", "配件图鉴"),
    DROPS("掉落图鉴", "掉落图鉴");

    val pageName: String get() = "$EXTRACTION_MODE_PAGE/$pageSuffix"
    val cacheKey: String get() = "extraction_${name.lowercase()}_v1"
}

data class ExtractionField(val label: String, val value: String)

data class ExtractionEntry(
    val id: String,
    val name: String,
    val category: String,
    val quality: Int? = null,
    val qualityLabel: String = "",
    val imageUrl: String? = null,
    val description: String = "",
    val fields: List<ExtractionField> = emptyList(),
    val tags: List<String> = emptyList(),
    val numericValues: Map<ExtractionSort, Double> = emptyMap()
) {
    fun matches(query: String): Boolean = query.isBlank() ||
        (listOf(name, category, description) + tags + fields.map { it.value }).any { it.contains(query.trim(), true) }
}

enum class ExtractionSort(val label: String, val attribute: String) {
    DEFAULT("默认顺序", ""), VALUE("价值", "value"), RATE("出现率", "rate"),
    WEIGHT("重量", "weight"), SEARCH_TIME("搜刮耗时", "time")
}

/** Stable sort, with absent numbers always last rather than pretending unknown = zero. */
internal fun filterExtractionEntries(
    entries: List<ExtractionEntry>, query: String, category: String?, quality: Int?, valuableOnly: Boolean,
    sort: ExtractionSort, descending: Boolean
): List<ExtractionEntry> {
    val filtered = entries.filter {
        it.matches(query) && (category == null || it.category == category) &&
            (quality == null || it.quality == quality) && (!valuableOnly || "贵重" in it.tags)
    }
    if (sort == ExtractionSort.DEFAULT) return filtered
    return filtered.sortedWith { a, b ->
        val first = a.numericValues[sort]
        val second = b.numericValues[sort]
        when {
            first == null && second == null -> 0
            first == null -> 1
            second == null -> -1
            descending -> second.compareTo(first)
            else -> first.compareTo(second)
        }
    }
}
