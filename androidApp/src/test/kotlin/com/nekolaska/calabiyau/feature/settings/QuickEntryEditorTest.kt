package com.nekolaska.calabiyau.feature.settings

import androidx.compose.runtime.saveable.SaverScope
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.feature.wiki.hub.allQuickEntries
import com.nekolaska.calabiyau.feature.wiki.hub.defaultQuickEntryIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class QuickEntryEditorTest {
    @Test fun replaceThirdThenSwapAnOccupiedSlotWithoutChangingTheOriginal() {
        val original = QuickEntryDraft.default
        assertSame(original, original.choose(-1, "extraction_hub"))
        val replaced = original.choose(2, "extraction_hub")
        assertEquals(listOf("characters", "weapons", "extraction_hub", "voting", "costumes", "bio_cards"), replaced.ids)
        val swapped = replaced.choose(0, "bio_cards")
        assertEquals(listOf("bio_cards", "weapons", "extraction_hub", "voting", "costumes", "characters"), swapped.ids)
        assertSame(swapped, swapped.choose(0, "bio_cards"))
        assertEquals(original, swapped.choose(0, "characters").choose(2, "maps"))
        assertEquals(defaultQuickEntryIds, original.ids)
    }

    @Test fun moveSixthToFirstShiftsTheOtherFiveInOrder() {
        val original = QuickEntryDraft.default
        val reordered = original.move(5, 0)
        assertEquals(listOf("bio_cards", "characters", "weapons", "maps", "voting", "costumes"), reordered.ids)
        assertEquals(original, reordered.move(0, 5))
    }

    @Test fun retainedEditorDoesNotRollBackTheOtherEditorsSave() {
        val original = QuickEntryDraft.default
        val otherOrder = original.move(5, 0)
        val newLayout = original.copy(layout = AppPrefs.HOME_QUICK_LAYOUT_BUTTONS)
        assertEquals(otherOrder, original.resolveSave(original, otherOrder))
        assertEquals(otherOrder.copy(layout = newLayout.layout), newLayout.resolveSave(original, otherOrder))
        val myOrder = original.choose(1, "extraction_hub")
        val other = otherOrder.copy(layout = newLayout.layout)
        assertNull(myOrder.resolveSave(original, other))
        assertEquals(myOrder.copy(layout = other.layout), myOrder.resolveSave(original, other, overwrite = true))
    }

    @Test fun recreationKeepsDisplayBaselineAndUnsavedEditsSeparate() {
        val stored = mutableListOf("wallpapers", "oath")
        val original = QuickEntryDraft.fromHome(stored, AppPrefs.HOME_QUICK_LAYOUT_BUTTONS)
        assertEquals(listOf("wallpapers", "oath", "characters", "weapons", "maps", "items"), original.ids)
        assertEquals(listOf("wallpapers", "oath"), stored)

        fun recreate(value: QuickEntryDraft): QuickEntryDraft = with(QuickEntryDraft.Saver) {
            val saved = requireNotNull(SaverScope { true }.save(value))
            requireNotNull(restore(saved))
        }
        val edited = original.choose(2, "extraction_hub").copy(layout = AppPrefs.HOME_QUICK_LAYOUT_GRID)
        assertEquals(original, recreate(original))
        assertEquals(edited, recreate(edited))
        assertNotEquals(recreate(original), recreate(edited))
        assertNotEquals(recreate(original), QuickEntryDraft.default)
    }

    @Test fun candidateListIncludesEveryShortcutOnceAndFindsShortNamesAndAliases() {
        val catalog = quickEntryCandidates("")
        val ids = catalog.values.flatten().map { it.entry.id }
        assertTrue(catalog.size > 1)
        assertEquals(allQuickEntries.size, ids.size)
        assertEquals(allQuickEntries.map { it.id }.toSet(), ids.toSet())
        fun matches(query: String) = quickEntryCandidates(query).values.flatten().map { it.entry.id }
        assertEquals(listOf("extraction_hub"), matches(" 搜打撤 "))
        assertEquals(listOf("weapon_skins"), matches("枪皮"))
        assertEquals(listOf("items"), matches("pc道具"))
        assertTrue(matches("没有这种入口").isEmpty())
    }
}
