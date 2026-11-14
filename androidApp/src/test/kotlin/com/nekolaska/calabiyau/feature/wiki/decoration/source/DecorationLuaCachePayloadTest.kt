package com.nekolaska.calabiyau.feature.wiki.decoration.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.cache.fetchValidatedCachePayload
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DecorationLuaCachePayloadTest {
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/fixtures/decoration/$name.lua")).readText(Charsets.UTF_8)
    @Test fun invalidDependencyPairCannotReplaceGoodDiskSnapshot() = runBlocking {
        val policy = DecorationCatalogs.forPage("个性名片")
        val items=fixture("profile-card-data")
        val ways=fixture("profile-card-ways")
        val good=DecorationLuaCachePayload.encode(items,ways)
        var disk=good
        for (bad in listOf(
            DecorationLuaCachePayload.encode(items,"return {}"),
            DecorationLuaCachePayload.encode(items.replace("34000002","34000001"),ways),
            DecorationLuaCachePayload.encode(items,ways.replace("[34000002]","[34000001]")),
            DecorationLuaCachePayload.encode("return {}",ways)
        )) {
            val result=fetchValidatedCachePayload(true,{bad},{OfflineCache.CacheResult(disk,true,45)},
                {disk=it},{DecorationLuaCachePayload.isValid(policy,it)})
            assertEquals(good,disk)
            assertEquals(good,result?.payload)
            assertTrue(result?.isFromCache == true)
        }
    }
    @Test fun apiErrorsAndMalformedPayloadsDoNotBecomeTables() {
        assertNull(DecorationLuaCachePayload.moduleText("""{"error":{"code":"missingtitle"}}"""))
        assertNull(DecorationLuaCachePayload.decode("{}"))
        assertEquals("return {}",DecorationLuaCachePayload.moduleText("""{"parse":{"wikitext":{"*":"return {}"}}}"""))
    }
}
