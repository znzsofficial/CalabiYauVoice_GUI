package com.nekolaska.calabiyau.feature.wiki.extraction.model

import java.util.BitSet
import java.util.IdentityHashMap

/** Index membership only. The original ordered groups still supply the exact qualities, evidence and artwork. */
internal class DropSourceSearch(private val records: List<DropSourceRecord>, private val catalog: List<ExtractionEntry>) {
    private data class Pools(val own: BitSet?, val notes: BitSet?, val shared: BitSet?)
    private class Index(val terms: Map<String, Int>, val pools: List<Pools>)

    // Browsing or filtering by category does not need to read the candidates. Many sources point at one shared pool.
    private val index = lazy {
        val terms = mutableMapOf<String, Int>()
        val byEntry = IdentityHashMap<ExtractionEntry, BitSet>()
        fun membership(entry: ExtractionEntry?): BitSet? {
            if (entry == null || entry.dropPoolGroups.isEmpty()) return null
            return byEntry.getOrPut(entry) {
                BitSet().apply {
                    entry.dropPoolGroups.forEach { group ->
                        set(terms.getOrPut(group.label) { terms.size })
                        group.items.forEach { set(terms.getOrPut(it) { terms.size }) }
                    }
                }
            }
        }
        val pools = records.map { Pools(membership(it.entry), membership(it.sourceNotes), membership(linkedDropSharedPool(it.entry, catalog))) }
        Index(terms, pools)
    }

    /** Build once on the worker. A keystroke never waits for this snapshot's index. */
    fun prepare() { index.value }

    fun filter(query: String, category: String?): List<DropSourceRecord> {
        val term = query.trim()
        if (term.isEmpty()) return if (category == null) records else records.filter { it.entry.filterCategory == category }
        val search = if (index.isInitialized()) index.value else null
        val hits = search?.terms?.let { terms ->
            BitSet(terms.size).apply { terms.forEach { (text, id) -> if (text.contains(term, ignoreCase = true)) set(id) } }
        }
        return records.mapIndexedNotNull { position, record ->
            if (category != null && category != record.entry.filterCategory) return@mapIndexedNotNull null
            val pool = search?.pools?.get(position)
            val notes = record.sourceNotes
            when {
                record.entry.matchesMetadata(term) || poolHit(hits, pool?.own, term) { record.entry } -> record
                notes != null && (notes.matchesMetadata(term) || poolHit(hits, pool?.notes, term) { notes }) -> DropSourceRecord(notes, false)
                poolHit(hits, pool?.shared, term) { linkedDropSharedPool(record.entry, catalog) } -> record
                else -> null
            }
        }
    }

    private inline fun poolHit(hits: BitSet?, mask: BitSet?, term: String, entry: () -> ExtractionEntry?): Boolean =
        if (hits == null) entry()?.matchesPoolTerm(term) == true else mask?.intersects(hits) == true
}
