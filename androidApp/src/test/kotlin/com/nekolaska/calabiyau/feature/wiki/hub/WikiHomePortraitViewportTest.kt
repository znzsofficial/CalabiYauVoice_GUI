package com.nekolaska.calabiyau.feature.wiki.hub

import androidx.compose.animation.EnterExitState
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WikiHomePortraitViewportTest {
    private val viewport = Rect(0f, 64f, 360f, 720f)
    private val visible = EnterExitState.Visible

    @Test fun retainedOutgoingAndPredictiveIncomingLayoutsCannotStartPrefetch() {
        val portraits = Rect(16f, 540f, 344f, 756f)
        fun canPrefetch(current: EnterExitState, target: EnterExitState, animating: Boolean = false) =
            isSettledPortraitViewport(viewport, portraits, current, target, animating)

        assertFalse(canPrefetch(visible, EnterExitState.PostExit))
        assertFalse(canPrefetch(EnterExitState.PreEnter, visible))
        assertFalse(canPrefetch(visible, visible, animating = true))
        assertTrue(canPrefetch(visible, visible))
    }

    @Test fun visibleSectionHeaderDoesNotImplyItsPortraitRowIsOnScreen() {
        fun canPrefetch(row: Rect) = isSettledPortraitViewport(viewport, row, visible, visible, false)
        assertFalse(canPrefetch(Rect.Zero))
        assertFalse(canPrefetch(Rect(16f, 720f, 344f, 936f)))
        assertFalse(canPrefetch(Rect(16f, 0f, 344f, 64f)))
        assertTrue(canPrefetch(Rect(16f, 710f, 344f, 926f)))
    }
}
