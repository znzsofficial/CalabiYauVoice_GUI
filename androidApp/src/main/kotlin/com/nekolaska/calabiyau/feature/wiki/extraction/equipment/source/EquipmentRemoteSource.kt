package com.nekolaska.calabiyau.feature.wiki.extraction.equipment.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.cache.fetchValidatedCachePayload
import com.nekolaska.calabiyau.core.network.NetworkMonitor
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.EquipmentKind
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.model.EquipmentPage
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.parser.EquipmentFormatException
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.parser.EquipmentParser
import com.nekolaska.calabiyau.feature.wiki.extraction.source.ExtractionCatalogRemoteSource
import data.SharedJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import util.buildParseUrl

internal data class EquipmentSnapshot(val page: EquipmentPage, val offline: Boolean, val ageMs: Long)

internal object EquipmentRemoteSource {
    private val type = OfflineCache.Type.ITEMS

    fun decode(kind: EquipmentKind, payload: String): EquipmentPage {
        val root = try { SharedJson.parseToJsonElement(payload) as? JsonObject }
            catch (_: SerializationException) { null }
        val parse = root?.takeUnless { "error" in it }?.get("parse") as? JsonObject
        if (parse == null || (parse["title"] as? JsonPrimitive)?.content != kind.pageName)
            throw EquipmentFormatException("响应不是${kind.title}")
        val text = parse["text"]
        val html = ((text as? JsonPrimitive) ?: ((text as? JsonObject)?.get("*") as? JsonPrimitive))
            ?.takeIf { it.isString }?.content ?: throw EquipmentFormatException("缺少页面正文")
        return EquipmentParser.parse(kind, html)
    }

    private suspend fun disk(kind: EquipmentKind) = OfflineCache.getEntry(type, kind.cacheKey)?.let {
        OfflineCache.CacheResult(it.content, true, it.ageMs)
    }

    suspend fun readCache(kind: EquipmentKind): EquipmentSnapshot? = disk(kind)?.let {
        EquipmentSnapshot(decode(kind, it.payload), true, it.ageMs)
    }

    suspend fun fetch(kind: EquipmentKind, force: Boolean): EquipmentSnapshot? {
        val url = buildParseUrl("https://wiki.biligame.com/klbq/api.php", kind.pageName, "text")
        // Memo belongs to this request only. A coalesced follower validates its returned snapshot too.
        var validated: Pair<String, EquipmentPage>? = null
        fun parse(raw: String): EquipmentPage = validated?.takeIf { it.first == raw }?.second
            ?: decode(kind, raw).also { validated = raw to it }
        val result = OfflineCache.fetchWithPolicy(type, kind.cacheKey, force,
            validatePayload = { try { parse(it); true } catch (_: EquipmentFormatException) { false } }) { ticket ->
            val route = OfflineCache.defaultRoute()
            validatedFetch(route == NetworkMonitor.Route.VALIDATED, route == NetworkMonitor.Route.UNVALIDATED,
                parse = ::parse, network = { ExtractionCatalogRemoteSource.get(url) }, read = { disk(kind) },
                publish = { OfflineCache.put(ticket, it) })
        } ?: return null
        return EquipmentSnapshot(parse(result.payload), result.isFromCache, result.ageMs)
    }

    internal suspend fun validatedFetch(online: Boolean, tryNetworkOnMiss: Boolean,
        parse: (String) -> EquipmentPage, network: suspend () -> String?,
        read: suspend () -> OfflineCache.CacheResult?, publish: suspend (String) -> Unit): OfflineCache.CacheResult? {
        var rejected: EquipmentFormatException? = null
        val result = fetchValidatedCachePayload(online, network, read, publish, validate = { raw ->
            try { parse(raw); true } catch (error: EquipmentFormatException) { rejected = error; false }
        }, tryNetworkOnMiss = tryNetworkOnMiss)
        if (result == null) rejected?.let { throw it }
        return result
    }
}
