package com.nekolaska.calabiyau.core.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellationException

/** One owner for route animations and gesture seeking. Cancelled callbacks never animate old routes. */
class PredictiveBackTransition<T> internal constructor(initialState: T) {
    val state = SeekableTransitionState(initialState)
    internal val gestures = BackGestureOwner(initialState)
}

internal class BackGesture<T>(val origin: T, val target: T)

internal class BackGestureOwner<T>(initialState: T) {
    private var current = initialState
    var active by mutableStateOf<BackGesture<T>?>(null)
        private set

    fun observe(state: T) {
        current = state
        if (active?.origin != state) active = null
    }

    fun begin(origin: T, target: T): BackGesture<T>? {
        if (current != origin) return null
        return BackGesture(origin, target).also { active = it }
    }

    fun owns(gesture: BackGesture<T>): Boolean = active === gesture && current == gesture.origin

    fun invalidate() { active = null }

    fun finish(gesture: BackGesture<T>): Boolean {
        if (!owns(gesture)) return false
        active = null
        return true
    }
}

@Composable
fun <T> rememberPredictiveBackTransition(
    currentState: T,
    resetKey: Any? = Unit
): PredictiveBackTransition<T> {
    val controller = remember(resetKey) { PredictiveBackTransition(currentState) }
    val gesture = controller.gestures.active
    DisposableEffect(controller) { onDispose { controller.gestures.invalidate() } }
    SideEffect { controller.gestures.observe(currentState) }
    LaunchedEffect(controller, currentState, gesture) {
        // A gesture cancels this effect. Its finish only releases ownership; this effect alone
        // animates to the latest committed route, including after cancellation or external navigation.
        if (gesture == null || gesture.origin != currentState) {
            val state = controller.state
            if (state.currentState != currentState || state.targetState != currentState) {
                state.animateTo(currentState)
            }
        }
    }
    return controller
}

@Composable
fun <T> SeekablePredictiveBackHandler(
    enabled: Boolean,
    currentState: T,
    targetState: T,
    transitionState: PredictiveBackTransition<T>,
    onCommit: () -> Unit,
    onStart: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    resolveTarget: (() -> T?)? = null
) {
    SideEffect { if (!enabled) transitionState.gestures.invalidate() }
    PredictiveBackHandler(enabled = enabled) { progress ->
        if (!enabled) return@PredictiveBackHandler
        // Null from an explicit resolver means the back stack has gone away, not a stale fallback.
        val seekTarget = if (resolveTarget != null) resolveTarget() ?: return@PredictiveBackHandler else targetState
        val owner = transitionState.gestures
        val gesture = owner.begin(currentState, seekTarget) ?: return@PredictiveBackHandler
        val state = transitionState.state
        // Don't reset an unfinished forward transition's fraction to a new gesture's zero.
        // A quick back still commits normally; interactive seeking starts only from a settled page.
        val canSeek = state.currentState == currentState && state.targetState == currentState
        try {
            onStart?.invoke()
            progress.collect { event ->
                if (canSeek && owner.owns(gesture)) {
                    state.seekTo(predictiveBackFraction(event.progress), gesture.target)
                }
            }
            if (owner.owns(gesture)) onCommit()
        } catch (error: CancellationException) {
            if (owner.owns(gesture)) onCancel?.invoke()
            throw error
        } finally {
            owner.finish(gesture)
        }
    }
}
