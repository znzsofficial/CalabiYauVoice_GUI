package com.nekolaska.calabiyau.core.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.*
import kotlin.test.*

/** Exercise real Compose transition cancellation with a manually advanced frame clock, without a device. */
class PredictiveBackTransitionTest {
    private class NoUiApplier : AbstractApplier<Unit>(Unit) {
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    @Test fun rapidReturnAndStaleGestureCleanupSettleOnlyOnTheLatestRoute() = runBlocking {
        withTimeout(5_000) {
            val clock = BroadcastFrameClock()
            val recomposer = Recomposer(coroutineContext + clock)
            val composition = Composition(NoUiApplier(), recomposer)
            val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
            val route = mutableStateOf(0)
            lateinit var controller: PredictiveBackTransition<Int>
            var visibleValue = -1f
            var time = 0L
            suspend fun frames(count: Int) {
                repeat(count) {
                    Snapshot.sendApplyNotifications()
                    yield()
                    time += 16_666_667L
                    clock.sendFrame(time)
                    yield()
                }
            }
            try {
                composition.setContent {
                    controller = rememberPredictiveBackTransition(route.value)
                    val transition = rememberTransition(controller.state)
                    val value by transition.animateFloat(transitionSpec = { tween(160) }, label = "test-page") { it.toFloat() }
                    SideEffect { visibleValue = value }
                }
                frames(5)
                route.value = 1
                frames(4) // Return before the forward transition finishes.
                route.value = 0
                frames(40)
                assertEquals(0, controller.state.currentState)
                assertEquals(0f, visibleValue, 0.001f)
                frames(20)
                assertEquals(0f, visibleValue, 0.001f, "Outgoing route must not replay at the tail")

                route.value = 1
                frames(35)
                val cancelled = assertNotNull(controller.gestures.begin(1, 0))
                frames(3)
                val cancelSeek = launch(clock) { controller.state.seekTo(0.6f, 0) }
                frames(12)
                cancelSeek.join()
                assertTrue(controller.gestures.finish(cancelled))
                frames(35)
                assertEquals(1, controller.state.currentState)
                assertEquals(1f, visibleValue, 0.001f, "Cancelled preview restores the committed page")

                val gesture = assertNotNull(controller.gestures.begin(1, 0))
                frames(3)
                val seek = launch(clock) { controller.state.seekTo(0.75f, 0) }
                frames(12)
                seek.join()
                route.value = 2 // Navigation wins while the old gesture is still in flight.
                frames(35)
                assertFalse(controller.gestures.finish(gesture))
                frames(20)
                assertEquals(2, controller.state.currentState)
                assertEquals(2, controller.state.targetState)
                assertEquals(2f, visibleValue, 0.001f)
            } finally {
                composition.dispose()
                recomposer.close()
                runner.cancelAndJoin()
            }
        }
    }
}
