package com.nekolaska.calabiyau.feature.wiki.balance

import com.nekolaska.calabiyau.feature.wiki.balance.model.BalanceMetric
import com.nekolaska.calabiyau.feature.wiki.balance.model.BalanceSettings
import data.BalanceSeasons

/** Only codes belong in saved state; labels and available options come from fresh settings. */
internal data class BalanceFilterCodes(
    val mode: String? = null,
    val map: String? = null,
    val season: String? = null,
    val compareSeason: String? = null,
    val ranks: List<String> = emptyList()
) {
    fun reconcile(settings: BalanceSettings): BalanceFilterCodes {
        val validSeason = season?.takeIf { code -> settings.seasons.any { it.code == code } }
            ?: settings.seasons.firstOrNull()?.code
        return copy(
            mode = mode?.takeIf { code -> settings.modes.any { it.code == code } }
                ?: (settings.modes.find { it.name == "排位爆破" } ?: settings.modes.firstOrNull())?.code,
            map = map?.takeIf { code -> settings.maps.any { it.code == code } },
            season = validSeason,
            compareSeason = compareSeason?.takeIf { code ->
                code != validSeason && settings.seasons.any { it.code == code }
            },
            // Canonical option order makes rank selection order irrelevant to requests.
            ranks = settings.ranks.map { it.code }.distinct().filter { it in ranks }
        )
    }

    fun queryOrNull(): BalanceDataQuery? {
        return BalanceDataQuery(
            mode = mode ?: return null,
            map = map ?: "-255",
            ranks = ranks.ifEmpty { listOf("-255") },
            season = season ?: return null,
            compareSeason = compareSeason ?: "0"
        )
    }
}

internal data class BalanceDataQuery(
    val mode: String,
    val map: String,
    val ranks: List<String>,
    val season: String,
    val compareSeason: String
)

internal enum class BalanceSortOrder { ASC, DESC }

/** Draft changes never change [applied] (the sole source of data request keys). */
internal data class BalanceFilterState(
    val applied: BalanceFilterCodes = BalanceFilterCodes(),
    val draft: BalanceFilterCodes? = null,
    val showAttackers: Boolean = false,
    val sortField: BalanceMetric = BalanceMetric.WIN_RATE,
    val sortOrder: BalanceSortOrder = BalanceSortOrder.DESC
) {
    fun reconcile(settings: BalanceSettings): BalanceFilterState = copy(
        applied = applied.reconcile(settings),
        draft = draft?.reconcile(settings)
    ).correctSort()

    fun beginEditing(settings: BalanceSettings): BalanceFilterState = copy(draft = applied.reconcile(settings))

    fun changeDraft(filters: BalanceFilterCodes, settings: BalanceSettings): BalanceFilterState =
        if (draft == null) this else copy(draft = filters.reconcile(settings))

    fun resetDraft(settings: BalanceSettings): BalanceFilterState =
        changeDraft(BalanceFilterCodes(), settings)

    fun cancelDraft(): BalanceFilterState = copy(draft = null)

    fun applyDraft(settings: BalanceSettings): BalanceFilterState =
        draft?.let { copy(applied = it.reconcile(settings), draft = null).correctSort() } ?: this

    private fun correctSort(): BalanceFilterState =
        if (sortField == BalanceMetric.SCORE && !BalanceSeasons.supportsScore(applied.season.orEmpty())) {
            copy(sortField = BalanceMetric.WIN_RATE, sortOrder = BalanceSortOrder.DESC)
        } else this

    /** Flat strings are Bundle-safe, small, and exercise the exact restoration codec in JVM tests. */
    fun savedValues(): List<String> = buildList {
        add("1") // Format version.
        add(showAttackers.toString())
        add(sortField.name)
        add(sortOrder.name)
        add((draft != null).toString())
        fun addFilters(filters: BalanceFilterCodes) {
            add(filters.mode.orEmpty())
            add(filters.map.orEmpty())
            add(filters.season.orEmpty())
            add(filters.compareSeason.orEmpty())
            add(filters.ranks.size.toString())
            addAll(filters.ranks)
        }
        addFilters(applied)
        draft?.let(::addFilters)
    }

    companion object {
        fun restore(values: List<String>): BalanceFilterState? = runCatching {
            require(values.first() == "1")
            var index = 5
            fun readFilters(): BalanceFilterCodes {
                fun code() = values[index++].takeIf { it.isNotEmpty() }
                val mode = code()
                val map = code()
                val season = code()
                val compareSeason = code()
                val count = values[index++].toInt()
                require(count >= 0 && count <= values.size - index)
                val ranks = values.subList(index, index + count).toList()
                index += count
                return BalanceFilterCodes(mode, map, season, compareSeason, ranks)
            }
            val applied = readFilters()
            val draft = if (values[4].toBooleanStrict()) readFilters() else null
            require(index == values.size)
            BalanceFilterState(
                applied = applied,
                draft = draft,
                showAttackers = values[1].toBooleanStrict(),
                sortField = BalanceMetric.entries.find { it.name == values[2] } ?: BalanceMetric.WIN_RATE,
                sortOrder = BalanceSortOrder.entries.find { it.name == values[3] } ?: BalanceSortOrder.DESC
            )
        }.getOrNull()
    }
}
