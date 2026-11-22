package com.nekolaska.calabiyau.core.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Selecting Home from another destination resumes; reselecting it explicitly goes to the root. */
internal fun shouldResetHubOnSelection(current: DrawerDestination, target: DrawerDestination): Boolean =
    current == DrawerDestination.WIKI_HUB && target == DrawerDestination.WIKI_HUB

internal fun mainDestinationStateKey(destination: DrawerDestination, hubGeneration: Int): String =
    if (destination == DrawerDestination.WIKI_HUB) "${destination.name}:$hubGeneration" else destination.name

internal fun modalDrawerCoversContent(
    permanentDrawer: Boolean,
    openOrOpening: Boolean,
    animating: Boolean,
    offsetPx: Float = Float.NaN,
    drawerWidthPx: Int = 0
): Boolean = !permanentDrawer && (openOrOpening || animating ||
    (offsetPx.isFinite() && drawerWidthPx > 0 && offsetPx > -drawerWidthPx + 0.5f))

internal fun shouldRecordDrawerBackdrop(glassSupportedAndEnabled: Boolean, permanentDrawer: Boolean, modalDrawerVisible: Boolean) =
    glassSupportedAndEnabled && (permanentDrawer || modalDrawerVisible)

/** New shortcuts/back/resize can invalidate a pending click before the close animation finishes. */
internal class DrawerNavigationQueue(private val scope: CoroutineScope) {
    private var pending: Job? = null

    fun cancel() {
        pending?.cancel()
        pending = null
    }

    fun navigate(closeDrawer: suspend () -> Unit, action: () -> Unit) {
        cancel()
        pending = scope.launch {
            closeDrawer()
            ensureActive()
            action()
        }
    }
}
/** All Wiki browser entry points preserve the first non-browser return destination. */
internal fun wikiReturnDestination(current: DrawerDestination, previous: DrawerDestination): DrawerDestination =
    if (current != DrawerDestination.WIKI) current else if (previous != DrawerDestination.WIKI) previous else DrawerDestination.WIKI_HUB
