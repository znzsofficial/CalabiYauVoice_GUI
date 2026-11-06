package com.nekolaska.calabiyau.feature.wiki.extraction.model

enum class DropCountState { POSITIVE, ZERO, UNKNOWN }

/** Validate the entire distribution before using it to exclude a source. No derived item rates. */
internal fun dropCountState(text: String): DropCountState {
    val parts = text.trim().split('、')
    val pattern = Regex("""(\d+)\s*件\s*(\d+(?:\.\d+)?)%""")
    val values = parts.map { part ->
        val match = pattern.matchEntire(part.trim()) ?: return DropCountState.UNKNOWN
        val count = match.groupValues[1].toIntOrNull() ?: return DropCountState.UNKNOWN
        val probability = match.groupValues[2].toDoubleOrNull() ?: return DropCountState.UNKNOWN
        if (probability !in 0.0..100.0) return DropCountState.UNKNOWN
        count to probability
    }
    if (values.map { it.first }.distinct().size != values.size ||
        kotlin.math.abs(values.sumOf { it.second } - 100.0) > 0.001) return DropCountState.UNKNOWN
    return if (values.any { it.first > 0 && it.second > 0 }) DropCountState.POSITIVE else DropCountState.ZERO
}

data class ExtractionDropItem(val name: String, val qualityLabel: String, val qualityCode: Int?) {
    val key: String get() = "${name.length}:$name:${qualityLabel.length}:$qualityLabel"
}

data class ExtractionDropHit(val item: ExtractionDropItem, val source: ExtractionEntry)

/** Name + source quality label is the strongest identity exposed by these HTML chips, not an item ID. */
internal fun buildDropLookup(containers: List<ExtractionEntry>): Map<ExtractionDropItem, List<ExtractionDropHit>> {
    val hits = linkedMapOf<ExtractionDropItem, MutableList<ExtractionDropHit>>()
    containers.forEach { source ->
        val seen = mutableSetOf<ExtractionDropItem>()
        source.dropPoolGroups.forEach { group ->
            group.items.forEach { name ->
                val item = ExtractionDropItem(name, group.label, group.qualityCode)
                if (seen.add(item)) hits.getOrPut(item) { mutableListOf() }.add(ExtractionDropHit(item, source))
            }
        }
    }
    return hits
}

/** Fail closed on ambiguous IDs. No names, row offsets, or numerical transformations participate. */
internal fun linkedDropContainer(source: ExtractionEntry, containers: List<ExtractionEntry>): ExtractionEntry? =
    source.containerId?.let { id -> containers.singleOrNull { it.containerId == id } }
