package com.nekolaska.calabiyau.core.ui

/** Assign before filtering/sorting. Repeated source identities stay distinct without hash collisions. */
fun <T> catalogKeys(items: List<T>, identity: (T) -> String): List<String> {
    val counts = mutableMapOf<String, Int>()
    return items.map { item ->
        val base = identity(item)
        val occurrence = counts.getOrDefault(base, 0)
        counts[base] = occurrence + 1
        "${base.length}:$base:$occurrence"
    }
}
