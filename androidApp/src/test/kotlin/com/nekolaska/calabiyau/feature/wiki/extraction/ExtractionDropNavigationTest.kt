package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import data.SharedJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.test.*

class ExtractionDropNavigationTest {
    @Test fun directSourcesEntryRestoresToItsOwnRootWithoutAnItemsStop() {
        val root = DropNavigation(DropTask.SOURCES)
        val fromSource = root.openSource("boss-a", "关键词")
        val exploring = fromSource.openItem(ExtractionDropItem("材料", "Q8", 8))
        val restored = SharedJson.decodeFromString<DropNavigation>(SharedJson.encodeToString(exploring))
        assertEquals(fromSource, restored.back())
        val returned = restored.back().back()
        assertEquals(DropTask.SOURCES, returned.current.task)
        assertFalse(returned.canGoBack)
    }

    @Test fun itemSourcesBackRestoresTheListBeforeLeavingTheCatalog() {
        val root = DropNavigation()
        val item = root.openItem(ExtractionDropItem("切割器", "卓越", 3))
        val detail = item.openContainer("container", "切割器", "卓越")
        assertEquals(item, detail.back())
        assertEquals(root, item.back())
        assertFalse(root.canGoBack)
        assertEquals(root, root.back())
    }

    @Test fun crossItemExplorationRestoresTheOriginalContainerAndItsQueryAfterSaving() {
        val origin = DropNavigation().selectTask(DropTask.SOURCES).openSource("source-a", "切割器").openLinkedPool()
        val exploring = origin.openItem(ExtractionDropItem("切割器", "卓越", 3)).openContainer("container-b", "切割器", "卓越")
        val restored = SharedJson.decodeFromString<DropNavigation>(SharedJson.encodeToString(exploring))
        assertEquals(origin, restored.back().back())
        assertTrue(restored.back().back().current.linkedPool)
        assertEquals("切割器", restored.back().back().current.query)
        assertEquals("source-a", restored.back().back().back().current.sourceId)
    }

    @Test fun supplementaryReferencesReturnToTheSelectedItem() {
        val selected = DropNavigation().openItem(ExtractionDropItem("物品:甲", "Q8 装备", 8))
        val reference = selected.openReference().selectReference(ExtractionDropView.STAGES).openSource("stage")
        assertEquals(selected, reference.back().back())
        assertEquals(DropTask.SOURCES, reference.selectTask(DropTask.SOURCES).current.task)
        assertFalse(reference.selectTask(DropTask.SOURCES).canGoBack)
    }

    @Test fun repeatedItemVisitsKeepIndependentStateAndRestoreTheirOriginalOrigin() {
        val item = ExtractionDropItem("物品:甲", "Q8 装备", 8)
        val first = DropNavigation().openItem(item)
        val firstPool = first.openContainer("pool", item.name, item.qualityLabel)
        val second = firstPool.openItem(item)
        val secondPool = second.openContainer("pool", item.name, item.qualityLabel)

        // Identical visible objects used to share both their list and detail provider keys.
        assertEquals(first.current, second.current)
        assertEquals(firstPool.current, secondPool.current)
        assertNotEquals(first.contentStatePath, second.contentStatePath)
        assertNotEquals(firstPool.history, secondPool.history)
        assertEquals(first.contentStatePath, firstPool.contentStatePath)
        assertEquals(second.contentStatePath, secondPool.contentStatePath)

        val restored = SharedJson.decodeFromString<DropNavigation>(SharedJson.encodeToString(secondPool))
        assertEquals(second.contentStatePath, restored.back().contentStatePath)
        assertEquals(firstPool, restored.back().back())
        assertEquals(first.contentStatePath, restored.back().back().contentStatePath)
        assertEquals(first, restored.back().back().back())
    }

    @Test fun sourceAndReferenceSheetsKeepTheUnderlyingListContext() {
        val sources = DropNavigation().selectTask(DropTask.SOURCES)
        val sourcePool = sources.openSource("source").openLinkedPool()
        assertEquals(sources.contentStatePath, sourcePool.contentStatePath)
        val reference = sourcePool.openReference().selectReference(ExtractionDropView.STAGES)
        assertEquals(reference.contentStatePath, reference.openSource("stage").contentStatePath)
        assertEquals(sourcePool, reference.back())
    }

