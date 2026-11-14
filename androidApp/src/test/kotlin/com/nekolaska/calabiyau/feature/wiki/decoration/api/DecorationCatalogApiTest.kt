package com.nekolaska.calabiyau.feature.wiki.decoration.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogPolicy
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationLuaSnapshot
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationSection
import com.nekolaska.calabiyau.feature.wiki.decoration.source.DecorationSource
import data.ApiResult
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class DecorationCatalogApiTest {
    private val cacheNames = mutableListOf<String>()
    @AfterTest fun cleanup() = cacheNames.forEach(MemoryCacheRegistry::unregister)
    private fun api(source: DecorationSource): DecorationCatalogApi {
        val name = "DecorationCatalogApiTest.${sequence.incrementAndGet()}"
        cacheNames += name
        return DecorationCatalogApi(source, name)
    }
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/fixtures/decoration/$name")).readText(Charsets.UTF_8)
    private fun pair() = DecorationLuaSnapshot(fixture("profile-card-data.lua"), fixture("profile-card-ways.lua"))
    private fun fake() = FakeSource().apply { lua = pair(); html = fixture("namecards.html") }
    private fun success(result: ApiResult<List<DecorationSection>>) = assertIs<ApiResult.Success<List<DecorationSection>>>(result)

    @Test fun completeLuaNeedsNoHtmlAndCachesOnlyCompleteImages() = runBlocking {
        val source = fake()
        val api = api(source)
        val first = success(api.fetchDecoration("个性名片"))
        assertEquals(5, first.value.single().items.size)
        assertEquals("默认", first.value.single().items.first().source)
        success(api.fetchDecoration("个性名片"))
        assertEquals(listOf("network.lua:个性名片:false", "network.images:false"), source.calls.toList())
    }

    @Test fun complexHtmlNeverEnumeratesLuaAndKeepsPageMemoryKeysSeparate() = runBlocking {
        val source = fake()
        val api = api(source)
        success(api.fetchDecoration("基板"))
        success(api.fetchDecoration("喷漆"))
        success(api.fetchDecoration("基板"))
        assertEquals(listOf("network.html:基板:false", "network.html:喷漆:false"), source.calls.toList())
    }

    @Test fun cacheOnlyBypassesMemoryAndForceNeverEnablesNetwork() = runBlocking {
        val source = fake()
        val api = api(source)
        success(api.fetchDecoration("个性名片"))
        source.calls.clear()
        source.forbidNetwork = true
        source.cachedLua = pair().copy(itemsText=pair().itemsText.replace("序列证", "缓存序列证"), isFromCache=true, ageMs=123L)
        val result = success(api.fetchDecoration("个性名片", cacheOnly=true, forceRefresh=true))
        assertEquals("缓存序列证", result.value.single().items.first().name)
        assertTrue(result.isOffline)
        assertEquals(123L, result.cacheAgeMs)
        assertEquals(listOf("disk.lua:个性名片", "disk.html:个性名片", "disk.images"), source.calls.toList())
    }

    @Test fun cacheOnlyMissingPairUsesHtmlWithoutAnyNetworkOrModuleGuess() = runBlocking {
        val source = fake().apply { forbidNetwork=true; cachedHtml=html; cachedLua=null }
        val result = success(api(source).fetchDecoration("个性名片", cacheOnly=true))
        assertEquals("序列证", result.value.single().items.first().name)
        assertTrue(result.value.first().notice!!.contains("兼容"))
        assertEquals(listOf("disk.lua:个性名片", "disk.html:个性名片"), source.calls.toList())
    }

    @Test fun invalidOrIncompleteDependencyUsesHtmlThenRetriesAfterRecovery() = runBlocking {
        for (bad in listOf(null, pair().copy(waysText="return {}"), pair().copy(itemsText="return {"),
            pair().copy(waysText=pair().waysText.replace("[34000005]", "[99999999]")))) {
            val source = fake().apply { lua=bad }
            val api = api(source)
            val fallback = success(api.fetchDecoration("个性名片"))
            assertEquals(2, fallback.value.single().items.size)
            assertTrue(fallback.value.first().notice != null)
            source.lua = pair()
            assertEquals(5, success(api.fetchDecoration("个性名片")).value.single().items.size)
            assertEquals(2, source.calls.count { it.startsWith("network.lua") })
            assertEquals(1, source.calls.count { it.startsWith("network.html") })
        }
    }

    @Test fun stalePairCannotOverrideCurrentHtmlAndCachedRecoveryStaysOffline() = runBlocking {
        val source = fake().apply { lua=pair().copy(isFromCache=true, ageMs=OfflineCache.Type.DECORATIONS.maxAgeMs+1) }
        val api = api(source)
        val result = success(api.fetchDecoration("个性名片"))
        assertEquals(2, result.value.single().items.size)
        assertTrue(result.value.first().notice!!.contains("过期"))
        source.lua = pair().copy(isFromCache=true, ageMs=456)
        source.html = null
        val cached = success(api.fetchDecoration("个性名片"))
        assertEquals(5, cached.value.single().items.size)
        assertTrue(cached.isOffline)
        assertEquals(456L, cached.cacheAgeMs)
    }

    @Test fun missingArtworkDoesNotDropRowsAndImageRecoveryIsNotBlockedByMemo() = runBlocking {
        val source = fake().apply { missingImages=true }
        val api = api(source)
        val first = success(api.fetchDecoration("个性名片"))
        assertEquals(5, first.value.single().items.size)
        assertTrue(first.value.single().items.all { it.imageUrl.isEmpty() })
        source.missingImages = false
        assertTrue(success(api.fetchDecoration("个性名片")).value.single().items.all { it.imageUrl.isNotEmpty() })
        assertEquals(2, source.calls.count { it.startsWith("network.lua") })
    }

    @Test fun cacheOnlyAndOfflineNetworkChooseTheSameNewerCompleteSnapshot() = runBlocking {
        // Both paths read real source metadata, including cached HTML rather than fake online HTML.
        for ((luaAge, htmlAge, expectedLua) in listOf(
            Triple(100L, 200L, true), Triple(200L, 100L, false), Triple(100L, 100L, true),
            Triple(OfflineCache.Type.DECORATIONS.maxAgeMs, OfflineCache.Type.DECORATIONS.maxAgeMs + 1, true),
            Triple(OfflineCache.Type.DECORATIONS.maxAgeMs + 1, OfflineCache.Type.DECORATIONS.maxAgeMs + 2, false)
        )) {
            for (cacheOnly in listOf(false, true)) {
                val source = fake().apply {
                    lua = pair().copy(isFromCache = true, ageMs = luaAge)
                    cachedLua = lua
                    cachedHtml = html
                    htmlFromCache = true
                    this.htmlAge = htmlAge
                    cachedHtmlAge = htmlAge
                    forbidNetwork = cacheOnly
                }
                val result = success(api(source).fetchDecoration("个性名片", cacheOnly = cacheOnly, forceRefresh = true))
                assertEquals(if (expectedLua) 5 else 2, result.value.single().items.size, "lua=$luaAge html=$htmlAge cacheOnly=$cacheOnly")
                assertEquals(if (expectedLua) luaAge else htmlAge, result.cacheAgeMs)
                assertTrue(result.isOffline)
                assertTrue(result.value.first().notice != null)
                if (cacheOnly) assertTrue(source.calls.all { it.startsWith("disk.") })
                else assertTrue("network.lua:个性名片:true" in source.calls && "network.html:个性名片:true" in source.calls)
            }
        }
    }

    @Test fun successfulOnlineHtmlWinsOverCompleteCachedLuaButNeverBecomesPermanentMemo() = runBlocking {
        val source = fake().apply { lua = pair().copy(isFromCache = true, ageMs = 0L) }
        val api = api(source)
        val onlineHtml = success(api.fetchDecoration("个性名片"))
        assertEquals(2, onlineHtml.value.single().items.size)
        assertEquals(false, onlineHtml.isOffline)
        source.lua = pair()
        assertEquals(5, success(api.fetchDecoration("个性名片")).value.single().items.size)
        assertEquals(2, source.calls.count { it.startsWith("network.lua") })
    }

    @Test fun invalidHtmlCannotWinByAgeAndInvalidDiskDirectoriesAreNotSuccesses() = runBlocking {
        val partial = fixture("namecards.html").replace("个性名片 34000002.png", "没有编号.png")
        for (cacheOnly in listOf(false, true)) {
            val source = fake().apply {
                lua = pair().copy(isFromCache = true, ageMs = 200)
                cachedLua = lua
                html = partial
                cachedHtml = partial
                htmlFromCache = true
                htmlAge = 1
                cachedHtmlAge = 1
                forbidNetwork = cacheOnly
            }
            assertEquals(5, success(api(source).fetchDecoration("个性名片", cacheOnly = cacheOnly)).value.single().items.size)
            assertIs<ApiResult.Error>(api(source).fetchDecoration("基板", cacheOnly = cacheOnly))
            source.lua = pair().copy(isFromCache = true, ageMs = OfflineCache.Type.DECORATIONS.maxAgeMs + 1)
            source.cachedLua = source.lua
            assertIs<ApiResult.Error>(api(source).fetchDecoration("个性名片", cacheOnly = cacheOnly))
        }
    }

    @Test fun comparingHtmlCachesStillPropagatesCancellation() = runBlocking {
        val source = fake().apply {
            lua = pair().copy(isFromCache = true, ageMs = 100)
            cachedLua = lua
            cancelAt = "network.html"
        }
        val api = api(source)
        assertFailsWith<CancellationException> { api.fetchDecoration("个性名片") }
        source.cancelAt = "disk.html"
        assertFailsWith<CancellationException> { api.fetchDecoration("个性名片", cacheOnly = true) }
        source.cancelAt = null
        assertEquals(5, success(api.fetchDecoration("个性名片", cacheOnly = true)).value.single().items.size)
    }

    @Test fun networkExceptionsDegradeButCancellationAlwaysPropagates() = runBlocking {
        val source = fake().apply { failAt="network.lua" }
        assertTrue(success(api(source).fetchDecoration("个性名片")).value.first().notice != null)
        for ((stage, page) in listOf("network.lua" to "个性名片", "network.images" to "个性名片", "network.html" to "基板")) {
            val failing = fake().apply { cancelAt=stage }
            val api = api(failing)
            assertFailsWith<CancellationException> { api.fetchDecoration(page) }
            failing.cancelAt = null
            success(api.fetchDecoration(page))
            assertEquals(2, failing.calls.count { it.startsWith(if(page=="基板") "network.html" else "network.lua") })
        }
    }

    @Test fun forcedFailurePreservesLastGoodMemoryAndForwardsForceToDependencies() = runBlocking {
        val source = fake()
        val api = api(source)
        success(api.fetchDecoration("个性名片"))
        source.lua = null
        source.html = null
        assertIs<ApiResult.Error>(api.fetchDecoration("个性名片",forceRefresh=true))
        assertEquals(5, success(api.fetchDecoration("个性名片")).value.single().items.size)
        assertTrue("network.lua:个性名片:true" in source.calls)
        assertTrue("network.html:个性名片:true" in source.calls)
    }

    @Test fun olderRequestCannotPublishOverNewerSuccessfulRequest() = runBlocking {
        withTimeout(5000) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val count = AtomicInteger()
            val source = fake().apply {
                networkPair = {
                    if (count.incrementAndGet()==1) {
                        started.complete(Unit); release.await()
                        pair().copy(itemsText=pair().itemsText.replace("序列证","旧结果"))
                    } else pair().copy(itemsText=pair().itemsText.replace("序列证","新结果"))
                }
            }
            val api = api(source)
            val old = async { api.fetchDecoration("个性名片",allowMemoryCache=false) }
            try { started.await(); success(api.fetchDecoration("个性名片",forceRefresh=true)) }
            finally { release.complete(Unit) }
            assertEquals("旧结果",success(old.await()).value.single().items.first().name)
            assertEquals("新结果",success(api.fetchDecoration("个性名片")).value.single().items.first().name)
            assertEquals(2,count.get())
        }
    }

    private class FakeSource : DecorationSource {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        var lua: DecorationLuaSnapshot? = null
        var cachedLua: DecorationLuaSnapshot? = null
        var html: String? = null
        var cachedHtml: String? = null
        var htmlFromCache = false
        var htmlAge = 0L
        var cachedHtmlAge = 123L
        var missingImages = false
        var forbidNetwork = false
        var cancelAt: String? = null
        var failAt: String? = null
        var networkPair: (suspend () -> DecorationLuaSnapshot?)? = null
        private fun record(call: String) {
            calls += call
            check(!forbidNetwork || !call.startsWith("network")) { call }
            if (call.substringBefore(':')==cancelAt) throw CancellationException(call)
            if (call.substringBefore(':')==failAt) throw IOException(call)
        }
        override suspend fun fetchLuaCatalog(policy: DecorationCatalogPolicy, forceRefresh: Boolean): DecorationLuaSnapshot? {
            record("network.lua:${policy.pageName}:$forceRefresh")
            return networkPair?.invoke() ?: lua
        }
        override suspend fun loadCachedLuaCatalog(policy: DecorationCatalogPolicy): DecorationLuaSnapshot? {
            record("disk.lua:${policy.pageName}"); return cachedLua
        }
        override suspend fun fetchPageHtml(pageName: String, forceRefresh: Boolean): WikiHtmlPageSourceResult? {
            record("network.html:$pageName:$forceRefresh"); return html?.let { WikiHtmlPageSourceResult(it,htmlFromCache,htmlAge) }
        }
        override suspend fun loadCachedPageHtml(pageName: String): WikiHtmlPageSourceResult? {
            record("disk.html:$pageName"); return cachedHtml?.let { WikiHtmlPageSourceResult(it,true,cachedHtmlAge) }
        }
        override suspend fun fetchImageUrls(fileNames: List<String>, forceRefresh: Boolean): Map<String,String> {
            record("network.images:$forceRefresh"); return images(fileNames)
        }
        override suspend fun loadCachedImageUrls(fileNames: List<String>): Map<String,String> {
            record("disk.images"); return images(fileNames)
        }
        private fun images(files: List<String>): Map<String,String> = if(missingImages) emptyMap() else files.associateWith { "https://example.com/${it.hashCode()}.png" }
    }
    companion object { private val sequence=AtomicInteger() }
}
