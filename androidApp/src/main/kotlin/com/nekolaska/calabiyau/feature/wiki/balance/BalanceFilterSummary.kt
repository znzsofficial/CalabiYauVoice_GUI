package com.nekolaska.calabiyau.feature.wiki.balance

/** 平衡数据页顶部只留一行摘要，完整条件放在底部弹层里。 */
internal data class BalanceFilterSummary(
    val headline: String,
    val compareLabel: String?
)

internal fun balanceFilterSummary(
    mode: String?,
    season: String?,
    map: String?,
    rankNames: List<String>,
    compareSeason: String?
): BalanceFilterSummary {
    val namedRanks = rankNames.map { it.trim() }.filter { it.isNotEmpty() }
    val rankLabel = when (namedRanks.size) {
        0 -> "全段位"
        1 -> namedRanks.single()
        else -> "${namedRanks.size} 个段位"
    }
    val headline = listOfNotNull(
        season?.trim()?.takeIf { it.isNotEmpty() },
        mode?.trim()?.takeIf { it.isNotEmpty() },
        map?.trim()?.takeIf { it.isNotEmpty() } ?: "全地图",
        rankLabel
    ).joinToString(" · ")
    return BalanceFilterSummary(
        headline = headline,
        compareLabel = compareSeason?.trim()?.takeIf { it.isNotEmpty() }?.let { "对比 $it" }
    )
}
