package com.nekolaska.calabiyau.feature.wiki.game.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.wiki.UnusableWikiHtml
import com.nekolaska.calabiyau.core.wiki.WikiArticleLoader
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.feature.wiki.game.model.GameModeDetail
import com.nekolaska.calabiyau.feature.wiki.game.model.ModeEntry
import com.nekolaska.calabiyau.feature.wiki.game.parser.GameModeParsers
import com.nekolaska.calabiyau.feature.wiki.game.source.GameModeRemoteSource
import com.nekolaska.calabiyau.feature.wiki.extraction.model.EXTRACTION_MODE_PAGE
import data.ApiResult
import data.ErrorKind
import data.ioApiCall
import data.toErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 游戏模式 API（Android）。
 *
 * 通过 MediaWiki parse API 获取各战斗模式子页面的 wikitext，
 * 解析模式说明、获胜条件、模式设定等信息。
 */
object GameModeApi : CachedWikiApi<List<GameModeDetail>>("GameModeApi") {

    val EXTRACTION_MODE = ModeEntry("弦溯战线", EXTRACTION_MODE_PAGE)
    val MODES = listOf(
        EXTRACTION_MODE,
        ModeEntry("一般爆破", "战斗模式/一般爆破"),
        ModeEntry("团队乱斗", "战斗模式/团队乱斗"),
        ModeEntry("排位爆破", "战斗模式/排位爆破"),
        ModeEntry("个人乱斗", "战斗模式/个人乱斗"),
        ModeEntry("无限团竞", "战斗模式/无限团竞"),
        ModeEntry("极限推进", "战斗模式/极限推进"),
        ModeEntry("超弦推进", "战斗模式/超弦推进"),
        ModeEntry("晶源感染", "战斗模式/晶源感染"),
        ModeEntry("极限刀战", "战斗模式/极限刀战"),
        ModeEntry("枪王乱斗", "战斗模式/枪王乱斗"),
        ModeEntry("晶能冲突", "战斗模式/晶能冲突"),
        ModeEntry("弦区争夺", "战斗模式/弦区争夺"),
        ModeEntry("大头乱斗", "战斗模式/大头乱斗"),
        ModeEntry("炸弹派对", "战斗模式/炸弹派对"),
    )

    suspend fun fetchModes(
        focusedMode: ModeEntry? = null,
        forceRefresh: Boolean = false,
        cacheOnly: Boolean = false,
        allowMemoryCache: Boolean = true
    ): ApiResult<List<GameModeDetail>> {
        // A single-page response must never replace the all-modes memory snapshot.
        if (focusedMode != null) return fetchFromSource(forceRefresh, cacheOnly, focusedMode)
        return fetch(
            forceRefresh = forceRefresh,
            cacheOnly = cacheOnly,
            allowMemoryCache = allowMemoryCache
        )
    }

    override suspend fun fetchFromCache(): ApiResult<List<GameModeDetail>> = fetchFromSource(cacheOnly = true)

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<GameModeDetail>> =
        fetchFromSource(forceRefresh = forceRefresh)

    override fun shouldCache(value: List<GameModeDetail>) = value.size == MODES.size

    internal suspend fun fetchFromSource(
        forceRefresh: Boolean = false,
        cacheOnly: Boolean = false,
        focusedMode: ModeEntry? = null,
        load: WikiArticleLoader = WikiParseSource::loadArticle
    ): ApiResult<List<GameModeDetail>> =
        ioApiCall("获取游戏模式失败") {
            // The extraction article declares its maps; opening its introduction needs only this page.
            val modeMapMapping = if (focusedMode == EXTRACTION_MODE) emptyMap() else fetchModeMapMapping(forceRefresh, cacheOnly)

            val results = coroutineScope {
                (focusedMode?.let(::listOf) ?: MODES).map { mode ->
                    async {
                        try {
                            val snapshot = GameModeRemoteSource.readMode(mode, modeMapMapping[mode.displayName].orEmpty(),
                                forceRefresh, cacheOnly, load)
                            snapshot?.let { ApiResult.Success(it.value, isOffline = it.isFromCache, cacheAgeMs = it.ageMs) }
                        } catch (e: CancellationException) { throw e }
                        catch (e: UnusableWikiHtml) { ApiResult.Error(e.message.orEmpty(), kind = ErrorKind.PARSE) }
                        catch (e: Exception) { ApiResult.Error("获取${mode.displayName}失败", kind = e.toErrorKind()) }
                    }
                }.awaitAll()
            }

            val data = results.filterIsInstance<ApiResult.Success<GameModeDetail>>()
            if (data.isEmpty()) {
                results.filterIsInstance<ApiResult.Error>().firstOrNull() ?: ApiResult.Error("获取游戏模式失败", kind = ErrorKind.NETWORK)
            } else {
                ApiResult.Success(data.map { it.value }, isOffline = data.any { it.isOffline }, cacheAgeMs = data.maxOf { it.cacheAgeMs })
            }
        }

    private suspend fun fetchModeMapMapping(forceRefresh: Boolean, cacheOnly: Boolean): Map<String, List<String>> {
        return try {
            val wikitext = if (cacheOnly) {
                GameModeRemoteSource.loadCachedModeMapMappingWikitext()
            } else {
                GameModeRemoteSource.fetchModeMapMappingWikitext(forceRefresh)
            } ?: return emptyMap()
            GameModeParsers.parseModeMapMapping(wikitext)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyMap()
        }
    }

}
