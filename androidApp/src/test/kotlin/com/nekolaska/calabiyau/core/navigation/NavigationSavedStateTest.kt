package com.nekolaska.calabiyau.core.navigation

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Runs the actual Compose saveable machinery, without Activities, network, or Android views. */
class NavigationSavedStateTest {
    private class NoUiApplier : AbstractApplier<Unit>(Unit) {
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    @Test fun destinationRemovalRestoresLightweightUiStateAndResetStartsFresh() {
        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(NoUiApplier(), recomposer)
        lateinit var value: MutableState<String>
        fun render(destination: DrawerDestination, generation: Int = 0) {
            composition.setContent {
                val holder = rememberSaveableStateHolder()
                holder.SaveableStateProvider(mainDestinationStateKey(destination, generation)) {
                    value = rememberSaveable { mutableStateOf("initial") }
                }
            }
        }
        try {
            render(DrawerDestination.WIKI_HUB)
            value.value = "characters:scroll=12:query=test"
            render(DrawerDestination.SETTINGS)
            assertEquals("initial", value.value)
            render(DrawerDestination.WIKI_HUB)
            assertEquals("characters:scroll=12:query=test", value.value)
            render(DrawerDestination.WIKI_HUB, generation = 1)
            assertEquals("initial", value.value)
        } finally {
            composition.dispose()
            recomposer.close()
        }
    }

    @Test fun movableContentUsesLatestLayoutCallbackAfterResize() {
        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(NoUiApplier(), recomposer)
        lateinit var openMenu: () -> Boolean
        fun render(expanded: Boolean) {
            composition.setContent {
                val callback = rememberUpdatedState<() -> Boolean> { !expanded }
                val moved = remember { movableContentOf { openMenu = { callback.value() } } }
                moved()
            }
        }
        try {
            render(true)
            assertFalse(openMenu())
            render(false)
            assertEquals(true, openMenu())
        } finally {
            composition.dispose()
            recomposer.close()
        }
    }

    @Test fun savedInactiveHubSurvivesRegistryRecreationOnAnotherPage() {
        var registry = SaveableStateRegistry(null) { true }
        var recomposer = Recomposer(EmptyCoroutineContext)
        var composition = Composition(NoUiApplier(), recomposer)
        lateinit var value: MutableState<String>
        fun render(destination: DrawerDestination) {
            composition.setContent {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    val holder = rememberSaveableStateHolder()
                    holder.SaveableStateProvider(mainDestinationStateKey(destination, 0)) {
                        value = rememberSaveable { mutableStateOf("initial") }
                    }
                }
            }
        }
        try {
            render(DrawerDestination.WIKI_HUB)
            value.value = "maps:scroll=7"
            render(DrawerDestination.SETTINGS)
            val saved = registry.performSave()
            composition.dispose()
            recomposer.close()
            registry = SaveableStateRegistry(saved) { true }
            recomposer = Recomposer(EmptyCoroutineContext)
            composition = Composition(NoUiApplier(), recomposer)
            render(DrawerDestination.SETTINGS)
            render(DrawerDestination.WIKI_HUB)
            assertEquals("maps:scroll=7", value.value)
        } finally {
            composition.dispose()
            recomposer.close()
        }
    }
}
