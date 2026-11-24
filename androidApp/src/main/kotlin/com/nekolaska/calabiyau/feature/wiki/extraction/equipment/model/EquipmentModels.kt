package com.nekolaska.calabiyau.feature.wiki.extraction.equipment.model

internal data class EquipmentQuality(val code: Int, val name: String) {
    val label get() = "Q$code $name"
}

internal sealed interface EquipmentStats {
    val fields: Map<String, String>

    data class Gun(val damage: String, val fireRate: String, val magazine: String, val scatter: String,
                   val recoil: String, val handling: String) : EquipmentStats {
        override val fields = linkedMapOf("伤害" to damage, "射速" to fireRate, "弹匣" to magazine,
            "散射控制" to scatter, "后坐力控制" to recoil, "操控速度" to handling)
    }

    data class Armor(val armor: String, val health: String, val pveDefense: String?, val size: String,
                     val weight: String) : EquipmentStats {
        override val fields = buildMap {
            put("护甲", armor); put("生命", health)
            pveDefense?.let { put("对怪物减伤", it) }
            put("占格", this@Armor.size); put("重量", weight)
        }
    }
}

internal data class EquipmentVariant(
    val id: Int, val quality: EquipmentQuality, val imageUrl: String?, val description: String,
    val stats: EquipmentStats, val extraStats: Map<String, String>, val value: String,
    val tags: List<String>, val sourceTags: Map<String, String>, val lootText: String, val weight: String, val searchTimeMs: String,
    val numericValue: Long, val numericRate: Double
) {
    val attributes get() = stats.fields + extraStats
    val searchText = (listOf(id.toString(), quality.label, description, lootText, value, numericValue.toString()) + tags + sourceTags.values +
        attributes.map { "${it.key} ${it.value}" }).joinToString("\n")
}

internal data class EquipmentSeries(val key: String, val name: String, val category: String,
                                    val variants: List<EquipmentVariant>)

internal data class EquipmentPage(val series: List<EquipmentSeries>, val categories: List<String>,
                                  val qualities: List<EquipmentQuality>) {
    companion object { val EMPTY = EquipmentPage(emptyList(), emptyList(), emptyList()) }
}

internal enum class EquipmentSort(val label: String) {
    DEFAULT("默认"), DAMAGE("伤害"), ARMOR("护甲"), HP("生命"), VALUE("价值"), RATE("出现率");

    fun rank(item: EquipmentVariant): Double = when (this) {
        DEFAULT -> 0.0
        DAMAGE -> (item.stats as EquipmentStats.Gun).damage.toDouble()
        ARMOR -> (item.stats as EquipmentStats.Armor).armor.toDouble()
        HP -> (item.stats as EquipmentStats.Armor).health.toDouble()
        VALUE -> item.numericValue.toDouble()
        RATE -> item.numericRate
    }
}

internal data class EquipmentRow(val series: EquipmentSeries, val variant: EquipmentVariant)

/** Sorting, thumbnail and detail entry all use the same matching variant. */
internal fun EquipmentPage.rows(query: String, category: String?, quality: Int?, sort: EquipmentSort): List<EquipmentRow> {
    val term = query.trim()
    val result = series.mapNotNull { group ->
        if (category != null && group.category != category) return@mapNotNull null
        val matchSeries = group.name.contains(term, true) || group.category.contains(term, true)
        val variant = group.variants.firstOrNull {
            (quality == null || it.quality.code == quality) && (matchSeries || it.searchText.contains(term, true))
        } ?: return@mapNotNull null
        EquipmentRow(group, variant)
    }
    return if (sort == EquipmentSort.DEFAULT) result else result.sortedByDescending { sort.rank(it.variant) }
}
