package com.nekolaska.calabiyau.feature.wiki.gallery

import kotlinx.coroutines.*
import kotlin.test.*

class WallpaperSelectionTest {
    @Test fun manualSelectionWinsOverAnOlderAutomaticResultAndConcurrentAutomaticDoesNotStart() = runBlocking {
        var pref: String? = null; val writes = mutableListOf<String>()
        val owner = WallpaperSelection({ pref }) { pref = it; writes += it }
        val a = CompletableDeferred<String>()
        val slow = async(start = CoroutineStart.UNDISPATCHED) { owner.select(false, true) { a.await() } }
        owner.select(false, false) { error("second automatic fetch") }
        assertEquals(WallpaperRefresh.UPDATED, owner.select(true, true) { "B" })
        a.complete("A"); assertEquals(WallpaperRefresh.RETAINED, slow.await())
        assertEquals(listOf("B"), writes)
        assertEquals(WallpaperRefresh.RETAINED, owner.select(true, true) { null })
    }
    @Test fun noListOnFirstReadIsNotAPermanentHit() = runBlocking {
        var pref: String? = null
        val owner = WallpaperSelection({ pref }) { pref = it }
        fun file(body: String): String? = WallpaperApi.parseFiles(body).firstOrNull()
        assertEquals(WallpaperRefresh.UNAVAILABLE, owner.select(false, false) { file("{}") })
        assertEquals(WallpaperRefresh.UPDATED, owner.select(true, false) {
            file("""{"parse":{"wikitext":{"*":"[[文件:壁纸-a.jpg|thumb]]"}}}""") })
        assertEquals("壁纸-a.jpg", pref)
    }
}
