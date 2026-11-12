package com.nekolaska.calabiyau.feature.character.detail

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.wikiListHtml
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.CancellationException
import util.buildParseUrl

internal class CharacterSettingsApi(
    private val disk: suspend () -> OfflineCache.CacheResult? = {
        OfflineCache.getEntry(OfflineCache.Type.CHARACTER_DETAIL, "character_setting")?.let {
            OfflineCache.CacheResult(it.content, true, it.ageMs)
        }
    },
    private val network: suspend (Boolean) -> OfflineCache.CacheResult? = { force ->
        OfflineCache.fetchWithCache(OfflineCache.Type.CHARACTER_DETAIL, "character_setting", force,
            validatePayload = ::isValidPayload) {
            WikiEngine.safeGet(buildParseUrl("https://wiki.biligame.com/klbq/api.php", "超弦体设定", "text"))
        }
    }
) : CachedWikiApi<Map<String, CharacterSettingInfo>>("CharacterSettingsApi") {
    fun currentSnapshot(): Map<String, CharacterSettingInfo>? = getCachedValue()

    override suspend fun fetchFromCache(): ApiResult<Map<String, CharacterSettingInfo>> = load { disk() }
    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<Map<String, CharacterSettingInfo>> =
        load { network(forceRefresh) }

    private suspend fun load(fetch: suspend () -> OfflineCache.CacheResult?): ApiResult<Map<String, CharacterSettingInfo>> = try {
        val result = fetch()
        val html = result?.let { wikiListHtml(it.payload) }
        when {
            result == null -> ApiResult.Error("设定页暂时无法加载", ErrorKind.NETWORK)
            html == null -> ApiResult.Error("设定页响应格式异常", ErrorKind.PARSE)
            else -> {
                val parsed = CharacterSettingsParser.parse(html)
                if (!parsed.canCache) ApiResult.Error("设定页结构不完整", ErrorKind.PARSE)
                else ApiResult.Success(parsed.entries, result.isFromCache, result.ageMs)
            }
        }
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { ApiResult.Error("设定页加载失败：${e.message}", e.toErrorKind()) }

    companion object {
        fun isValidPayload(payload: String): Boolean =
            wikiListHtml(payload)?.let { CharacterSettingsParser.parse(it).canCache } == true
    }
}
