package com.nekolaska.calabiyau.core.ui

import kotlin.test.*

class BackGestureOwnerTest {
    @Test fun externalNavigationInvalidatesLateCommitAndCancel() {
        val owner = BackGestureOwner("list")
        val gesture = assertNotNull(owner.begin("list", "home"))
        owner.observe("detail")
        assertFalse(owner.owns(gesture))
        assertFalse(owner.finish(gesture))
        assertNull(owner.active)
        assertNull(owner.begin("list", "home"))
    }

    @Test fun oldCancellationCannotReleaseANewerGestureEvenOnTheSamePage() {
        val owner = BackGestureOwner("list")
        val first = assertNotNull(owner.begin("list", "home"))
        val second = assertNotNull(owner.begin("list", "home"))
        assertFalse(owner.finish(first))
        assertTrue(owner.owns(second))
        assertSame(second, owner.active)
        assertTrue(owner.finish(second))
        assertFalse(owner.finish(second))
    }

    @Test fun routeRoundTripDoesNotReviveAnOldGesture() {
        val owner = BackGestureOwner("list")
        val gesture = assertNotNull(owner.begin("list", "home"))
        owner.observe("home")
        owner.observe("list")
        assertFalse(owner.owns(gesture))
        assertFalse(owner.finish(gesture))
    }

    @Test fun cancellationReleasesOwnershipWithoutCommittingTheTarget() {
        val owner = BackGestureOwner("list")
        val gesture = assertNotNull(owner.begin("list", "home"))
        assertTrue(owner.finish(gesture))
        assertNotNull(owner.begin("list", "home"))
        assertNull(owner.begin("home", "other"))
    }

    @Test fun nullableToolDestinationsAreValidGestureTargets() {
        val owner = BackGestureOwner<String?>("image")
        val gesture = assertNotNull(owner.begin("image", null))
        assertNull(gesture.target)
        owner.observe(null)
        assertFalse(owner.finish(gesture))
        assertNotNull(owner.begin(null, "image"))
    }

    @Test fun disabledOrDisposedHandlerCannotCommitAnOutstandingGesture() {
        val owner = BackGestureOwner("list")
        val gesture = assertNotNull(owner.begin("list", "home"))
        owner.invalidate()
        assertFalse(owner.owns(gesture))
        assertFalse(owner.finish(gesture))
    }
}
