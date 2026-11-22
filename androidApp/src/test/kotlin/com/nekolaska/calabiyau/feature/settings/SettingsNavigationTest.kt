package com.nekolaska.calabiyau.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SettingsNavigationTest {
    @Test fun restoredDetailsReturnToTheirOwnCategoryBeforeHome() {
        for ((category, detail) in listOf(
            SettingsPage.DOWNLOADS to SettingsPage.STORAGE,
            SettingsPage.APPEARANCE to SettingsPage.WALLPAPER,
            SettingsPage.APPEARANCE to SettingsPage.COLORS
        )) {
            val path = SettingsNavigation().open(SettingsPage.HOME, category).open(category, detail)
            val restored = SettingsNavigation.restore(path.save())
            assertEquals(detail, restored.current)
            assertEquals(category, restored.previous)
            val parent = restored.pop(detail)
            assertEquals(category, parent.current)
            val home = parent.pop(category)
            assertEquals(SettingsPage.HOME, home.current)
            assertNull(home.previous)
        }
    }

    @Test fun outgoingToolbarOrLateGestureCannotPopOrPushOverAnotherPage() {
        val category = SettingsNavigation().open(SettingsPage.HOME, SettingsPage.DOWNLOADS)
        val detail = category.open(SettingsPage.DOWNLOADS, SettingsPage.STORAGE)
        assertSame(detail, detail.pop(SettingsPage.DOWNLOADS))
        assertSame(detail, detail.open(SettingsPage.HOME, SettingsPage.QUICK_ENTRIES))
        val returned = detail.pop(SettingsPage.STORAGE)
        assertSame(returned, returned.pop(SettingsPage.STORAGE))
    }

    @Test fun invalidRestoredBranchStopsAtLastReachablePage() {
        assertEquals(SettingsPage.APPEARANCE, SettingsNavigation.restore(listOf("HOME", "APPEARANCE", "STORAGE")).current)
        assertEquals(SettingsPage.DOWNLOADS, SettingsNavigation.restore(listOf("HOME", "DOWNLOADS", "RETIRED", "STORAGE")).current)
        assertEquals(SettingsPage.HOME, SettingsNavigation.restore(listOf("STORAGE")).current)
    }
}
