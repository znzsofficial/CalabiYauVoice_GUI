package com.nekolaska.calabiyau.core.cache

import kotlinx.coroutines.CancellationException

/** Preserve the last known good payload until a valid replacement is available. */
internal suspend fun fetchValidatedCachePayload(
    online: Boolean,
    network: suspend () -> String?,
    read: suspend () -> OfflineCache.CacheResult?,
    publish: suspend (String) -> Unit,
    validate: (String) -> Boolean,
    tryNetworkOnMiss: Boolean = false
): OfflineCache.CacheResult? {
    fun valid(payload: String): Boolean = try { validate(payload) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
    if (!online) {
        val cached = read()?.takeIf { valid(it.payload) }
        if (cached != null || !tryNetworkOnMiss) return cached
    }
    val fresh = network()
    if (fresh != null && valid(fresh)) {
        publish(fresh)
        return OfflineCache.CacheResult(fresh, false, 0)
    }
    return if (online) read()?.takeIf { valid(it.payload) } else null
}
