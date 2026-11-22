package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.feature.wiki.extraction.model.DropArtworkIndex
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionDropItem
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionDropPoolGroup
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionEntry

/** Evidence for the same searchable values as ExtractionEntry.matches; no inferred item membership. */
internal data class ExtractionDropMatchEvidence(
    val label: String,
    val snippet: String,
    val items: List<ExtractionDropItem> = emptyList(),
    val isNameMatch: Boolean = false
) {
    // A matching name is already the title. A Wiki field may independently be labeled “名称”.
    val caption: String? get() = if (isNameMatch) null else "$label：$snippet"

    fun captionWithThumbnail(item: ExtractionDropItem?): String? =
        if (item == null || item == items.firstOrNull()) caption else "$label：${itemSnippet(items, item)}"
}

// Put the illustrated name and quality together first, even when only a later match has artwork.
private fun itemSnippet(items: List<ExtractionDropItem>, first: ExtractionDropItem = items.first()): String =
    (sequenceOf(first) + items.asSequence().filterNot { it == first }).take(3).joinToString("、") { "${it.name}（${it.qualityLabel}）" } +
        if (items.size > 3) " 等 ${items.size} 项" else ""

internal fun dropPreviewArtwork(query: String, evidence: ExtractionDropMatchEvidence?, groups: List<ExtractionDropPoolGroup>,
    artwork: DropArtworkIndex): Pair<ExtractionDropItem, String>? {
    if (artwork === DropArtworkIndex.Empty) return null
    if (query.isBlank()) return groups.firstNotNullOfOrNull(artwork::firstImageIn)
    return evidence?.items?.firstNotNullOfOrNull { item -> artwork.imageFor(item.name, item.qualityLabel)?.let { item to it } }
}

internal fun extractionDropMatchEvidence(entry: ExtractionEntry, query: String,
    groups: List<ExtractionDropPoolGroup> = entry.dropPoolGroups): ExtractionDropMatchEvidence? {
    val keyword = query.trim().takeIf { it.isNotEmpty() } ?: return null
    val items = groups.flatMap { group ->
        group.items.filter { it.contains(keyword, ignoreCase = true) }
            .map { ExtractionDropItem(it, group.label, group.qualityCode) }
    }.distinctBy { it.key }
    if (items.isNotEmpty()) return ExtractionDropMatchEvidence("候选物品", itemSnippet(items), items)
    fun excerpt(label: String, value: String, isName: Boolean = false): ExtractionDropMatchEvidence? {
        val matchStart = value.indexOf(keyword, ignoreCase = true)
        if (matchStart < 0) return null
        val start = (matchStart - 20).coerceAtLeast(0)
        val end = (matchStart + keyword.length + 40).coerceAtMost(value.length)
        return ExtractionDropMatchEvidence(label,
            (if (start > 0) "…" else "") + value.substring(start, end) + if (end < value.length) "…" else "", isNameMatch = isName)
    }
    excerpt("名称", entry.name, isName = true)?.let { return it }
    val values = groups.map { "物品品质" to it.label } +
        entry.fields.map { it.label to it.value } + listOf("分类" to entry.category) +
        entry.tags.map { "标签" to it } + listOf("说明" to entry.description)
    return values.firstNotNullOfOrNull { (label, value) -> excerpt(label, value) }
}
