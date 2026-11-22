package com.nekolaska.calabiyau.feature.wiki.extraction.workshop.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.feature.wiki.extraction.source.ExtractionCatalogRemoteSource
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.model.WORKSHOP_PAGE
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.model.WorkshopPage
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.parser.*
import data.SharedJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import util.buildParseUrl

internal data class WorkshopSnapshot(val page: WorkshopPage, val offline: Boolean, val ageMs: Long)

internal object WorkshopRemoteSource {
    const val CACHE_KEY = "extraction_workshop_v1"
    private val cacheType = OfflineCache.Type.ITEMS

    fun parsePayload(payload: String): WorkshopPage {
        val root = try { SharedJson.parseToJsonElement(payload) as? JsonObject } catch (_: SerializationException) { null }
        if (root == null || "error" in root) broken("工坊响应格式异常")
        val parse = root["parse"] as? JsonObject ?: broken("缺少工坊页面")
        if ((parse["title"] as? JsonPrimitive)?.content != WORKSHOP_PAGE) broken("页面不是弦溯战线工坊")
        fun text(field: String): String {
            val node = parse[field]
            return ((node as? JsonPrimitive) ?: (node as? JsonObject)?.get("*") as? JsonPrimitive)
                ?.takeIf { it.isString }?.content?.takeIf(String::isNotBlank) ?: broken("工坊响应缺少$field")
        }
        return WorkshopParser.parse(text("text"), text("wikitext"))
    }

    suspend fun readCache(): WorkshopSnapshot? = OfflineCache.getEntry(cacheType, CACHE_KEY)?.let {
        WorkshopSnapshot(parsePayload(it.content), true, it.ageMs)
    }

    suspend fun fetch(forceRefresh: Boolean) = fetchValidated { validate ->
        OfflineCache.fetchWithCache(cacheType, CACHE_KEY, forceRefresh, validate) {
            ExtractionCatalogRemoteSource.get(buildParseUrl("https://wiki.biligame.com/klbq/api.php", WORKSHOP_PAGE, "text|wikitext"))
        }
    }

    /** Validation happens before publication, including force refresh. Reuse that parse only for
     * the actual winning payload; a single-flight follower may receive another reader's result. */
    internal suspend fun fetchValidated(load: suspend ((String) -> Boolean) -> OfflineCache.CacheResult?): WorkshopSnapshot? {
        var raw: String? = null
        var page: WorkshopPage? = null
        var rejected: WorkshopFormat? = null
        val result = load { payload ->
            try { page = parsePayload(payload); raw = payload; true }
            catch (e: WorkshopFormat) { rejected = e; false }
        } ?: run { rejected?.let { throw it }; return null }
        return WorkshopSnapshot(page?.takeIf { raw == result.payload } ?: parsePayload(result.payload), result.isFromCache, result.ageMs)
    }
}
