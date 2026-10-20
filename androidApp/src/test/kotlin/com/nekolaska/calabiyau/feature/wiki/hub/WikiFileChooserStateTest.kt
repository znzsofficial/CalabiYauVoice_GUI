package com.nekolaska.calabiyau.feature.wiki.hub

import kotlin.test.*

class WikiFileChooserStateTest {
    @Test fun navigationWithinTheSameViewCancelsTheOldDocumentWithoutReassigningItsResult() {
        val owner = Any(); val results = mutableListOf<String?>()
        val picker = WikiFileChooserState<Any, String>(currentOwner = { true })
        picker.begin(owner) { results.add(it) }
        picker.invalidate(owner)
        assertNull(picker.begin(owner) { results.add(it) })
        picker.complete("old-document-file")
        picker.begin(owner) { results.add(it) }; picker.complete("new-document-file")
        assertEquals(listOf(null, null, "new-document-file"), results)
    }

    @Test fun overlappingLaunchIsRejectedInsteadOfReplacingTheOriginalCallback() {
        val owner = Any(); val a = mutableListOf<String?>(); val b = mutableListOf<String?>()
        val picker = WikiFileChooserState<Any, String>(currentOwner = { it === owner })
        assertNotNull(picker.begin(owner) { a.add(it) })
        assertNull(picker.begin(owner) { b.add(it) })
        picker.complete("A-file")
        assertEquals(listOf<String?>("A-file"), a); assertEquals(listOf<String?>(null), b)
    }

    @Test fun recoveryLeavesATombstoneSoAnOldResultCanNeverReachTheNewPage() {
        val aOwner = Any(); val bOwner = Any(); var current = aOwner
        val a = mutableListOf<String?>(); val b = mutableListOf<String?>()
        val picker = WikiFileChooserState<Any, String>(currentOwner = { it === current })
        picker.begin(aOwner) { a.add(it) }
        picker.invalidate(aOwner); current = bOwner
        assertNull(picker.begin(bOwner) { b.add(it) })
        picker.complete("late-A-file")
        assertEquals(listOf<String?>(null), a); assertEquals(listOf<String?>(null), b)
        picker.begin(bOwner) { b.add(it) }; picker.complete("B-file")
        assertEquals(listOf(null, "B-file"), b)
    }

    @Test fun ownerCheckStillRejectsAStaleResultIfExplicitInvalidationWasMissed() {
        val owner = Any(); var live = true; val results = mutableListOf<String?>()
        val picker = WikiFileChooserState<Any, String>(currentOwner = { live })
        picker.begin(owner) { results.add(it) }; live = false; picker.complete("file")
        assertEquals(listOf<String?>(null), results)
    }

    @Test fun onlyTheMatchingLaunchFailureCanClearTheActiveRequest() {
        val owner = Any(); val results = mutableListOf<String?>()
        val picker = WikiFileChooserState<Any, String>(currentOwner = { true })
        val first = checkNotNull(picker.begin(owner) { results.add(it) })
        picker.launchFailed(first)
        picker.begin(owner) { results.add(it) }
        picker.launchFailed(first); picker.complete("second")
        assertEquals(listOf(null, "second"), results)
    }

    @Test fun restoredOutstandingActivityResultIsConsumedBeforeAnyNewLaunch() {
        val owner = Any(); val results = mutableListOf<String?>()
        val picker = WikiFileChooserState<Any, String>(restoredPending = true, currentOwner = { true })
        assertNull(picker.begin(owner) { results.add(it) })
        picker.complete("pre-recreation-file")
        picker.begin(owner) { results.add(it) }; picker.complete("new")
        assertEquals(listOf(null, "new"), results)
    }

    @Test fun disposingCancelsExactlyOnceAndPreservesTheSavedPendingTombstone() {
        val owner = Any(); val results = mutableListOf<String?>(); val pending = mutableListOf<Boolean>()
        val picker = WikiFileChooserState<Any, String>(currentOwner = { true }, pendingChanged = { pending.add(it) })
        picker.begin(owner) { results.add(it) }; picker.dispose(); picker.dispose(); picker.complete("late")
        assertEquals(listOf<String?>(null), results); assertEquals(listOf(true), pending)
    }
}
