package data

/** Legacy official balance-page seasons omitted from the new site's settings API. */
object BalanceSeasons {
    // S1–S9: https://klbq.qq.com/web202305/balance-date.html
    // S10: 敕魂命途 (2025-03-25 season update).
    val historicalNames = listOf(
        "赤刃行动", "低语暗云", "暮光幻蝶", "日珥鸣火", "纵弦震音",
        "虚像艺匠", "绯息醒世", "蚀刻迷局", "隙间重构", "敕魂命途"
    )

    /** Upstream metadata wins; never replace future seasons with a static catalog. */
    fun <T> merge(upstream: List<T>, code: (T) -> String, option: (String, String) -> T): List<T> =
        (upstream + historicalNames.mapIndexed { index, name -> option((index + 1).toString(), name) })
            .distinctBy(code)
            .sortedByDescending { code(it).toIntOrNull() ?: Int.MIN_VALUE }

    // Match the legacy official UI, even if earlier responses contain a score field.
    fun supportsScore(seasonCode: String): Boolean = (seasonCode.toIntOrNull() ?: 0) >= 8
}
