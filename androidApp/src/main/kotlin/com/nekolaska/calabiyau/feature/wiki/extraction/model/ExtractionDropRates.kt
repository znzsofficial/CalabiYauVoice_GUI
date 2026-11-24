package com.nekolaska.calabiyau.feature.wiki.extraction.model

import com.nekolaska.calabiyau.core.ui.catalogKeys
import java.math.BigDecimal

internal data class DropRateShard(val number: Int, val names: Set<String>)

internal data class DropRate(
    val source: String, val type: String, val quality: Int?, val pool: String, val percent: BigDecimal
) {
    val displayPercent: String get() = "${percent.stripTrailingZeros().toPlainString()}%"
}

internal data class DropRateRow(val key: String, val rate: DropRate)
internal data class DropRateSelection(val rows: List<DropRateRow>, val total: Int, val allBelowThreshold: Boolean)

/** The Widget's rates are already percentages. Apply its threshold and top-five fallback *within* the selected quality. */
internal fun selectDropRates(rates: List<DropRate>, quality: Int?, showAll: Boolean): DropRateSelection {
    val keys = catalogKeys(rates) { row ->
        listOf(row.source, row.type, row.quality.toString(), row.pool).joinToString("") { "${it.length}:$it" }
    }
    val sorted = rates.mapIndexedNotNull { i, row ->
        if (row.quality == quality) DropRateRow(keys[i], row) else null
    }.sortedByDescending { it.rate.percent } // Stable on ties; never merge equal names or pool records.
    val visible = sorted.takeWhile { it.rate.percent >= minDropRate }
    return DropRateSelection(when {
        showAll -> sorted
        visible.isEmpty() -> sorted.take(5)
        else -> visible
    }, sorted.size, sorted.isNotEmpty() && visible.isEmpty())
}

private val minDropRate = BigDecimal("0.005")
