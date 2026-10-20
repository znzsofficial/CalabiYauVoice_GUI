package com.nekolaska.calabiyau.feature.download

import data.SearchMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryFileSelectionStateTest {
    @Test
    fun aLateResponseCannotPopulateOrUnlockANewerDialog() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        try {
            val a = CompletableDeferred<List<Pair<String, String>>>()
            val b = CompletableDeferred<List<Pair<String, String>>>()
            val state = CategoryFileSelectionState(scope) { cat, _ ->
                withContext(NonCancellable) { if (cat == "A") a.await() else b.await() }
            }
            state.open("A", SearchMode.VOICE_ONLY, null)
            state.open("B", SearchMode.VOICE_ONLY, null)
            a.complete(listOf("A.wav" to "a"))
            assertEquals("B", state.category.value); assertTrue(state.loading.value)
            assertTrue(state.files.value.isEmpty())
            b.complete(listOf("B.wav" to "b"))
            assertEquals("B" to listOf("B.wav" to "b"), state.confirm(SearchMode.VOICE_ONLY))
            assertFalse(state.visible.value)
        } finally { scope.cancel() }
    }

    @Test
    fun failuresCannotBeConfirmedAsAnEmptyManualSelectionAndRetryUsesSameCategory() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        try {
            var fail = true
            val state = CategoryFileSelectionState(scope) { _, audio ->
                assertTrue(audio)
                if (fail) error("HTTP 567") else listOf("A.wav" to "a", "A.wav" to "a")
            }
            state.open("A", SearchMode.VOICE_ONLY, null)
            assertEquals("HTTP 567", state.error.value)
            assertNull(state.confirm(SearchMode.VOICE_ONLY))
            fail = false; state.open("A", SearchMode.VOICE_ONLY, emptyList())
            assertTrue(state.selected.value.isEmpty())
            state.selectAll(); assertEquals(setOf("a"), state.selected.value)
            assertNull(state.confirm(SearchMode.ALL_CATEGORIES))
            assertEquals("A" to listOf("A.wav" to "a"), state.confirm(SearchMode.VOICE_ONLY))
        } finally { scope.cancel() }
    }

    @Test
    fun closeOrModeSwitchInvalidatesEvenUncancellableReads() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        try {
            val read = CompletableDeferred<List<Pair<String, String>>>()
            val state = CategoryFileSelectionState(scope) { _, _ -> withContext(NonCancellable) { read.await() } }
            state.open("A", SearchMode.VOICE_ONLY, null)
            state.close()
            read.complete(listOf("A.wav" to "a"))
            assertFalse(state.visible.value); assertFalse(state.loading.value)
            assertTrue(state.files.value.isEmpty()); assertNull(state.confirm(SearchMode.VOICE_ONLY))
        } finally { scope.cancel() }
    }
}
