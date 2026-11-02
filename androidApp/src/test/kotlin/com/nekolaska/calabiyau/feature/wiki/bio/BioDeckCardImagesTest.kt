package com.nekolaska.calabiyau.feature.wiki.bio

import com.nekolaska.calabiyau.feature.wiki.bio.model.DeckCardOption
import com.nekolaska.calabiyau.feature.wiki.bio.model.PcCard
import com.nekolaska.calabiyau.feature.wiki.bio.model.MobileCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BioDeckCardImagesTest {
    @Test
    fun sameNameUsesCardIdInsteadOfTheOtherFactionImage() {
        val crystal = card("破釜沉舟", "晶源体", "https://patchwiki.biligame.com/images/klbq/6/69/a.png")
        val human = card("破釜沉舟", "超弦体", "https://patchwiki.biligame.com/images/klbq/thumb/b/b1/b.png/200px-%E7%94%9F%E5%8C%96%E5%8D%A1%E7%89%8C_20251043.png")
        val crystalUrl = crystal.imageUrl!!.replace("a.png", "生化卡牌_10351015.png")
        val cards = listOf(crystal.copy(imageUrl = crystalUrl), human)
        val images = BioDeckCardImageIndex(cards, emptyList())

        assertEquals(crystalUrl, images.imageUrl(option("10351015", "破釜沉舟"), "晶源体"))
        assertEquals(human.imageUrl, images.imageUrl(option("20251043", "破釜沉舟"), "超弦体"))
        assertEquals("10351015", imageCardId(crystalUrl))
        assertEquals("20251043", imageCardId(human.imageUrl))
    }

    @Test
    fun idWinsOverSameFactionNameAndCanResolveMobileImages() {
        val pc = card("重复名称", "超弦体", "https://fake/生化卡牌_123.png")
        val mobile = mobile("别名", "超弦体", "https://fake/生化卡牌_456.png")
        val images = BioDeckCardImageIndex(listOf(pc), listOf(mobile))
        assertEquals(mobile.imageUrl, images.imageUrl(option("456", "重复名称"), "超弦体"))
    }

    @Test
    fun fallsBackOnlyToSameFactionNameAndSkipsMissingImages() {
        val otherFaction = card("重复名称", "晶源体", "https://fake/crystal.png")
        val emptyPc = card("重复名称", "超弦体", " ")
        val goodPc = card("重复名称", "超弦体", "https://fake/human.png")
        val mobile = mobile("重复名称", "超弦体", "https://fake/mobile.png")
        val images = BioDeckCardImageIndex(listOf(otherFaction, emptyPc, goodPc), listOf(mobile))
        assertEquals(goodPc.imageUrl, images.imageUrl(option("unknown", "重复名称"), "超弦体"))
        assertEquals(otherFaction.imageUrl, images.imageUrl(option("", "重复名称"), "晶源体"))
        val mobileFallback = BioDeckCardImageIndex(listOf(otherFaction, emptyPc.copy(imageUrl = null)), listOf(mobile))
        assertEquals(mobile.imageUrl, mobileFallback.imageUrl(option("", "重复名称"), "超弦体"))
        assertNull(BioDeckCardImageIndex(listOf(otherFaction), emptyList()).imageUrl(option("missing", "重复名称"), "超弦体"))
        assertNull(images.imageUrl(option("missing", "不存在"), "超弦体"))
        assertNull(BioDeckCardImageIndex(emptyList(), emptyList()).imageUrl(option("", "重复名称"), "超弦体"))
    }

    @Test
    fun imageIdHandlesMissingMalformedAndSpaceSeparatedFileNames() {
        assertNull(imageCardId(null))
        assertNull(imageCardId(""))
        assertNull(imageCardId("https://fake/no-id.png"))
        assertNull(imageCardId("https://fake/生化卡牌_123wrong.png"))
        assertEquals("123", imageCardId("https://fake/生化卡牌%20123.png"))
        assertEquals("456", imageCardId("https://fake/%broken/生化卡牌_456.png"))
    }

    private fun option(id: String, name: String) = DeckCardOption(name, id, "精致", false, 1)

    private fun mobile(name: String, faction: String, image: String?) =
        MobileCard(name, faction, "武器", 2, "", "", image)

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
