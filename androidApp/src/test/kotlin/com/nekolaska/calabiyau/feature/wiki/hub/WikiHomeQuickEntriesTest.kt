package com.nekolaska.calabiyau.feature.wiki.hub

import kotlin.test.Test
import kotlin.test.assertEquals

class WikiHomeQuickEntriesTest {
    private fun slots(vararg ids: String) = ids.toList().resolveQuickEntries().map(QuickEntry::id)

    @Test fun emptyOrRetiredSelectionUsesOriginalSixShortcuts() {
        val expected = listOf("characters", "weapons", "maps", "voting", "costumes", "bio_cards")
        assertEquals(expected, slots())
        assertEquals(expected, slots("retired", ""))
    }

    @Test fun invalidAndDuplicateSlotsKeepFirstUserChoicesAndLegacyFillOrder() {
        assertEquals(listOf("activities", "maps", "characters", "weapons", "items", "mobile_items"),
            slots("retired", "activities", "maps", "activities", "", "maps"))
    }

    @Test fun explicitlyChosenTopicStaysInPlaceAndExtraSlotsDoNotDisplaceSavedOnes() {
        val saved = listOf("wallpapers", "extraction_hub", "announcements", "oath", "maps", "costumes")
        assertEquals(saved, (saved + "items").resolveQuickEntries().map(QuickEntry::id))
    }
}
