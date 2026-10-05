package com.nekolaska.calabiyau.core.preferences

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ObservablePreferenceTest {
    @Test
    fun wallpaperChangeReachesBothActiveConsumersAndRecreatedScreen() = runBlocking {
        var stored: String? = "old-wallpaper"
        val preference = ObservablePreference(stored) { stored = it }
        val hub = mutableListOf<String?>()
        val theme = mutableListOf<String?>()
        val hubJob = launch(start = CoroutineStart.UNDISPATCHED) { preference.state.collect { hub.add(it) } }
        val themeJob = launch(start = CoroutineStart.UNDISPATCHED) { preference.state.collect { theme.add(it) } }

        preference.value = "new-wallpaper"
        yield()

        assertEquals("new-wallpaper", stored)
        assertEquals(listOf<String?>("old-wallpaper", "new-wallpaper"), hub)
        assertEquals(hub, theme)
        // A Hub recreated after visiting settings starts from the new URL, not a singleton snapshot.
        assertEquals("new-wallpaper", preference.state.value)
        val afterRestart = ObservablePreference(stored) { stored = it }
        assertEquals("new-wallpaper", afterRestart.value)

        preference.value = null
        yield()
        assertEquals(listOf("old-wallpaper", "new-wallpaper", null), hub)
        assertEquals(hub, theme)
        assertEquals(null, stored)
        hubJob.cancel()
        themeJob.cancel()
    }

    @Test
    fun failedPersistenceDoesNotPublishUnsavedWallpaper() {
        val preference = ObservablePreference("old-wallpaper") { error("Write failed") }
        assertFailsWith<IllegalStateException> { preference.value = "new-wallpaper" }
        assertEquals("old-wallpaper", preference.value)
        assertEquals("old-wallpaper", preference.state.value)
    }
}
