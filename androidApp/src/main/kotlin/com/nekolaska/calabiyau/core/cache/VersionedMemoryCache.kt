package com.nekolaska.calabiyau.core.cache

/** Latest-started request wins. Clearing also invalidates all outstanding writers. */
internal class VersionedMemoryCache<K, V> {
    private val values = mutableMapOf<K, V>()
    private val requests = mutableMapOf<K, Long>()
    private var version = 0L

    @Synchronized fun get(key: K): V? = values[key]
    @Synchronized fun begin(key: K): Long = (++version).also { requests[key] = it }
    @Synchronized fun publish(key: K, token: Long, value: V) {
        if (requests[key] == token) values[key] = value
    }
    @Synchronized fun put(key: K, value: V?) {
        begin(key)
        if (value == null) values.remove(key) else values[key] = value
    }
    @Synchronized fun clear() { values.clear(); requests.clear(); version++ }
}
