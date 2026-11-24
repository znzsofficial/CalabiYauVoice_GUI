package com.nekolaska.calabiyau.feature.wiki.extraction.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.feature.wiki.extraction.model.DropRateShard
import java.security.MessageDigest
import util.buildWikiUrl

internal const val DROP_RATE_INDEX_TITLE = "MediaWiki:弦溯战线掉落反向索引.json"
internal fun dropRateShardTitle(number: Int) = "MediaWiki:弦溯战线掉落反向-$number.json"

internal data class DropRatePage(val title: String, val cacheKey: String = title)

/** Different index assignments must not overwrite the file referenced by the last verified binding. */
internal fun DropRateShard.page(): DropRatePage {
    val identity = names.sorted().joinToString("") { "${it.length}:$it" }
    val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
    return DropRatePage(dropRateShardTitle(number), "shard:$number:$hash")
}

internal interface DropRateDirectory {
    suspend fun readBindings(): String?
    /** Reserve before fetching either dependency, so clearing the cache revokes a pending publication. */
    suspend fun reserveBindings(): suspend (String) -> Unit
}

/** Keep the raw, title-checked revisions responses separate from the container HTML. */
internal object ExtractionDropRateSource : DropRateDirectory {
    private val type = OfflineCache.Type.ITEMS
    private const val directoryKey = "extraction_drop_rate_verified_v1"

    override suspend fun readBindings() = OfflineCache.getEntry(type, directoryKey)?.content
    override suspend fun reserveBindings(): suspend (String) -> Unit {
        val ticket = OfflineCache.captureWrite(type, directoryKey)
        return { OfflineCache.put(ticket, it) }
    }

    suspend fun read(
        page: DropRatePage, cacheOnly: Boolean, force: Boolean, valid: (String) -> Boolean
    ): OfflineCache.CacheResult? {
        val key = "extraction_drop_rate_v1:${page.cacheKey}"
        if (cacheOnly) return OfflineCache.getEntry(type, key)?.let { OfflineCache.CacheResult(it.content, true, it.ageMs) }
        return OfflineCache.fetchWithCache(type, key, force, validatePayload = valid) {
            ExtractionCatalogRemoteSource.get(buildWikiUrl("https://wiki.biligame.com/klbq/api.php",
                "action" to "query", "format" to "json", "formatversion" to "2", "prop" to "revisions",
                "rvslots" to "main", "rvprop" to "content", "titles" to page.title))
        }
    }
}
