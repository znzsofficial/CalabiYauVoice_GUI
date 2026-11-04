package com.nekolaska.calabiyau.core.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver

/** Keep an existing selection during empty prefetch/refresh; reconcile only against available data. */
internal fun reconcileSectionKey(selected: String?, available: List<String>): String? =
    if (available.isEmpty() || selected in available) selected else available.first()

@Stable
class SectionSelectionState(initial: String? = null) {
    var selectedKey by mutableStateOf(initial)
        private set
    fun select(key: String) { selectedKey = key }
    fun reconcile(keys: List<String>) { selectedKey = reconcileSectionKey(selectedKey, keys) }

    companion object {
        val Saver = listSaver<SectionSelectionState, String>(
            save = { listOfNotNull(it.selectedKey) },
            restore = { SectionSelectionState(it.firstOrNull()) }
        )
    }
}

@Composable
fun rememberSectionSelection(pageKey: String, availableKeys: List<String>): SectionSelectionState {
    val state = rememberSaveable(pageKey, saver = SectionSelectionState.Saver) { SectionSelectionState() }
    LaunchedEffect(state, availableKeys) { state.reconcile(availableKeys) }
    return state
}
