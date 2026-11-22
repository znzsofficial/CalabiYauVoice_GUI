package com.nekolaska.calabiyau.feature.wiki.hub

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.SingletonImageLoader
import com.nekolaska.calabiyau.core.wiki.prefetchWikiPortraits
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Only speculative portrait work is suspended; visible images remain mounted for shared transitions. */
@Composable
internal fun HubPortraitPrefetch(
    enabled: Boolean,
    sharedScope: SharedTransitionScope?,
    visibilityScope: AnimatedVisibilityScope?,
    window: () -> List<String?>
) {
    // A dialog from the permanent sidebar owns a separate window; the Activity stays RESUMED.
    if (!enabled || !LocalWindowInfo.current.isWindowFocused) return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val transition = visibilityScope?.transition
    val currentWindow by rememberUpdatedState(window)
    LaunchedEffect(context, lifecycle, transition, sharedScope) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {
                val settled = isPortraitRouteSettled(
                    transition?.currentState, transition?.targetState,
                    transition?.isRunning == true || sharedScope?.isTransitionActive == true
                )
                if (settled) currentWindow() else emptyList()
            }.collectLatest { urls ->
                if (urls.isNotEmpty()) {
                    delay(160)
                    SingletonImageLoader.get(context).prefetchWikiPortraits(context, urls)
                }
            }
        }
    }
}

internal fun isPortraitRouteSettled(current: EnterExitState?, target: EnterExitState?, animating: Boolean) =
    !animating && ((current == null && target == null) ||
        (current == EnterExitState.Visible && target == EnterExitState.Visible))
