package com.nekolaska.calabiyau.feature.wiki.decoration.api

import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogs
import com.nekolaska.calabiyau.feature.wiki.decoration.parser.DecorationHtmlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecorationAssemblerTest {
    private fun fixture() = requireNotNull(javaClass.getResource("/fixtures/decoration/modern-contract.html")).readText(Charsets.UTF_8)

    @Test
    fun mixedPlatformsKeepIndependentStableKeysAndOriginalImagesWin() {
        val raw = DecorationHtmlParser.parse(fixture())
        val groups = DecorationAssembler.assemble(raw, DecorationCatalogs.forPage("喷漆"), notice = "降级提示")
        val sprays = groups.last().items
        assertEquals(listOf("赏春荧", "马踏飞弦", "甜蜜心愿", "默契考验"), sprays.map { it.name })
        assertEquals(4, sprays.map { it.stableKey }.distinct().size)
        assertEquals(setOf("PC", "移动端"), sprays.filter { it.id == 30002237 }.map { it.platform }.toSet())
        assertEquals(1, groups.count { it.notice != null })
        assertEquals("降级提示", groups.first().notice)
        assertEquals(groups, DecorationAssembler.assemble(raw, DecorationCatalogs.forPage("喷漆"), notice = "降级提示"))
    }

    @Test
    fun duplicateRowsCollapseWithinGroupButRemainAcrossGroupsAndVariantsNeverCollide() {
        val raw = DecorationHtmlParser.parse(fixture()).last()
        val item = raw.items.first()
        val sections = listOf(raw.copy(items=listOf(item,item,item.copy(description="另一条真实说明"))),
            raw.copy(title="另一组",key="另一组",items=listOf(item)))
        val assembled = DecorationAssembler.assemble(sections, DecorationCatalogs.forPage("基板"))
        assertEquals(listOf(2,1), assembled.map { it.items.size })
        val keys = assembled.flatMap { it.items }.map { it.stableKey }
        assertEquals(3, keys.distinct().size)
    }

    @Test
    fun missingArtworkNeverDropsItemsOrDeclaredExtraPreviews() {
        val raw = DecorationHtmlParser.parse(fixture())
        val room = DecorationAssembler.assemble(raw, DecorationCatalogs.forPage("房间外观")).first().items.single()
        assertEquals(2, room.extraPreviews.size)
        assertEquals("", room.extraPreviews.first().imageUrl)
        assertTrue(room.imageUrl.startsWith("https://patchwiki"))
        assertTrue(DecorationAssembler.unresolvedImageFiles(raw).any { it.contains("FriendTitleBg") })
    }
}
