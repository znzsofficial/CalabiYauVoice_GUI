package com.nekolaska.calabiyau.feature.download

import data.CharacterGroup
import data.SearchMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryScanRetryTest {
    private val a = CharacterGroup("A", "分类:A", listOf("分类:A语音"))
    private val b = CharacterGroup("B", "分类:B", emptyList())

    @Test
    fun retryScansOnlyTheOriginalSeedsAndKeepsTheDetailOpen() = withScanScope {
        val calls = mutableListOf<List<String>>()
        val response = CompletableDeferred<List<String>>()
        val vm = SearchViewModel(this) { roots ->
            calls.add(roots)
            if (calls.size == 1) error("HTTP 567")
            response.await()
        }
        val searchErrors = mutableListOf<String>()
        launch { vm.errorEvent.collect { searchErrors.add(it) } }
        vm.onSearchKeywordChange("语音")
        vm.onSelectGroup(a)

        val failure = assertNotNull(vm.categoryScanFailure.value)
        assertTrue("HTTP 567" in failure.message)
        assertTrue(searchErrors.isEmpty())
        assertNull(vm.searchError.value)
        vm.retryCategoryScan(failure)
        vm.retryCategoryScan(failure) // A double tap while the new scan is pending.
        assertNull(vm.categoryScanFailure.value)
        assertTrue(vm.isScanningTree.value)
        val seeds = listOf("分类:A", "分类:A语音")
        assertEquals(listOf(seeds, seeds), calls)

        val categories = seeds + "分类:语音/中文"
        response.complete(categories)
        assertFalse(vm.isScanningTree.value)
        assertEquals(categories, vm.subCategories.value)
        assertEquals(categories, vm.checkedCategories.value)
        assertEquals(a, vm.selectedGroup.value)
        assertEquals("语音", vm.searchKeyword.value)
    }

    @Test
    fun staleRetryCannotReopenOrReplaceADetailEvenWhenTheSameGroupFailsAgain() = withScanScope {
        var calls = 0
        val vm = SearchViewModel(this) { calls++; error("offline") }
        vm.onSelectGroup(a)
        val first = assertNotNull(vm.categoryScanFailure.value)
        vm.clearSelectedGroup()
        vm.retryCategoryScan(first)
        assertNull(vm.selectedGroup.value)

        vm.onSelectGroup(a)
        val reopened = assertNotNull(vm.categoryScanFailure.value)
        vm.retryCategoryScan(first)
        assertEquals(reopened, vm.categoryScanFailure.value)

        vm.onSelectGroup(b)
        val other = assertNotNull(vm.categoryScanFailure.value)
        vm.retryCategoryScan(reopened)
        assertEquals(b, vm.selectedGroup.value)
        assertEquals(other, vm.categoryScanFailure.value)
        vm.onSearchKeywordChange("新关键词")
        vm.retryCategoryScan(other)
        assertNull(vm.selectedGroup.value)
        assertNull(vm.categoryScanFailure.value)

        vm.onSelectGroup(b)
        val beforeModeChange = assertNotNull(vm.categoryScanFailure.value)
        vm.onSearchModeChange(SearchMode.FILE_SEARCH)
        vm.retryCategoryScan(beforeModeChange)
        assertNull(vm.selectedGroup.value)
        assertNull(vm.categoryScanFailure.value)
        assertEquals(4, calls)
    }

    @Test
    fun lateFailureFromRetriedGroupCannotInterruptTheNextGroupScan() = withScanScope {
        val late = CompletableDeferred<List<String>>()
        val current = CompletableDeferred<List<String>>()
        var calls = 0
        val vm = SearchViewModel(this) { roots ->
            if (++calls == 1) error("offline")
            withContext(NonCancellable) { (if (a.rootCategory in roots) late else current).await() }
        }
        vm.onSelectGroup(a)
        vm.retryCategoryScan(assertNotNull(vm.categoryScanFailure.value))
        vm.onSelectGroup(b)
        late.completeExceptionally(IllegalStateException("late failure"))
        assertNull(vm.categoryScanFailure.value)
        assertTrue(vm.isScanningTree.value)
        current.complete(listOf(b.rootCategory))
        assertEquals(b, vm.selectedGroup.value)
        assertEquals(listOf(b.rootCategory), vm.checkedCategories.value)
        assertFalse(vm.isScanningTree.value)
    }

    private fun withScanScope(block: CoroutineScope.() -> Unit) {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        try { scope.block() } finally { scope.cancel() }
    }
}
