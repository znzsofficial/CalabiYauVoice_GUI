package com.nekolaska.calabiyau.core.wiki

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Visible items first, then at most two neighbours in total; never seed an offscreen list. */
internal fun portraitPrefetchIndices(
    itemCount: Int,
    visibleIndices: Collection<Int>,
    isVisible: Boolean = true,
    neighbourCount: Int = 2
): List<Int> {
    if (!isVisible || itemCount <= 0) return emptyList()
    val visible = visibleIndices.filter { it in 0 until itemCount }.distinct().sorted()
    if (visible.isEmpty()) return emptyList()
    val result = visible.toMutableList()
    var before = visible.first() - 1
    var after = visible.last() + 1
    var remaining = neighbourCount.coerceIn(0, 2)
    while (remaining > 0 && (after < itemCount || before >= 0)) {
        if (after < itemCount) {
            result.add(after++)
            remaining--
        }
        if (remaining > 0 && before >= 0) {
            result.add(before--)
            remaining--
        }
    }
    return result
}

/** One shared budget across home/list callers, with ownership-safe, temporary deduplication. */
internal class PortraitPrefetchBudget(concurrency: Int = 2) {
    private val semaphore = Semaphore(concurrency.coerceAtLeast(1))
    private val inflight = ConcurrentHashMap<String, Any>()

    suspend fun run(url: String, block: suspend () -> Unit) {
        currentCoroutineContext().ensureActive()
        val owner = Any()
        if (inflight.putIfAbsent(url, owner) != null) return
        try {
            semaphore.withPermit {
                currentCoroutineContext().ensureActive()
                block()
            }
        } finally {
            // A cancelled/duplicate caller must never release another caller's claim.
            inflight.remove(url, owner)
        }
    }
}
