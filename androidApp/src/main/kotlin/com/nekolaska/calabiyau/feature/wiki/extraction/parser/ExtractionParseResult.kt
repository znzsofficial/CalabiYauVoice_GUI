package com.nekolaska.calabiyau.feature.wiki.extraction.parser

import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionEntry

/** Diagnostics are structural, never inferred from a hard-coded number of game items. */
data class ExtractionParseResult(val entries: List<ExtractionEntry>, val issues: List<String>) {
    val canCache: Boolean get() = entries.isNotEmpty() && issues.isEmpty()
}
