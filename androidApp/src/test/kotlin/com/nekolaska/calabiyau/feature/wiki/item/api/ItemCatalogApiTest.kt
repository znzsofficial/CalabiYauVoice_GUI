package com.nekolaska.calabiyau.feature.wiki.item.api

import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.hub.WikiRoute
import com.nekolaska.calabiyau.feature.wiki.hub.decodeRoute
import com.nekolaska.calabiyau.feature.wiki.hub.encode
import com.nekolaska.calabiyau.feature.wiki.item.model.ItemCatalogPlatform
import data.ApiResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ItemCatalogApiTest {
    @Test
    fun pagesCachesSnapshotsAndRestorableRoutesAreSeparated() {
        assertEquals("功能道具图鉴PC端", ItemCatalogPlatform.PC.pageName)
        assertEquals("功能道具图鉴移动端", ItemCatalogPlatform.MOBILE.pageName)
        assertNotEquals(ItemCatalogPlatform.PC.cacheKey, ItemCatalogPlatform.MOBILE.cacheKey)
        assertNotEquals(ItemCatalogPlatform.PC.snapshotName, ItemCatalogPlatform.MOBILE.snapshotName)
        assertEquals(WikiRoute.Items, decodeRoute("items")) // Released shortcut/state remains PC.
        assertEquals(WikiRoute.MobileItems, decodeRoute(WikiRoute.MobileItems.encode()))
        assertNotEquals(WikiRoute.Items.encode(), WikiRoute.MobileItems.encode())
    }

    @Test
    fun memoryAndOfflineResultsCannotCrossCatalogsAndValidationBypassesMemo() = runBlocking {
        val calls = mutableListOf<Pair<ItemCatalogPlatform, Boolean>>()
        fun api(platform: ItemCatalogPlatform) = PlatformCatalogApi(platform,
            readCache = { p -> WikiHtmlPageSourceResult(fixture(p), true, 1234) },
            readNetwork = { p, force ->
                calls.add(p to force)
                WikiHtmlPageSourceResult(fixture(p), false, 0)
            })
        val pc = api(ItemCatalogPlatform.PC)
        val mobile = api(ItemCatalogPlatform.MOBILE)
        val pcResult = pc.fetch() as ApiResult.Success
        val mobileResult = mobile.fetch() as ApiResult.Success
        assertEquals("理想币", pcResult.value.single().name)
        assertEquals("排位币", mobileResult.value.single().name)
        pc.fetch(); mobile.fetch()
        assertEquals(listOf(ItemCatalogPlatform.PC to false, ItemCatalogPlatform.MOBILE to false), calls)
        val cached = mobile.fetch(cacheOnly = true) as ApiResult.Success
        assertTrue(cached.isOffline)
        assertEquals(1234L, cached.cacheAgeMs)
        assertEquals("排位币", cached.value.single().name)
        pc.fetch(allowMemoryCache = false)
        mobile.fetch(forceRefresh = true)
        assertEquals(listOf(ItemCatalogPlatform.PC to false, ItemCatalogPlatform.MOBILE to true), calls.takeLast(2))
    }

    @Test
    fun emptyParseIsAnErrorNamingTheAffectedPlatform() = runBlocking {
        val api = PlatformCatalogApi(ItemCatalogPlatform.MOBILE,
            readNetwork = { _, _ -> WikiHtmlPageSourceResult("<div class='mw-parser-output'></div>", false, 0) })
        val result = api.fetch() as ApiResult.Error
        assertTrue(result.message.contains(ItemCatalogPlatform.MOBILE.pageName))
    }

    private fun fixture(platform: ItemCatalogPlatform) = checkNotNull(javaClass.getResource(
        "/fixtures/items/${if (platform == ItemCatalogPlatform.PC) "items_pc_card" else "items_mobile_card"}.html"
    )).readText(Charsets.UTF_8)
}
