package com.nekolaska.calabiyau.feature.wiki.decoration.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.core.wiki.fetchBatchImageUrls
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogPolicy
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogSource
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationLuaSnapshot
import util.buildParseUrl

internal interface DecorationSource {
    suspend fun fetchPageHtml(pageName: String, forceRefresh: Boolean): WikiHtmlPageSourceResult?
    suspend fun loadCachedPageHtml(pageName: String): WikiHtmlPageSourceResult?
    suspend fun fetchLuaCatalog(policy: DecorationCatalogPolicy, forceRefresh: Boolean): DecorationLuaSnapshot?
    suspend fun loadCachedLuaCatalog(policy: DecorationCatalogPolicy): DecorationLuaSnapshot?
    suspend fun fetchImageUrls(fileNames: List<String>, forceRefresh: Boolean): Map<String, String>
    suspend fun loadCachedImageUrls(fileNames: List<String>): Map<String, String>
}

internal object PlayerDecorationRemoteSource : DecorationSource {
    override suspend fun fetchPageHtml(pageName: String, forceRefresh: Boolean): WikiHtmlPageSourceResult? {
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.DECORATIONS, key = pageCacheKey(pageName), forceRefresh = forceRefresh,
            validatePayload = { DecorationHtmlCachePayload.isValid(pageName, it) }
        ) {
            WikiEngine.safeGet(buildParseUrl("https://wiki.biligame.com/klbq/api.php", pageName, "text"))
        } ?: return null
        return DecorationHtmlCachePayload.html(pageName, result.payload)?.let {
            WikiHtmlPageSourceResult(it, result.isFromCache, result.ageMs)
        }
    }

    override suspend fun loadCachedPageHtml(pageName: String): WikiHtmlPageSourceResult? {
        val entry = OfflineCache.getEntry(OfflineCache.Type.DECORATIONS, pageCacheKey(pageName)) ?: return null
        return DecorationHtmlCachePayload.html(pageName, entry.content)?.let {
            WikiHtmlPageSourceResult(it, true, entry.ageMs)
        }
    }

    override suspend fun fetchLuaCatalog(policy: DecorationCatalogPolicy, forceRefresh: Boolean): DecorationLuaSnapshot? {
        val lua = policy.source as? DecorationCatalogSource.Lua ?: return null
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.DECORATIONS,
            key = pairCacheKey(policy.pageName),
            forceRefresh = forceRefresh,
            validatePayload = { DecorationLuaCachePayload.isValid(policy, it) }
        ) {
            // One cache publication for BOTH dependencies, after joint schema/ID/coverage validation.
            val items = fetchModule(lua.modulePage) ?: return@fetchWithCache null
            val ways = fetchModule(lua.waysPage) ?: return@fetchWithCache null
            DecorationLuaCachePayload.encode(items, ways)
        } ?: return null
        return DecorationLuaCachePayload.decode(result.payload)?.copy(isFromCache = result.isFromCache, ageMs = result.ageMs)
    }

    override suspend fun loadCachedLuaCatalog(policy: DecorationCatalogPolicy): DecorationLuaSnapshot? {
        val entry = OfflineCache.getEntry(OfflineCache.Type.DECORATIONS, pairCacheKey(policy.pageName)) ?: return null
        if (!DecorationLuaCachePayload.isValid(policy, entry.content)) return null
        return DecorationLuaCachePayload.decode(entry.content)?.copy(isFromCache = true, ageMs = entry.ageMs)
    }

    private suspend fun fetchModule(page: String): String? {
        val response = WikiEngine.safeGet(buildParseUrl("https://wiki.biligame.com/klbq/api.php", page, "wikitext"))
            ?: return null
        return DecorationLuaCachePayload.moduleText(response)
    }

    override suspend fun fetchImageUrls(fileNames: List<String>, forceRefresh: Boolean): Map<String, String> =
        fetchBatchImageUrls(fileNames.map { it.replace('_', ' ') }) { url ->
            OfflineCache.fetchWithCache(OfflineCache.Type.DECORATIONS, imageCacheKey(url), forceRefresh) {
                WikiEngine.safeGet(url)
            }?.payload
        }.withRequestedAliases(fileNames)

    override suspend fun loadCachedImageUrls(fileNames: List<String>): Map<String, String> =
        fetchBatchImageUrls(fileNames.map { it.replace('_', ' ') }) { url ->
            OfflineCache.getEntry(OfflineCache.Type.DECORATIONS, imageCacheKey(url))?.content
        }.withRequestedAliases(fileNames)

    private fun Map<String, String>.withRequestedAliases(files: List<String>): Map<String, String> =
        this + files.mapNotNull { file -> this[file.replace('_', ' ')]?.let { file to it } }.toMap()

    private fun pageCacheKey(page: String) = "decoration_$page"
    private fun pairCacheKey(page: String) = "decoration_lua_pair_v1_$page"
    private fun imageCacheKey(url: String) = "decoration_image_urls_${url.hashCode().toString(16)}"
}
