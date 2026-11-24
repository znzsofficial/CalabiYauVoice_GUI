package com.nekolaska.calabiyau.core.wiki

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** The costume and weapon-skin Lua modules publish the same five filter fields. */
internal object SkinCatalogMarkup {
    data class Fields(
        val owner: String, val qualityLevel: String, val sources: List<String>,
        val crystalCost: String, val baseCost: String
    )

    private val legacyRow = Regex(
        """\|-\s*class="divsort"\s+data-param1="([^"]*?)"\s+data-param2="([^"]*?)"\s+data-param3="([^"]*?)"\s+data-param4="([^"]*?)"\s+data-param5="([^"]*?)"([\s\S]*?)(?=\|-\s*class="divsort"|$)"""
    )
    private val legacyColumn = Regex("""(?m)^\|\s*""")
    private val whitespace = Regex("""\s+""")
    private val currencyAmount = Regex("""^\d+(晶核|基弦)?$""")

    private fun fields(owner: String, quality: String, sources: String, crystal: String, base: String) = Fields(
        owner.trim(), quality, sources.split(',').map { it.trim() }.filter { it.isNotBlank() },
        crystal.replace("无", "").trim(), base.replace("无", "").trim()
    )

    fun cardFields(card: Element): Fields = fields(
        card.attr("data-param1"), card.attr("data-param2"), card.attr("data-param3"),
        card.attr("data-param4"), card.attr("data-param5")
    )

    fun legacyRows(html: String): Sequence<Pair<Fields, String>> = legacyRow.findAll(html).map { match ->
        val (owner, quality, sources, crystal, base, body) = match.destructured
        fields(owner, quality, sources, crystal, base) to body
    }

    /** Skip the first nonempty (name/image) cell; retain line breaks in the final prose cell. */
    fun legacyDescription(body: String, isCatalogMetadata: (String) -> Boolean): String =
        legacyColumn.splitToSequence(body).map { it.trim() }.filter { it.isNotBlank() }
            .map { HtmlText.detail(Jsoup.parseBodyFragment(it).body(), preserveTextLines = true) }
            .filter { it.isNotBlank() }.drop(1).lastOrNull { text ->
                val compact = text.replace(whitespace, "")
                compact.length >= 12 && '|' !in compact && !currencyAmount.matches(compact) &&
                    !isAcquisitionNote(compact) && !isCatalogMetadata(compact)
            }.orEmpty()

    private fun isAcquisitionNote(text: String): Boolean =
        text.contains("套装限时价格") || text.contains("原价") || text.contains("飘飞特效") ||
            text.contains("进阶外观") || text.contains("定向匣单次抽取价格") || text.endsWith("活动兑换") ||
            text.endsWith("活动奖励") || text.endsWith("赛季奖励") || text.endsWith("活动获得") ||
            text.endsWith("兑换获得") || text.contains("活动商城") || text.contains("限时商城购买") ||
            text.contains("签到活动获得") || text.contains("获得对应角色时装")
}
