package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.feature.wiki.extraction.api.DropArtworkLoader
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionCatalogKind
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionEntry
import data.ApiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ExtractionDropArtworkLoadingTest {
    private val loot = ExtractionCatalogKind.LOOT
    private val attachments = ExtractionCatalogKind.ATTACHMENTS
    private val picture = listOf(ExtractionEntry("item", "物品", "材料"))

    @Test fun cachedAttachmentPublishesBeforeMissingLootStartsNetworkAndIsNotRefetched() = runBlocking {
        val events = mutableListOf<String>()
        val loader = DropArtworkLoader { kind, cacheOnly ->
            events += "$kind:$cacheOnly"
            if (kind == attachments && cacheOnly) ApiResult.Success(picture, isOffline = true, cacheAgeMs = 86_400_000L)
            else null
        }
        loader.loadMissing { fail("Network must wait for both caches") }
        assertTrue(events.isEmpty())
        loader.readCaches { events += "published" }
        // Holding network permission does not hold cached artwork back.
        assertEquals(listOf("LOOT:true", "ATTACHMENTS:true", "published"), events)
        loader.loadMissing { events += "published" }
        assertEquals(listOf("LOOT:true", "ATTACHMENTS:true", "published", "LOOT:false"), events)
        loader.readCaches { fail("Caches must not republish on resumed network permission") }
        loader.loadMissing { fail("A completed or failed attempt must not retry") }
        assertEquals(4, events.size)
    }

    @Test fun cancelledNetworkAttemptIsNotRetriedWhenEnrichmentResumes() = runBlocking {
        val requests = mutableListOf<Pair<ExtractionCatalogKind, Boolean>>()
        val loader = DropArtworkLoader { kind, cacheOnly ->
            requests += kind to cacheOnly
            if (kind == loot && !cacheOnly) throw CancellationException("Core source now takes priority")
            null
        }
        loader.readCaches { fail("No cache exists") }
        assertFailsWith<CancellationException> {
            loader.loadMissing { fail("Cancelled request published data") }
        }
        loader.loadMissing { fail("No response exists") }
        assertEquals(listOf(loot to true, attachments to true, loot to false, attachments to false), requests)
    }

    @Test fun nonCooperativeFetchCannotPublishAfterItsJobWasCancelled() = runBlocking {
        val requests = mutableListOf<Pair<ExtractionCatalogKind, Boolean>>()
        val loader = DropArtworkLoader { kind, cacheOnly ->
            requests += kind to cacheOnly
            if (cacheOnly) null else {
                currentCoroutineContext().cancel()
                ApiResult.Success(picture)
            }
        }
        loader.readCaches { fail("No cache exists") }
        val job = launch {
            loader.loadMissing { fail("Late response from cancelled request was published") }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(listOf(loot to true, attachments to true, loot to false), requests)
    }
}
