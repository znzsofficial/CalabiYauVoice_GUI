package com.nekolaska.calabiyau.core.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class MainNavigationPolicyTest {
    @Test fun explicitResetHasANewSavedSubtreeButSwitchingOtherPagesDoesNot() {
        assertNotEquals(mainDestinationStateKey(DrawerDestination.WIKI_HUB, 0),
            mainDestinationStateKey(DrawerDestination.WIKI_HUB, 1))
        assertEquals(mainDestinationStateKey(DrawerDestination.DOWNLOADER, 0),
            mainDestinationStateKey(DrawerDestination.DOWNLOADER, 1))
    }
    @Test fun switchingBackResumesButExplicitHomeReselectionResets() {
        for (source in DrawerDestination.entries) {
            if (source == DrawerDestination.WIKI_HUB) continue
            assertFalse(shouldResetHubOnSelection(source, DrawerDestination.WIKI_HUB))
        }
        assertTrue(shouldResetHubOnSelection(DrawerDestination.WIKI_HUB, DrawerDestination.WIKI_HUB))
        assertFalse(shouldResetHubOnSelection(DrawerDestination.WIKI_HUB, DrawerDestination.SETTINGS))
    }

    @Test fun hiddenUnsupportedAndLandscapeDrawersDoNotRecordTheWholePage() {
        assertFalse(shouldRecordDrawerBackdrop(false, true, true, true))
        assertFalse(shouldRecordDrawerBackdrop(true, false, false, false))
        assertTrue(shouldRecordDrawerBackdrop(true, true, false, false))
        assertTrue(shouldRecordDrawerBackdrop(true, false, true, false))
        assertTrue(shouldRecordDrawerBackdrop(true, false, false, true))
        assertFalse(shouldRecordDrawerBackdrop(true, false, false, false, -300f, 300))
        // During a drag both anchors can remain Closed, without any animation running.
        assertTrue(shouldRecordDrawerBackdrop(true, false, false, false, -290f, 300))
        assertFalse(shouldRecordDrawerBackdrop(false, false, false, false, -290f, 300))
    }

    @Test fun shortcutOrBackCancelsPendingDrawerNavigation() = runBlocking {
        val queue = DrawerNavigationQueue(this)
        val closing = CompletableDeferred<Unit>()
        var destination = "home"
        queue.navigate({ closing.await() }) { destination = "settings" }
        yield()
        assertEquals("home", destination)
        queue.cancel()
        destination = "shortcut:characters"
        closing.complete(Unit)
        yield()
        assertEquals("shortcut:characters", destination)
    }

    @Test fun rapidClicksApplyOnlyTheLastNavigationAfterClosing() = runBlocking {
        val queue = DrawerNavigationQueue(this)
        val closing = CompletableDeferred<Unit>()
        var destination = "home"
        queue.navigate({ closing.await() }) { destination = "settings" }
        yield()
        queue.navigate({ closing.await() }) { destination = "downloader" }
        yield()
        closing.complete(Unit)
        yield()
        assertEquals("downloader", destination)
    }
}
