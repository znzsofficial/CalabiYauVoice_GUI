package com.nekolaska.calabiyau.core.cache

object MemoryCacheRegistry {

    private val clearers = linkedMapOf<String, () -> Unit>()

    fun register(key: String, clearer: () -> Unit) {
        synchronized(clearers) {
            clearers[key] = clearer
        }
    }

    fun unregister(key: String) {
        synchronized(clearers) {
            clearers.remove(key)
        }
    }

    fun clearAll() {
        val snapshot = synchronized(clearers) { clearers.values.toList() }
        snapshot.forEach { clearer ->
            runCatching { clearer() }
        }
    }
}