    @Test fun onlyUniqueExplicitIdsUnifyTheSourceBrowser() {
        fun entry(id: String, containerId: String? = null, type: ExtractionEntryType = ExtractionEntryType.DROP_SOURCE) =
            ExtractionEntry(id, "相同名称", "来源", type = type, containerId = containerId)
        val container = entry("container", "10001", ExtractionEntryType.DROP_POOL)
        val linked = entry("linked", "10001")
        val unknown = entry("unknown")
        val records = dropSourceRecords(listOf(container), listOf(linked, unknown))
        assertEquals(listOf("container", "unknown"), records.map { it.entry.id })
        assertEquals(linked, uniqueDropSourceNotes(container, listOf(linked, unknown)))
        val collision = entry("duplicate", "10001")
        assertEquals(4, dropSourceRecords(listOf(container), listOf(linked, collision, unknown)).size)
        assertNull(uniqueDropSourceNotes(container, listOf(linked, collision)))
        assertEquals(3, dropSourceRecords(listOf(container, container.copy(id = "container-2")), listOf(linked)).size)
        assertTrue(dropSourceRecords(listOf(container, container.copy(id = "container-2")), listOf(linked))
            .all { it.sourceNotes == null })
        assertEquals(1, dropSourceRecords(emptyList(), listOf(unknown)).size)
    }

    @Test fun searchCanStillReachDetailsFromAUnambiguouslyLinkedSourceCard() {
        val container = ExtractionEntry("container", "容器", "Boss", containerId = "10001", type = ExtractionEntryType.DROP_POOL)
        val notes = ExtractionEntry("source", "Boss别名", "Boss", containerId = "10001", type = ExtractionEntryType.DROP_SOURCE,
            fields = listOf(ExtractionField("掉落说明", "独有材料")))
        val search = DropSourceSearch(dropSourceRecords(listOf(container), listOf(notes)), listOf(notes))
        assertEquals(container, search.filter("", null).single().entry)
        assertEquals(container, search.filter("boss", null).single().entry)
        assertEquals(notes, search.filter("Boss别名", null).single().entry)
        assertEquals(DropSourceRecord(notes, false), search.filter("独有材料", null).single())
        assertTrue(search.filter("完全无关", null).isEmpty())
    }

    @Test fun candidateTermsAreReadOnceAcrossRepeatedQueriesAndSharedSources() {
        var reads = 0
        val names = object : AbstractList<String>() {
            override val size = 2
            override fun get(index: Int): String { reads++; return if (index == 0) "Alpha" else "Beta" }
        }
        val pool = ExtractionEntry("pool", "清单", "地图", type = ExtractionEntryType.DROP_POOL,
            dropPoolGroups = listOf(ExtractionDropPoolGroup("Q4 完美", names)))
        val a = DropSourceRecord(ExtractionEntry("a", "甲", "地图", sharedPoolId = pool.id), false)
        val b = DropSourceRecord(a.entry.copy(id = "b", category = "晶源体"), false)
        val search = DropSourceSearch(listOf(a, b), listOf(pool))
        assertEquals(listOf(a), search.filter("  ", "地图"))
        assertEquals(0, reads)
        search.prepare()
        assertEquals(listOf(a, b), search.filter(" PHa ", null))
        assertEquals(listOf(b), search.filter("q4", "晶源体"))
        assertTrue(search.filter("AlphaBeta", null).isEmpty())
        assertEquals(2, reads)
    }

    @Test fun firstSearchInABossCategoryDoesNotReadUnrelatedContainerPools() {
        var reads = 0
        val names = object : AbstractList<String>() {
            override val size = 1
            override fun get(index: Int) = "目标物品".also { reads++ }
        }
        val boss = DropSourceRecord(ExtractionEntry("boss", "莉莉丝 ／ KONG", "首领（Boss）"), false)
        val box = DropSourceRecord(ExtractionEntry("box", "弹药仓", "地图", dropPoolGroups = listOf(
            ExtractionDropPoolGroup("Q4 完美", names))), true)
        val search = DropSourceSearch(listOf(box, boss), emptyList())
        assertEquals(listOf(boss), search.filter("kong", boss.entry.category))
        assertEquals(0, reads)
        assertEquals(listOf(box), search.filter("目标", null))
        assertEquals(listOf(box), search.filter("完美", box.entry.category))
        assertEquals(1, reads)
    }
}
