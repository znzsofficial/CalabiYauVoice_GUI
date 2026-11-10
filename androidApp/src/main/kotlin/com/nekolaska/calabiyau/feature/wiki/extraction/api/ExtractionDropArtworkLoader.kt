package com.nekolaska.calabiyau.feature.wiki.extraction.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nekolaska.calabiyau.feature.wiki.extraction.model.DropArtworkIndex
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionCatalogKind
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionEntry
import com.nekolaska.calabiyau.feature.wiki.extraction.model.buildDropArtworkIndex
import data.ApiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Optional enrichment; failures never change the main catalog's load/error state. */
@Composable
internal fun rememberDropArtwork(
    enabled: Boolean, catalogEntries: List<ExtractionEntry>, allowNetwork: Boolean = enabled
): DropArtworkIndex {
    var artworkEntries by remember { mutableStateOf<List<ExtractionEntry>>(emptyList()) }
    val loader = remember { DropArtworkLoader() }
    var cachesReady by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        loader.readCaches { entries ->
            artworkEntries = artworkEntries + entries
        }
        currentCoroutineContext().ensureActive()
        cachesReady = true
    }
    LaunchedEffect(enabled, allowNetwork, cachesReady) {
        if (!enabled || !allowNetwork || !cachesReady) return@LaunchedEffect
        loader.loadMissing { entries ->
            artworkEntries = artworkEntries + entries
        }
    }
    return remember(catalogEntries, artworkEntries) { buildDropArtworkIndex(catalogEntries + artworkEntries) }
}

/** Attempt state belongs to the screen, so pausing enrichment never retries a cancelled request. */
internal class DropArtworkLoader(
    private val fetch: suspend (ExtractionCatalogKind, Boolean) -> ApiResult<List<ExtractionEntry>>? = ::optionalArtworkFetch
) {
    private val kinds = listOf(ExtractionCatalogKind.LOOT, ExtractionCatalogKind.ATTACHMENTS)
    private val attempted = mutableSetOf<ExtractionCatalogKind>()
    private var cachesReady = false

    // Publish both disk caches before waiting on any network request. Old HTML is reparsed by
    // the current parser and still passes the same strict name/quality resolver as live data.
    suspend fun readCaches(publish: (List<ExtractionEntry>) -> Unit) {
        if (cachesReady) return
        for (kind in kinds.filterNot { it in attempted }) {
            val result = fetch(kind, true)
            currentCoroutineContext().ensureActive()
            if (result is ApiResult.Success) {
                attempted += kind
                publish(result.value)
            }
        }
        cachesReady = true
    }

    suspend fun loadMissing(publish: (List<ExtractionEntry>) -> Unit) {
        if (!cachesReady) return
        for (kind in kinds.filterNot { it in attempted }) {
            currentCoroutineContext().ensureActive()
            // Register before suspension: cancellation is also an attempt, not permission to retry
            // automatically when a core refresh finishes or the user switches back to this task.
            attempted += kind
            val result = fetch(kind, false)
            currentCoroutineContext().ensureActive()
            if (result is ApiResult.Success) publish(result.value)
        }
    }
}

private suspend fun optionalArtworkFetch(kind: ExtractionCatalogKind, cacheOnly: Boolean): ApiResult<List<ExtractionEntry>>? =
    try {
        // The existing source serializes all extraction requests through its shared 6.5s gate.
        ExtractionCatalogApi.fetch(kind, cacheOnly = cacheOnly)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
