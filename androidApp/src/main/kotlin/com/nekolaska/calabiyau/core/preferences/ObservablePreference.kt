package com.nekolaska.calabiyau.core.preferences

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A persisted preference with one observable in-process source of truth. */
internal class ObservablePreference<T>(initialValue: T, private val persist: (T) -> Unit) {
    private val mutableState = MutableStateFlow(initialValue)
    val state = mutableState.asStateFlow()

    var value: T
        get() = state.value
        @Synchronized set(value) {
            persist(value)
            mutableState.value = value
        }
}
