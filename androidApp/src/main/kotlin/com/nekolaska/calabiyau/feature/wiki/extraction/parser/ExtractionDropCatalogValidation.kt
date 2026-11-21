package com.nekolaska.calabiyau.feature.wiki.extraction.parser

import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionEntry
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionEntryType
import org.jsoup.nodes.Element

private val mapSection = Regex("""地图场景容器(?:[（(]\s*(\d+)\s*个[）)])?""")
private val itemCount = Regex("""(\d+)\s*项""")

/** Cross-record evidence independent of a pool's own (possibly self-consistent but truncated) counts. */
internal fun validateDropCatalog(root: Element, headings: Map<Int, String>, intros: Map<Int, String>,
    distributionStages: Set<Int>, records: List<DropParsedRecord>, linked: List<ExtractionEntry>, issues: MutableList<String>) {
    val body = root.selectFirst(".mw-parser-output") ?: root
    if (body.selectFirst(".error, .scribunto-error") != null) issues += "drop-catalog: template error in page content"

    // linkDropSharedPools returns one entry per raw record (before variant deduplication).
    val bySection = linked.indices.groupBy { records[it].scope }
    val sizes = linked.filter { it.isDropPool }.associate { pool -> pool.id to pool.dropPoolGroups.sumOf { it.items.size } }
    val itemListTitle = Regex("""^(?:共用)?物品清单\s*·""")
    val itemLists = linked.filter { it.isDropPool && itemListTitle.containsMatchIn(it.name) }
        .map { it.id }.toSet()
    for ((scope, heading) in headings) {
        val indices = bySection[scope].orEmpty()
        val sources = indices.map { linked[it] }.filter { it.type == ExtractionEntryType.DROP_SOURCE }
        val intro = intros[scope].orEmpty()
        val map = mapSection.matchEntire(heading)
        val expectedCards = map?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (expectedCards != null && (sources.size != expectedCards || sources.distinctBy { it.name }.size != expectedCards)) {
            issues += "drop-section $heading: expected $expectedCards distinct source cards, found ${sources.size}"
        }

        // A pool-size field alone does not promise that this page publishes the list (e.g. the Boss card).
        // Enforce full coverage only where the section explicitly says the lists are provided.
        val mapItemLists = map != null && intro.contains("组下方是该组的完整物品清单")
        val mapLists = mapItemLists || (map != null && (intro.contains("组下方即该组的完整掉落池") ||
            intro.contains("组内卡片下方直接给该组的完整掉落池")))
        val bothLists = heading == "据点箱与空投" && intro.contains("两者的完整掉落池见本节末")
        if (bothLists && sources.distinctBy { it.name }.size != 2) issues += "drop-section $heading: expected both source cards"
        val requireLists = mapLists || bothLists || scope in distributionStages
        if (requireLists) {
            val names = sources.map { it.name }.toSet()
            for (index in indices.filter { linked[it].isDropPool }) {
                val enumerated = namedDropSources(linked[index].name).orEmpty()
                if (enumerated.any { it !in names }) issues += "drop-section $heading: pool names an absent source card"
            }
        }
        val tierNotes = sources.mapNotNull { entry -> entry.fields.singleOrNull { it.label == "本节掉落说明" }?.value }
            .mapNotNull { tierPoolDeclaration.find(it)?.groupValues?.get(1)?.toIntOrNull() }.distinct()
        if (tierNotes.isNotEmpty() && heading in setOf("晶源体 · 普通档", "晶源体 · 精英档")) {
            // In 120524 the explicit elite membership reference replaces its own list. No adjacency/ID inference.
            val localTierPools = indices.filter { records[it].element.hasClass("sec-drop-pool") &&
                dropPoolTitle(records[it].element.selectFirst(".sec-drop-pool__head")) == "本档共用掉落池" }
            if (localTierPools.isEmpty() && sources.all { it.sharedPoolId == null }) {
                issues += "drop-section $heading: missing declared tier item list"
            }
            if (localTierPools.any { sizes[linked[it].id] != tierNotes.singleOrNull() }) {
                issues += "drop-section $heading: tier note disagrees with item list size"
            }
        }
        for (source in sources) {
            val actual = source.sharedPoolId?.let(sizes::get)
            if (requireLists && actual == null) issues += "drop-section $heading: missing declared list for ${source.name}"
            // 119982's ammo card counts 25 source entries while its valid legacy pool lists 5 ammo kinds.
            // Compare like with like only under the item-list/distribution contract of the revised page.
            if (actual != null && (source.sharedPoolId in itemLists || mapItemLists || bothLists || scope in distributionStages)) {
                source.fields.filter { it.label == "掉落池规模" }.forEach { field ->
                    val declared = itemCount.matchEntire(field.value)?.groupValues?.get(1)?.toIntOrNull()
                    if (declared != actual) issues += "drop-source ${source.name}: declares ${field.value}, list has $actual items"
                }
            }
        }
    }
}
