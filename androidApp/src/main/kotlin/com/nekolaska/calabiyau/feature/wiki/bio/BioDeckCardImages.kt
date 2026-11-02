package com.nekolaska.calabiyau.feature.wiki.bio

import com.nekolaska.calabiyau.feature.wiki.bio.model.DeckCardOption
import com.nekolaska.calabiyau.feature.wiki.bio.model.MobileCard
import com.nekolaska.calabiyau.feature.wiki.bio.model.PcCard
import java.net.URLDecoder

/**
 * PC/移动端图鉴里人类和晶源体可以同名。按名字取最后一张会把另一阵营的图套到这张卡上。
 * 卡牌文件名带 cardId 时优先用 ID，否则只接受同一阵营的同名卡。
 */
internal class BioDeckCardImageIndex(
    pcCards: List<PcCard>,
    mobileCards: List<MobileCard>
) {
    private val byId = mutableMapOf<String, String>()
    private val byFactionAndName = mutableMapOf<Pair<String, String>, String>()

    init {
        // Decode each URL once per catalog update, not once per selectable card.
        pcCards.forEach { add(it.imageUrl, it.faction, it.name, it.cardId) }
        mobileCards.forEach { add(it.imageUrl, it.faction, it.name) }
    }

    private fun add(url: String?, faction: String, name: String, knownCardId: String = "") {
        if (url.isNullOrBlank()) return
        val id = knownCardId.trim().takeIf { it.isNotBlank() } ?: imageCardId(url)
        id?.let { byId.putIfAbsent(it, url) }
        byFactionAndName.putIfAbsent(faction.trim() to name.trim(), url)
    }

    fun imageUrl(card: DeckCardOption, faction: String): String? {
        return byId[card.cardId.trim()]
            ?: byFactionAndName[faction.trim() to card.name.trim()]
    }
}

private val IMAGE_CARD_ID = Regex("""生化卡牌[_ ](\d+)(?=[._ /?#]|$)""")

internal fun imageCardId(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val decoded = runCatching { URLDecoder.decode(url, Charsets.UTF_8.name()) }.getOrDefault(url)
    return IMAGE_CARD_ID.find(decoded)?.groupValues?.getOrNull(1)
}
