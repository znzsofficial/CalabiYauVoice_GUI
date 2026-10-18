package com.nekolaska.calabiyau.feature.wiki.balance.model

import java.util.Locale

enum class BalanceMetric(val label: String, private val decimals: Int) {
    WIN_RATE("胜率", 2), SELECT_RATE("选取率", 2), KD("K/D", 2),
    DAMAGE("回合均伤", 1), SCORE("表现分", 1);

    private val isPercent: Boolean get() = this == WIN_RATE || this == SELECT_RATE

    fun format(value: Double?): String {
        if (value == null || !value.isFinite()) return "—"
        return String.format(Locale.ROOT, "%.${decimals}f", value) + if (isPercent) "%" else ""
    }

    /** Percentages are compared in percentage points, never relative percentage change. */
    fun difference(current: Double?, baseline: Double?): String? {
        if (current == null || baseline == null || !current.isFinite() || !baseline.isFinite()) return null
        val rounded = String.format(Locale.ROOT, "%.${decimals}f", current - baseline).toDouble()
        if (rounded == 0.0) return "持平"
        val sign = if (rounded > 0) "+" else "−"
        val value = String.format(Locale.ROOT, "%.${decimals}f", kotlin.math.abs(rounded))
        return sign + value + if (isPercent) " pp" else ""
    }
}
