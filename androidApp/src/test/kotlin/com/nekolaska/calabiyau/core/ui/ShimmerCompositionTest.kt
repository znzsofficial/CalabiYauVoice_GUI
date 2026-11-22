package com.nekolaska.calabiyau.core.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import kotlinx.coroutines.*
import kotlin.test.*

class ShimmerCompositionTest {
    @Test fun animatingNestedPlaceholdersDoesNotRecomposeThemEachFrame() = runBlocking {
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val composition = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertTopDown(index: Int, instance: Unit) = Unit
            override fun insertBottomUp(index: Int, instance: Unit) = Unit
            override fun move(from: Int, to: Int, count: Int) = Unit
            override fun remove(index: Int, count: Int) = Unit
            override fun onClear() = Unit
        }, recomposer)
        val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        var time = 0L
        suspend fun frame() {
            Snapshot.sendApplyNotifications(); yield()
            time += 16_666_667; clock.sendFrame(time); yield()
        }
        val phases = mutableSetOf<State<Float>>()
        var compositions = 0
        try {
            composition.setContent {
                ShimmerGroup {
                    val parent = checkNotNull(LocalShimmerPhase.current)
                    repeat(8) {
                        ShimmerGroup {
                            val phase = checkNotNull(LocalShimmerPhase.current)
                            Modifier.shimmerEffect()
                            SideEffect { phases.add(parent); phases.add(phase); compositions++ }
                        }
                    }
                }
            }
            repeat(6) { frame() }
            val phase = phases.single()
            val before = phase.value
            val count = compositions
            repeat(15) { frame() }
            assertTrue(count >= 8)
            assertNotEquals(before, phase.value, "The animation must actually advance")
            assertEquals(count, compositions, "Progress belongs to the draw phase")
        } finally {
            composition.dispose(); recomposer.close(); runner.cancelAndJoin()
        }
    }
}
