package com.nekolaska.calabiyau.feature.wiki.hub

import kotlin.test.*

class WikiWebViewLifetimeTest {
    @Test fun excessivePendingPopupsAreRejectedAndTheRejectedViewIsStillDestroyed() {
        val main = Any(); val destroyed = mutableListOf<Any>()
        val lifetime = WikiWebViewLifetime<Any> { view, _ -> destroyed.add(view) }
        lifetime.attach(main)
        repeat(4) { assertTrue(lifetime.addPopup(main, Any())) }
        val extra = Any()
        assertFalse(lifetime.addPopup(main, extra)); assertEquals(listOf(extra), destroyed)
        lifetime.close(); assertEquals(6, destroyed.size)
    }

    @Test fun releasingTheParentClosesPopupAndTimerExactlyOnce() {
        val main = Any(); val popup = Any(); val destroyed = mutableListOf<Any>(); var cancelled = 0
        val lifetime = WikiWebViewLifetime<Any> { view, _ -> destroyed.add(view) }
        lifetime.attach(main); lifetime.addPopup(main, popup) { cancelled++ }
        lifetime.release(main); lifetime.closePopup(popup); lifetime.release(main)
        assertEquals(listOf(popup, main), destroyed); assertEquals(1, cancelled)
    }

    @Test fun oldPopupAndRendererCallbacksCannotTouchTheReplacementMainView() {
        val a = Any(); val b = Any(); val popup = Any(); val destroyed = mutableListOf<Any>()
        val lifetime = WikiWebViewLifetime<Any> { view, _ -> destroyed.add(view) }
        lifetime.attach(a); lifetime.addPopup(a, popup); lifetime.attach(b)
        assertFalse(lifetime.isMain(a)); assertFalse(lifetime.isPopup(popup)); assertTrue(lifetime.isMain(b))
        lifetime.release(a, true); lifetime.closePopup(popup, true)
        assertEquals(listOf(popup, a), destroyed); assertTrue(lifetime.isMain(b))
    }

    @Test fun crashedViewsAreDisposedWithoutCallingLiveRendererMethodsAndCloseIsIdempotent() {
        val main = Any(); val popup = Any(); val flags = mutableListOf<Boolean>()
        val lifetime = WikiWebViewLifetime<Any> { _, gone -> flags.add(gone) }
        lifetime.attach(main); lifetime.addPopup(main, popup)
        lifetime.release(main, true); lifetime.close(); lifetime.close()
        assertEquals(listOf(true, true), flags)
        assertFailsWith<IllegalStateException> { lifetime.attach(Any()) }
    }

    @Test fun anUnownedPopupIsImmediatelyDestroyedInsteadOfLeaking() {
        var destroyed = 0
        val lifetime = WikiWebViewLifetime<Any> { _, _ -> destroyed++ }
        assertFalse(lifetime.addPopup(Any(), Any())); assertEquals(1, destroyed)
    }
}
