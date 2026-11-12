package com.nekolaska.calabiyau.feature.wiki.extraction.parser

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import org.jsoup.nodes.Element

internal data class DropParsedRecord(val element: Element, val entry: ExtractionEntry, val scope: Int)

/** November 10 removed card IDs. Use explicit pool declarations, never the numbers in pool anchors. */
internal fun linkDropSharedPools(records: List<DropParsedRecord>): List<ExtractionEntry> {
    val poolIdCounts = records.filter { it.entry.isDropPool }.groupingBy { it.entry.id }.eachCount()
    val byScope = records.groupBy { it.scope }
    return records.map { record ->
        val source = record.entry
        if (source.type != ExtractionEntryType.DROP_SOURCE) return@map source
        val scoped = byScope.getValue(record.scope)
        // A repeated name in one section cannot be disambiguated by a pool title.
        if (scoped.count { it.entry.type == ExtractionEntryType.DROP_SOURCE && it.entry.name == source.name } != 1)
            return@map source
        val candidates = scoped.filter { pool ->
            if (!pool.element.hasClass("sec-drop-pool") || poolIdCounts[pool.entry.id] != 1) return@filter false
            val title = content(pool.element.selectFirst(".sec-drop-pool__head"), "b, .sec-drop-pool__note")
            when {
                title == "本档共用掉落池" -> source.fields.any {
                    it.label == "本节掉落说明" && Regex("""^本档\s*\d+\s*只(?:晶源体|怪)共用(?:同一套|一套)\s*\d+\s*项掉落池""")
                        .containsMatchIn(it.value)
                }
                else -> Regex("^(?:共用)?掉落池\\s*·\\s*(.+)$").matchEntire(title)
                    ?.groupValues?.get(1)?.split('、')?.map { it.trim() }?.contains(source.name) == true
            }
        }
        source.copy(sharedPoolId = candidates.singleOrNull()?.entry?.id)
    }
}
