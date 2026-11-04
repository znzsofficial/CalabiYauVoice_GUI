package com.nekolaska.calabiyau.core.ui

import androidx.compose.runtime.saveable.SaverScope

import kotlin.test.*

class CatalogStateTest {
    @Test fun sectionSaverCapturesTheCurrentSelectionWithoutWaitingForARecomposition() {
        val state = SectionSelectionState("甲")
        state.select("乙")
        val scope = SaverScope { true }
        val saved = with(SectionSelectionState.Saver) { scope.save(state) }
        val restored = SectionSelectionState.Saver.restore(assertNotNull(saved))
        assertEquals("乙", restored?.selectedKey)
    }
    @Test fun sectionSurvivesEmptyPrefetchAndReorderingButInvalidSelectionIsReconciled() {
        val state = SectionSelectionState("乙")
        state.reconcile(emptyList())
        assertEquals("乙", state.selectedKey)
        state.reconcile(listOf("乙", "甲"))
        assertEquals("乙", state.selectedKey)
        state.reconcile(listOf("甲"))
        assertEquals("甲", state.selectedKey)
    }

    @Test fun missingInitialSectionSelectsFirstOnlyAfterDataArrives() {
        val state = SectionSelectionState()
        state.reconcile(emptyList())
        assertNull(state.selectedKey)
        state.reconcile(listOf("甲", "乙"))
        assertEquals("甲", state.selectedKey)
        state.select("乙")
        assertEquals("乙", state.selectedKey)
    }

    @Test fun keysAreCollisionFreeForDuplicatesAndDelimiterLikeNames() {
        val names = listOf("a", "a", "a:0", "", "", "a:0:1")
        val keys = catalogKeys(names) { it }
        assertEquals(names.size, keys.toSet().size)
        assertEquals(keys, catalogKeys(names) { it })
        assertEquals(catalogKeys(listOf("乙", "甲")) { it }.reversed(), catalogKeys(listOf("甲", "乙")) { it })
    }
}
