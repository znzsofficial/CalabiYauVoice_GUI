package com.nekolaska.calabiyau.core.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellationException

@Composable
fun <T> rememberPredictiveBackTransition(
    currentState: T,
    resetKey: Any? = Unit
): SeekableTransitionState<T> {
    val transitionState = remember(resetKey) { SeekableTransitionState(currentState) }
    LaunchedEffect(currentState) {
        if (transitionState.currentState != currentState || transitionState.targetState != currentState) {
            transitionState.animateTo(currentState)
        }
    }
    return transitionState
}

@Composable
fun <T> SeekablePredictiveBackHandler(
    enabled: Boolean,
    currentState: T,
    targetState: T,
    transitionState: SeekableTransitionState<T>,
    onCommit: () -> Unit,
    onStart: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    resolveTarget: (() -> T?)? = null
) {
    PredictiveBackHandler(enabled = enabled) { progress ->
        val seekTarget = resolveTarget?.invoke() ?: targetState
        onStart?.invoke()
        try {
            progress.collect { event ->
                transitionState.seekTo(predictiveBackFraction(event.progress), seekTarget)
            }
            onCommit()
        } catch (error: CancellationException) {
            onCancel?.invoke()
            transitionState.animateTo(currentState)
            throw error
        }
    }
}
