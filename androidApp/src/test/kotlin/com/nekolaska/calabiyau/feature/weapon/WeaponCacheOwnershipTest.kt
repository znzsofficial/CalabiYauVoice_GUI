package com.nekolaska.calabiyau.feature.weapon

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.feature.weapon.detail.WeaponDetailApi
import com.nekolaska.calabiyau.feature.weapon.detail.WeaponDetailReader
import com.nekolaska.calabiyau.feature.weapon.list.WeaponListApi
import com.nekolaska.calabiyau.feature.weapon.skin.WeaponSkinFilterApi
import data.ApiResult
import kotlinx.coroutines.*
import kotlin.test.*

class WeaponCacheOwnershipTest {
    @Test fun realDetailReaderRetriesOfflineAndRejectsLateRefreshAfterClear() = runBlocking {
        val detail = assertNotNull(WeaponDetailApi.parseWeaponWikitext("甲", "{{武器|类型=主武器}}"))
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var calls = 0
        val reader = WeaponDetailReader({ _, force ->
            calls++
            if (force) { started.complete(Unit); release.await() }
            ApiResult.Success(detail.copy(description = "call$calls"), calls == 1, if (calls == 1) 123 else 0)
        }, "weaponReaderContract")
        val offline = assertIs<ApiResult.Success<*>>(reader.fetch("甲")); assertTrue(offline.isOffline); assertEquals(123L, offline.cacheAgeMs)
        assertFalse(assertIs<ApiResult.Success<*>>(reader.fetch("甲")).isOffline)
        val pending = launch { reader.fetch("甲", true) }; started.await()
        MemoryCacheRegistry.clearAll(); release.complete(Unit); pending.join()
        val fresh = assertIs<ApiResult.Success<WeaponDetailApi.WeaponDetail>>(reader.fetch("甲"))
        assertEquals("call4", fresh.value.description)
    }

    @Test fun onlyCategoryFallbackPropagatesAgeAndFailedGroupsCannotBecomeCompleteSkinCatalog() {
        val groups = WeaponListApi.WeaponCategory.entries.map { WeaponListApi.WeaponCategoryData(it, emptyList()) }
        val payload = """{"parse":{"text":{"*":"<div class='klbq-skin-card' data-param1='甲'><span class='name'>蓝</span></div>"}}}"""
        val source = OfflineCache.CacheResult(payload, false, 0)
        val diskMeta = WeaponSkinFilterApi.metaSnapshot(ApiResult.Success(groups, true, 55))
        val mixed = assertIs<ApiResult.Success<WeaponSkinFilterApi.Loaded>>(WeaponSkinFilterApi.assemble(source, diskMeta))
        assertTrue(mixed.isOffline); assertEquals(55L, mixed.cacheAgeMs)
        val partial = WeaponSkinFilterApi.metaSnapshot(ApiResult.Success(groups.dropLast(1) + groups.last().copy(loadError = "bad")))
        val degraded = assertIs<ApiResult.Success<WeaponSkinFilterApi.Loaded>>(WeaponSkinFilterApi.assemble(source, partial))
        assertFalse(degraded.value.complete); assertEquals("甲", degraded.value.skins.single().weapon)
    }
}
