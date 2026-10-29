package com.nekolaska.calabiyau.feature.wiki.bio

import com.nekolaska.calabiyau.feature.wiki.bio.model.DeckCardOption
import com.nekolaska.calabiyau.feature.wiki.bio.model.MobileCard
import com.nekolaska.calabiyau.feature.wiki.bio.model.PcCard
import java.net.URLDecoder

/**
 * PC/移动端图鉴里人类和晶源体可以同名。按名字取最后一张会把另一阵营的图套到这张卡上。
 * 卡牌文件名带 cardId 时优先用 ID，否则只接受同一阵营的同名卡。
 */
internal fun deckCardImageUrl(
    card: DeckCardOption,
    faction: String,
    pcCards: List<PcCard>,
    mobileCards: List<MobileCard>
): String? {
    val cardId = card.cardId.trim()
    if (cardId.isNotBlank()) {
        val byId = (pcCards.map { it.imageUrl } + mobileCards.map { it.imageUrl })
            .firstOrNull { imageCardId(it) == cardId }
        if (byId != null) return byId
    }
    return pcCards.firstOrNull { it.faction == faction && it.name == card.name }?.imageUrl
        ?: mobileCards.firstOrNull { it.faction == faction && it.name == card.name }?.imageUrl
}

internal fun imageCardId(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val decoded = runCatching { URLDecoder.decode(url, Charsets.UTF_8.name()) }.getOrDefault(url)
    return Regex("""生化卡牌[_ ](\d+)""").find(decoded)?.groupValues?.getOrNull(1)
}
