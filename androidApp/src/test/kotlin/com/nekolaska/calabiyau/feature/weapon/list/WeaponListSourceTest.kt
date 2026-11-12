package com.nekolaska.calabiyau.feature.weapon.list

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class WeaponListSourceTest {
    private fun page(name: String, next: Int? = null) = """{"query":{"results":{"$name":{"printouts":{"类型":[{"fulltext":"步枪"}]}}}}${next?.let { ",\"query-continue-offset\":$it" }.orEmpty()}}"""

    @Test fun followsContinuationAndPublishesOnlyTheCompleteCategory() = runBlocking {
        val offsets = mutableListOf<Int>()
        val result = WeaponListSource.fetchAll { offset -> offsets += offset; if (offset == 0) page("甲", 100) else page("乙") }!!
        assertEquals(listOf(0, 100), offsets)
        assertTrue(WeaponListSource.isValidPayload(result))
        assertEquals(setOf("乙", "甲"), WeaponListApi.parseWeapons(result).map { it.name }.toSet())
        assertFalse(WeaponListSource.isValidPayload(page("甲", 100)))
    }

    @Test fun tailFailureLoopsDuplicatesAndMalformedRowsCannotPublishTheFirstPage() = runBlocking {
        assertNull(WeaponListSource.fetchAll { if (it == 0) page("甲", 100) else null })
        assertFailsWith<IllegalArgumentException> { WeaponListSource.fetchAll { page("甲", 0) } }
        assertFailsWith<IllegalArgumentException> { WeaponListSource.fetchAll { page("甲", 100) } }
        assertFalse(WeaponListSource.isValidPayload("""{"query":{"results":{"甲":{}}}}"""))
        assertFalse(WeaponListSource.isValidPayload("""{"query":{"results":{}}}"""))
        assertFailsWith<CancellationException> { WeaponListSource.fetchAll { throw CancellationException() } }
        Unit
    }
}
