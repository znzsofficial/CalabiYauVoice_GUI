package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionDropItem
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionEntry

/** Evidence for the same searchable values as ExtractionEntry.matches; no inferred item membership. */
internal data class ExtractionDropMatchEvidence(
    val label: String,
    val snippet: String,
    val items: List<ExtractionDropItem> = emptyList()
)

internal fun extractionDropMatchEvidence(entry: ExtractionEntry, query: String): ExtractionDropMatchEvidence? {
    val keyword = query.trim().takeIf { it.isNotEmpty() } ?: return null
    val items = entry.dropPoolGroups.flatMap { group ->
        group.items.filter { it.contains(keyword, ignoreCase = true) }
            .map { ExtractionDropItem(it, group.label, group.qualityCode) }
    }.distinctBy { it.key }
    if (items.isNotEmpty()) return ExtractionDropMatchEvidence(
        "包含匹配物品", items.take(3).joinToString("、") { "${it.name}（${it.qualityLabel}）" } +
            if (items.size > 3) " 等 ${items.size} 项" else "", items
    )
    val values = listOf("来源名称匹配" to entry.name) +
        entry.dropPoolGroups.map { "池品质匹配" to it.label } +
        entry.fields.map { "${it.label}匹配" to it.value } +
        listOf("类别匹配" to entry.category) + entry.tags.map { "标签匹配" to it } +
        listOf("说明匹配" to entry.description)
    val (label, value) = values.firstOrNull { it.second.contains(keyword, ignoreCase = true) } ?: return null
    val matchStart = value.indexOf(keyword, ignoreCase = true)
    val start = (matchStart - 20).coerceAtLeast(0)
    val end = (matchStart + keyword.length + 40).coerceAtMost(value.length)
    return ExtractionDropMatchEvidence(label,
        (if (start > 0) "…" else "") + value.substring(start, end) + if (end < value.length) "…" else "")
}
