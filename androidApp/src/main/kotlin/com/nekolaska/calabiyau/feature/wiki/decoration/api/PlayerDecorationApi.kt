package com.nekolaska.calabiyau.feature.wiki.decoration.api

import com.nekolaska.calabiyau.core.cache.KeyedCachedWikiApi
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogPolicy
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogSource
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogs
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationLuaSnapshot
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationSection
import com.nekolaska.calabiyau.feature.wiki.decoration.model.RawDecorationSection
import com.nekolaska.calabiyau.feature.wiki.decoration.parser.DecorationHtmlParser
import com.nekolaska.calabiyau.feature.wiki.decoration.parser.DecorationLuaCatalogParser
import com.nekolaska.calabiyau.feature.wiki.decoration.source.DecorationSource
import com.nekolaska.calabiyau.feature.wiki.decoration.source.PlayerDecorationRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PlayerDecorationApi {
    private val catalogs = DecorationCatalogApi(PlayerDecorationRemoteSource)
    suspend fun fetchDecoration(pageName: String, forceRefresh: Boolean = false, cacheOnly: Boolean = false,
        allowMemoryCache: Boolean = true): ApiResult<List<DecorationSection>> =
        catalogs.fetchDecoration(pageName, forceRefresh, cacheOnly, allowMemoryCache)
}

internal data class LoadedDecorationPage(val sections: List<DecorationSection>, val canCache: Boolean)

internal class DecorationCatalogApi(
    private val source: DecorationSource,
    cacheName: String = "PlayerDecorationApi",
    private val policyForPage: (String) -> DecorationCatalogPolicy = DecorationCatalogs::forPage
) : KeyedCachedWikiApi<String, LoadedDecorationPage>(cacheName) {
    suspend fun fetchDecoration(pageName: String, forceRefresh: Boolean = false, cacheOnly: Boolean = false,
        allowMemoryCache: Boolean = true): ApiResult<List<DecorationSection>> =
        when (val result = fetch(pageName, forceRefresh, cacheOnly, allowMemoryCache)) {
            is ApiResult.Success -> ApiResult.Success(result.value.sections, result.isOffline, result.cacheAgeMs)
            is ApiResult.Error -> result
        }

    override fun shouldCache(value: LoadedDecorationPage) = value.canCache
    override suspend fun fetchFromCache(key: String) = load(key, false, true)
    override suspend fun fetchFromNetwork(key: String, forceRefresh: Boolean) = load(key, forceRefresh, false)

    private suspend fun load(pageName: String, force: Boolean, cacheOnly: Boolean): ApiResult<LoadedDecorationPage> =
        withContext(Dispatchers.IO) {
            try {
                val policy = policyForPage(pageName)
                val lua = policy.source as? DecorationCatalogSource.Lua
                var fallbackReason: String? = null
                var pair: DecorationLuaSnapshot? = null
                var luaSections: List<RawDecorationSection>? = null
                if (lua != null) {
                    pair = optionalRead { if (cacheOnly) source.loadCachedLuaCatalog(policy) else source.fetchLuaCatalog(policy, force) }
                    if (pair != null) {
                        val parsed = DecorationLuaCatalogParser.parse(lua, pair.itemsText, pair.waysText)
                        if (parsed.isComplete && pair.ageMs <= OfflineCache.Type.DECORATIONS.maxAgeMs) luaSections = parsed.sections
                        else {
                            fallbackReason = if (pair.ageMs > OfflineCache.Type.DECORATIONS.maxAgeMs) "结构化资料缓存已过期" else "结构化资料不完整"
                            if (parsed.issues.isNotEmpty()) WikiParseLogger.warnMalformed(
                                "DecorationLua:$pageName", parsed.issues.joinToString("; "), pair.itemsText)
                        }
                    } else fallbackReason = "结构化资料暂不可用"
                    if (luaSections != null && !cacheOnly && pair?.isFromCache == false) {
                        return@withContext finish(luaSections, policy, force, cacheOnly, pair.isFromCache,
                            pair.ageMs, canCache = true, notice = null)
                    }
                    if (luaSections != null) fallbackReason = if (cacheOnly) "当前读取离线资料" else "结构化资料更新失败"
                }

                val html = optionalRead { if (cacheOnly) source.loadCachedPageHtml(pageName) else source.fetchPageHtml(pageName, force) }
                val htmlParsed = html?.html?.let(DecorationHtmlParser::parseResult)
                val htmlSections = htmlParsed?.takeIf { it.isComplete }?.sections.orEmpty()
                // Compare the same complete snapshots for prefetch and normal offline reads.
                // Fresh network HTML wins over cached Lua; tied cached snapshots prefer Lua.
                val useLuaCache = luaSections != null && (htmlSections.isEmpty() ||
                    ((cacheOnly || html?.isFromCache == true) && (pair?.ageMs ?: 0L) <= (html?.ageMs ?: 0L)))
                if (useLuaCache) return@withContext finish(luaSections, policy, force, cacheOnly, true,
                    pair?.ageMs ?: 0L, canCache = false,
                    notice = if (cacheOnly) "当前使用完整结构化缓存。" else "更新失败，当前使用完整结构化缓存；下次刷新将重试。")
                if (htmlSections.isNotEmpty()) {
                    return@withContext finish(htmlSections, policy, force, cacheOnly, html!!.isFromCache, html.ageMs,
                        canCache = lua == null,
                        notice = fallbackReason?.let { "$it，当前使用 Wiki 页面兼容资料；下次刷新将重试。" })
                }
                ApiResult.Error(if (html == null) "获取${pageName}失败，且无可用离线资料"
                    else "${pageName}目录不完整：${htmlParsed?.issues?.take(2)?.joinToString("；").orEmpty()}",
                    kind = if (html == null) ErrorKind.NETWORK else ErrorKind.PARSE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ApiResult.Error("获取${pageName}失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    private suspend fun finish(raw: List<RawDecorationSection>, policy: DecorationCatalogPolicy, force: Boolean,
        cacheOnly: Boolean, offline: Boolean, age: Long, canCache: Boolean, notice: String?): ApiResult<LoadedDecorationPage> {
        val files = DecorationAssembler.unresolvedImageFiles(raw)
        val urls = if (files.isEmpty()) emptyMap() else optionalRead {
            if (cacheOnly) source.loadCachedImageUrls(files.toList()) else source.fetchImageUrls(files.toList(), force)
        }.orEmpty()
        val sections = DecorationAssembler.assemble(raw, policy, urls, notice)
        val completeImages = files.all { !urls[it].isNullOrBlank() || !urls[it.replace('_', ' ')].isNullOrBlank() }
        return ApiResult.Success(LoadedDecorationPage(sections, canCache && completeImages), cacheOnly || offline, age)
    }

    private suspend fun <T> optionalRead(read: suspend () -> T): T? = try { read() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
}
