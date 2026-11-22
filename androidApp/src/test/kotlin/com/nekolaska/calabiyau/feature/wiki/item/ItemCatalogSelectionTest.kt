package com.nekolaska.calabiyau.feature.wiki.item

import com.nekolaska.calabiyau.feature.wiki.item.model.ItemInfo
import com.nekolaska.calabiyau.feature.wiki.item.model.Quality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemCatalogSelectionTest {
    @Test fun savedKeyResolvesUpdatedNamesakeEvenAfterItStopsMatchingTheSearch() {
        val first = ItemInfo("体验卡", "消耗品", Quality.SUPERIOR, "卓越", "第一款", null)
        val second = first.copy(description = "限时试用")
        val cached = keyedCatalogItems(listOf(first, second))
        val savedKey = cached.entries.single { "限时" in it.value.description }.key

        assertNull(keyedCatalogItems(emptyList())[savedKey]) // State restored before data arrives.
        val updated = second.copy(description = "长期可用", iconUrl = "new-icon.png")
        val fresh = keyedCatalogItems(listOf(first.copy(name = "新道具"), first, updated))
        assertEquals(updated, fresh[savedKey])
        assertNull(keyedCatalogItems(listOf(first))[savedKey])
    }
}
