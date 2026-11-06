package com.nekolaska.calabiyau.feature.wiki.hub

/** Direction belongs to a transition segment, never to a mutable screen-wide back flag. */
internal data class HubTransitionTarget(val route: WikiRoute, val depth: Int) {
    fun isPopFrom(previous: HubTransitionTarget): Boolean = depth < previous.depth
}
