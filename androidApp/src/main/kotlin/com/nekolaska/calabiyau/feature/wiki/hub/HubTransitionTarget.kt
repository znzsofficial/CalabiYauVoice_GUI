package com.nekolaska.calabiyau.feature.wiki.hub

/** Direction belongs to a transition segment, never to a mutable screen-wide back flag. */
internal data class HubTransitionTarget(val route: WikiRoute, val depth: Int) {
    fun isPopFrom(previous: HubTransitionTarget): Boolean = depth < previous.depth
}

internal fun HubTransitionTarget.openWeaponUser(stack: List<WikiRoute>, overlaid: Boolean, user: String,
    resolve: () -> WikiRoute.CharDetail?): List<WikiRoute> {
    if (overlaid || depth != stack.lastIndex || route != stack.lastOrNull()) return stack
    val index = stack.indexOfLast { it is WikiRoute.CharDetail && it.name == user }
    return if (index >= 0) stack.take(index + 1) else resolve()?.let { stack + it } ?: stack
}
