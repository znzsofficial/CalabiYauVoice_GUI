package com.nekolaska.calabiyau.feature.wiki.bio

import com.nekolaska.calabiyau.feature.wiki.bio.model.DeckCardOption
import com.nekolaska.calabiyau.feature.wiki.bio.model.PcCard
import kotlin.test.Test
import kotlin.test.assertEquals

class BioDeckCardImagesTest {
    @Test
    fun sameNameUsesCardIdInsteadOfTheOtherFactionImage() {
        val crystal = card("破釜沉舟", "晶源体", "https://patchwiki.biligame.com/images/klbq/6/69/a.png")
        val human = card("破釜沉舟", "超弦体", "https://patchwiki.biligame.com/images/klbq/thumb/b/b1/b.png/200px-%E7%94%9F%E5%8C%96%E5%8D%A1%E7%89%8C_20251043.png")
        val crystalUrl = crystal.imageUrl!!.replace("a.png", "生化卡牌_10351015.png")
        val cards = listOf(crystal.copy(imageUrl = crystalUrl), human)

        assertEquals(crystalUrl, deckCardImageUrl(option("10351015", "破釜沉舟"), "晶源体", cards, emptyList()))
        assertEquals(human.imageUrl, deckCardImageUrl(option("20251043", "破釜沉舟"), "超弦体", cards, emptyList()))
        assertEquals("10351015", imageCardId(crystalUrl))
        assertEquals("20251043", imageCardId(human.imageUrl))
    }

    private fun option(id: String, name: String) = DeckCardOption(name, id, "精致", false, 1)

    private fun card(name: String, faction: String, image: String) = PcCard(
        name = name,
        faction = faction,
        rarity = 2,
        category = "武器",
        defaultTag = "",
        acquireType = "",
        releaseDate = "",
        maxLevel = "",
        effect = "",
        roles = emptyList(),
        imageUrl = image,
        refreshProbability = null
    )
}
