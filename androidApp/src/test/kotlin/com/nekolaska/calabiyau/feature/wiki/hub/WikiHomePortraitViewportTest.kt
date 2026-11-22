package com.nekolaska.calabiyau.feature.wiki.hub

import androidx.compose.animation.EnterExitState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WikiHomePortraitViewportTest {
    private val visible = EnterExitState.Visible

    @Test fun retainedOutgoingAndPredictiveIncomingLayoutsCannotStartPrefetch() {
        fun canPrefetch(current: EnterExitState, target: EnterExitState, animating: Boolean = false) =
            isPortraitRouteSettled(current, target, animating)

        assertFalse(canPrefetch(visible, EnterExitState.PostExit))
        assertFalse(canPrefetch(EnterExitState.PreEnter, visible))
        assertFalse(canPrefetch(visible, visible, animating = true))
        assertTrue(canPrefetch(visible, visible))
    }

    @Test fun prefetchRequiresAnActuallyVisiblePortraitRowAndBothAxesAtRest() {
        assertFalse(canPrefetchHomePortraits(rowVisible = false, pageScrolling = false, rowScrolling = false))
        assertFalse(canPrefetchHomePortraits(rowVisible = true, pageScrolling = true, rowScrolling = false))
        assertFalse(canPrefetchHomePortraits(rowVisible = true, pageScrolling = false, rowScrolling = true))
        assertTrue(canPrefetchHomePortraits(rowVisible = true, pageScrolling = false, rowScrolling = false))
    }
}
