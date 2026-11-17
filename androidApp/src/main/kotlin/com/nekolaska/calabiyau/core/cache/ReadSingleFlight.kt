package com.nekolaska.calabiyau.core.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Read-only coalescing. A departing owner cannot cancel a still-active follower. */
internal class ReadSingleFlight<K, V> {
    private val active = ConcurrentHashMap<K, CompletableDeferred<V>>()

    suspend fun run(key: K, read: suspend () -> V): V {
        var takeovers = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val mine = CompletableDeferred<V>()
            val previous = active.putIfAbsent(key, mine)
            if (previous != null) {
                try { return previous.await() }
                catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    active.remove(key, previous)
                    if (takeovers++ >= 1) throw IOException("读取中断，可重试", e)
                    continue
                }
            }
            try {
                val value = read()
                currentCoroutineContext().ensureActive()
                mine.complete(value)
                return value
            } catch (e: CancellationException) {
                mine.cancel(e)
                throw e
            } catch (e: Throwable) {
                mine.completeExceptionally(e)
                throw e
            } finally { active.remove(key, mine) }
        }
    }
}
