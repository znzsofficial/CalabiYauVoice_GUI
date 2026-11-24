package com.nekolaska.calabiyau.feature.wiki.extraction.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.cache.VersionedMemoryCache
import com.nekolaska.calabiyau.feature.wiki.extraction.model.DropRate
import com.nekolaska.calabiyau.feature.wiki.extraction.model.DropRateShard
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionDropRateParser as Parser
import com.nekolaska.calabiyau.feature.wiki.extraction.source.*
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.ioApiCall
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

internal val extractionDropRateApi = ExtractionDropRateApi().also {
    MemoryCacheRegistry.register("ExtractionDropRates", it::clearMemory)
}

// Each entry points only to a shard checked against these exact names. Latest binding wins on a move.
@Serializable
private data class Binding(val number: Int, val names: Set<String>, val indexTime: Long) {
    val shard get() = DropRateShard(number, names)
}

private typealias RatesByName = Map<String, List<DropRate>>
private class RateSession(val index: Map<String, DropRateShard>, val indexTime: Long) {
    val shards = mutableMapOf<DropRateShard, RatesByName>()
}

internal class ExtractionDropRateApi(
    private val directory: DropRateDirectory = ExtractionDropRateSource,
    private val now: () -> Long = System::currentTimeMillis,
    private val read: suspend (DropRatePage, Boolean, Boolean, (String) -> Boolean) -> OfflineCache.CacheResult? = ExtractionDropRateSource::read
) {
    private val memory = VersionedMemoryCache<Unit, RateSession>()
    // Serialize live index/shard publications; a cache-only preview never waits for the network.
    private val live = Mutex()
    internal fun clearMemory() = memory.clear()

    suspend fun fetch(name: String, cacheOnly: Boolean = false, forceRefresh: Boolean = false): ApiResult<List<DropRate>> =
        ioApiCall("单件抽中率加载失败") {
            if (cacheOnly) lookup(name, true, false) else live.withLock { lookup(name, false, forceRefresh) }
        }

    private suspend fun lookup(name: String, cacheOnly: Boolean, force: Boolean): ApiResult<List<DropRate>> {
        if (force) memory.clear() // Even a failed/cancelled refresh retires the previous online generation.
        val session = if (cacheOnly) null else memory.get(Unit)
        val warmKey = session?.index?.get(name)
        session?.shards?.get(warmKey)?.get(name)?.let { return ApiResult.Success(it) }
        if (session != null && warmKey == null) return ApiResult.Success(emptyList())

        val publish = if (cacheOnly) null else directory.reserveBindings()
        val token = if (!cacheOnly && session == null) memory.begin(Unit) else null
        val manifest = session?.let { ApiResult.Success(it.index) } ?: when (val result =
            page(DropRatePage(DROP_RATE_INDEX_TITLE), cacheOnly, force, Parser::index)) {
            is ApiResult.Success -> result
            is ApiResult.Error -> return previous(name) ?: result
        }
        val indexTime = session?.indexTime ?: (now() - manifest.cacheAgeMs)
        val active = session ?: if (!cacheOnly && !manifest.isOffline) RateSession(manifest.value, indexTime).also {
            if (token != null) memory.publish(Unit, token, it)
        } else null
        val key = manifest.value[name] ?: return if (manifest.isOffline) previous(name)
            ?: ApiResult.Success(emptyList(), true, manifest.cacheAgeMs) else ApiResult.Success(emptyList())

        val result = when (val shard = readShard(key, cacheOnly, force)) {
            is ApiResult.Success -> shard
            is ApiResult.Error -> return previous(name) ?: shard
        }
        // Persist routing only after a complete shard validated. Its disk timestamp belongs to the
        // raw shard; keep the index's original time separately even when siblings reuse memory.
        if (publish != null && !result.isOffline) {
            val binding = Binding(key.number, key.names, indexTime)
            val saved = bindings()
            val covered = key.names.toMutableSet()
            publish(SharedJson.encodeToString(listOf(binding) + saved.filter { covered.addAll(it.names) }))
        }
        if (!result.isOffline) active?.shards?.set(key, result.value)
        val offline = manifest.isOffline || result.isOffline
        val boundTime = if (result.isOffline) bindings().firstOrNull { it.shard == key }?.indexTime else null
        val age = if (offline) maxOf(result.cacheAgeMs, now() - minOf(indexTime, boundTime ?: indexTime), 0L) else 0L
        return ApiResult.Success(result.value.getValue(name), offline, age)
    }

    private suspend fun bindings(): List<Binding> = directory.readBindings()?.let {
        runCatching { SharedJson.decodeFromString<List<Binding>>(it) }.getOrNull()
    }.orEmpty()

    private suspend fun previous(name: String): ApiResult.Success<List<DropRate>>? {
        val binding = bindings().firstOrNull { name in it.names } ?: return null
        val old = readShard(binding.shard, cacheOnly = true, force = false) as? ApiResult.Success ?: return null
        return ApiResult.Success(old.value.getValue(name), true, maxOf(old.cacheAgeMs, now() - binding.indexTime))
    }

    private suspend fun readShard(key: DropRateShard, cacheOnly: Boolean, force: Boolean) =
        page(key.page(), cacheOnly, force) { Parser.shard(it, key) }

    private suspend fun <T : Any> page(file: DropRatePage, cacheOnly: Boolean, force: Boolean, parse: (String) -> T?): ApiResult<T> {
        var checked: String? = null
        var parsed: T? = null
        fun decode(raw: String): T? {
            if (raw !== checked) {
                parsed = Parser.content(raw, file.title)?.let(parse)
                checked = raw
            }
            return parsed
        }
        val result = read(file, cacheOnly, force) { decode(it) != null }
            ?: return if (checked != null) ApiResult.Error("单件抽中率资料不完整", kind = ErrorKind.PARSE)
                else ApiResult.Error("单件抽中率资料不可用", kind = ErrorKind.NETWORK)
        val data = decode(result.payload) ?: return ApiResult.Error("单件抽中率资料不完整", kind = ErrorKind.PARSE)
        return ApiResult.Success(data, result.isFromCache, result.ageMs)
    }
}
