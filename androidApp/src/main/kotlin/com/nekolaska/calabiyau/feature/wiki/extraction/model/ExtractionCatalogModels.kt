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

/** The source may use codes beyond Q1–Q5; keep its label rather than inventing a rarity name. */
data class ExtractionDropPoolGroup(val label: String, val items: List<String>, val qualityCode: Int? = null)

enum class ExtractionDropView(val title: String) {
    SOURCES("掉落来源"), POOLS("完整掉落池")
}

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
    val numericValues: Map<ExtractionSort, Double> = emptyMap(),
    val dropPoolGroups: List<ExtractionDropPoolGroup> = emptyList()
) {
    val isDropPool: Boolean get() = fields.any { it.label == "掉落池条目（完整）" }
    val filterCategory: String get() = if (isDropPool) {
        fields.firstOrNull { it.label == "来源类别" }?.value?.takeIf { it.isNotBlank() } ?: category
    } else category

    fun matches(query: String): Boolean = query.isBlank() ||
        (listOf(name, category, description) + tags + fields.map { it.value }).any { it.contains(query.trim(), true) }
}

internal fun extractionEntriesForView(entries: List<ExtractionEntry>, view: ExtractionDropView): List<ExtractionEntry> =
    entries.filter { it.isDropPool == (view == ExtractionDropView.POOLS) }

internal fun filterDropPoolGroups(groups: List<ExtractionDropPoolGroup>, query: String): List<ExtractionDropPoolGroup> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return groups
    return groups.mapNotNull { group ->
        val items = if (group.label.contains(keyword, true)) group.items
            else group.items.filter { it.contains(keyword, true) }
        items.takeIf { it.isNotEmpty() }?.let { group.copy(items = it) }
    }
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
        it.matches(query) && (category == null || it.filterCategory == category) &&
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
