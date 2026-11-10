package com.nekolaska.calabiyau.feature.wiki.extraction.model

/** An image explicitly attached to one dropped item, never a portrait of its source. */
data class ExtractionDropImage(val name: String, val qualityLabel: String? = null, val imageUrl: String)

class DropArtworkIndex internal constructor(private val images: Map<Pair<String, String>, String>) {
    fun imageFor(name: String, qualityLabel: String): String? = images[name.trim() to artworkQuality(qualityLabel)]

    companion object {
        val Empty = DropArtworkIndex(emptyMap())
    }
}

/** Only these labels have the same meaning in this mode's item and drop catalogs. */
private val modeQualities = listOf("优秀", "精致", "卓越", "完美", "臻藏")

private fun artworkQuality(label: String): String {
    val value = label.trim().replace(Regex("\\s+"), " ")
    modeQualities.forEachIndexed { index, name ->
        val code = "Q${index + 1}"
        if (value == name || value == code || value == "$code $name") return "$code $name"
    }
    return value
}

/** Input is restricted to this feature's LOOT/ATTACHMENTS and drop entries, not other game modes. */
fun buildDropArtworkIndex(entries: List<ExtractionEntry>): DropArtworkIndex {
    data class Candidate(val name: String, val quality: String, val image: String?, val itemId: String?)
    val candidates = buildList {
        entries.forEach { entry ->
            if (entry.type == ExtractionEntryType.ITEM) {
                val quality = artworkQuality(entry.qualityLabel)
                // A contradictory code/label is not evidence for another mode's rarity mapping.
                val expected = entry.quality?.let { modeQualities.getOrNull(it - 1) }
                val labelCode = Regex("""^Q(\d+)(?:\s.*)?$""").matchEntire(quality)?.groupValues?.get(1)?.toIntOrNull()
                if ((expected == null || quality == artworkQuality(expected)) &&
                    (entry.quality == null || labelCode == null || entry.quality == labelCode)) {
                    add(Candidate(entry.name.trim(), quality, entry.imageUrl, entry.id))
                }
            }
            entry.dropImages.forEach { image ->
                add(Candidate(image.name.trim(), artworkQuality(image.qualityLabel.orEmpty()), image.imageUrl, null))
            }
            // Missing pictures still establish competing qualities for the same name.
            entry.dropPoolGroups.forEach { group ->
                group.items.forEach { add(Candidate(it.trim(), artworkQuality(group.label), null, null)) }
            }
        }
    }.filter { it.name.isNotBlank() }
    val qualitiesByName = candidates.groupBy { it.name }.mapValues { (_, rows) -> rows.map { it.quality }.toSet() }
    val images = candidates.groupBy { it.name to it.quality }.mapNotNull { (key, rows) ->
        // Never guess the quality of an unqualified inline image, even if only one URL is known.
        if (key.second.isBlank() && qualitiesByName[key.first] != setOf("")) return@mapNotNull null
        if (rows.mapNotNull { it.itemId }.distinct().size > 1) return@mapNotNull null
        val urls = rows.mapNotNull { it.image?.trim()?.takeIf { url -> url.startsWith("https://") || url.startsWith("http://") } }.distinct()
        urls.singleOrNull()?.let { key to it }
    }.toMap()
    return if (images.isEmpty()) DropArtworkIndex.Empty else DropArtworkIndex(images)
}
