package com.nekolaska.calabiyau.feature.wiki.extraction.parser

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import org.jsoup.nodes.Element

internal data class DropParsedRecord(val element: Element, val entry: ExtractionEntry, val scope: Int)

internal fun dropPoolTitle(heading: Element?): String {
    val copy = heading?.clone() ?: return ""
    val titleNodes = copy.childNodes().takeWhile { it !is Element || (it.tagName() != "b" && !it.hasClass("sec-drop-pool__note")) }
    copy.childNodes().drop(titleNodes.size).forEach { it.remove() }
    return content(copy)
}
internal val tierPoolDeclaration = Regex("""^本档\s*\d+\s*只(?:晶源体|怪)共用(?:同一套|一套)\s*(\d+)\s*项掉落池\s*(?=$|[。（])""")
private val sameOrdinaryItems = Regex("""(?:^|[。\n])\s*本档与普通档物品完全相同（同为\s*(\d+)\s*项），只是各物品的单件抽中率不同。?$""")
// Do not parse editorial sentences, exceptions or open-ended lists as enumerated names.
private val namedPool = Regex("""^(?:共用)?(?:掉落池|物品清单)\s*·\s*([^。，；：！？（）\n]+)$""")
internal fun namedDropSources(title: String) = namedPool.matchEntire(title)?.groupValues?.get(1)?.split('、')?.map { it.trim() }

/** November 10 removed card IDs. Use explicit pool declarations, never the numbers in pool anchors. */
internal fun linkDropSharedPools(records: List<DropParsedRecord>, headings: Map<Int, String>): List<ExtractionEntry> {
    val poolIdCounts = records.filter { it.entry.isDropPool }.groupingBy { it.entry.id }.eachCount()
    val byScope = records.groupBy { it.scope }
    val titles = records.filter { it.element.hasClass("sec-drop-pool") }.associate { pool ->
        pool.element to dropPoolTitle(pool.element.selectFirst(".sec-drop-pool__head"))
    }
    // 120524 removed the elite pool. A scoped, affirmative note explicitly directs us to
    // the ordinary tier's *item membership*. Equal sizes/nearby headings alone never link.
    val elite = headings.filterValues { it == "晶源体 · 精英档" }.keys.singleOrNull()
    val ordinary = headings.filterValues { it == "晶源体 · 普通档" }.keys.singleOrNull()
    val ordinaryPool = byScope[ordinary].orEmpty().filter { it.entry.isDropPool }.singleOrNull()?.takeIf {
        titles[it.element] == "本档共用掉落池" && poolIdCounts[it.entry.id] == 1
    }?.entry
    val membership = ordinaryPool?.takeIf { target ->
        val size = target.dropPoolGroups.sumOf { it.items.size }
        val note = target.fields.singleOrNull { it.label == "本节掉落说明" }?.value.orEmpty()
        val declared = target.fields.singleOrNull { it.label == "池规模" }?.value.orEmpty()
        size > 0 && Regex("""(\d+)\s*项""").matchEntire(declared)?.groupValues?.get(1)?.toIntOrNull() == size &&
            tierPoolDeclaration.find(note)?.groupValues?.get(1)?.toIntOrNull() == size &&
            elite != null && byScope[elite].orEmpty().none { it.entry.isDropPool }
    }
    return records.map { record ->
        val source = record.entry
        if (source.type != ExtractionEntryType.DROP_SOURCE) return@map source
        val scoped = byScope.getValue(record.scope)
        // A repeated name in one section cannot be disambiguated by a pool title.
        if (scoped.count { it.entry.type == ExtractionEntryType.DROP_SOURCE && it.entry.name == source.name } != 1)
            return@map source
        val candidates = scoped.filter { pool ->
            val title = titles[pool.element] ?: return@filter false
            when {
                title == "本档共用掉落池" -> source.fields.any {
                    it.label == "本节掉落说明" && tierPoolDeclaration.containsMatchIn(it.value)
                }
                else -> namedDropSources(title)?.contains(source.name) == true
            }
        }
        val local = candidates.singleOrNull()?.entry?.id?.takeIf { poolIdCounts[it] == 1 }
        val crossTier = if (membership != null && record.scope == elite) {
            val note = source.fields.singleOrNull { it.label == "本节掉落说明" }?.value.orEmpty()
            val size = membership.dropPoolGroups.sumOf { it.items.size }
            membership.id.takeIf { tierPoolDeclaration.find(note)?.groupValues?.get(1)?.toIntOrNull() == size &&
                sameOrdinaryItems.find(note)?.groupValues?.get(1)?.toIntOrNull() == size }
        } else null
        source.copy(sharedPoolId = local ?: crossTier)
    }
}
