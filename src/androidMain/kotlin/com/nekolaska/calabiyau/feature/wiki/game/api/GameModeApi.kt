package com.nekolaska.calabiyau.feature.wiki.game.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.feature.wiki.game.model.GameModeDetail
import com.nekolaska.calabiyau.feature.wiki.game.model.ModeEntry
import com.nekolaska.calabiyau.feature.wiki.game.parser.GameModeParsers
import com.nekolaska.calabiyau.feature.wiki.game.source.GameModeRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * 游戏模式 API（Android）。
 *
 * 通过 MediaWiki parse API 获取各战斗模式子页面的 wikitext，
 * 解析模式说明、获胜条件、模式设定等信息。
 */
object GameModeApi {

    init {
        MemoryCacheRegistry.register("GameModeApi", ::clearMemoryCache)
    }

    val MODES = listOf(
        ModeEntry("一般爆破", "战斗模式/一般爆破"),
        ModeEntry("团队乱斗", "战斗模式/团队乱斗"),
        ModeEntry("无限团竞", "战斗模式/无限团竞"),
        ModeEntry("极限推进", "战斗模式/极限推进"),
        ModeEntry("晶源感染", "战斗模式/晶源感染"),
        ModeEntry("极限刀战", "战斗模式/极限刀战"),
        ModeEntry("枪王乱斗", "战斗模式/枪王乱斗"),
        ModeEntry("晶能冲突", "战斗模式/晶能冲突"),
        ModeEntry("弦区争夺", "战斗模式/弦区争夺"),
        ModeEntry("大头乱斗", "战斗模式/大头乱斗"),
    )

    @Volatile
    private var cachedModes: List<GameModeDetail>? = null

    fun clearMemoryCache() { cachedModes = null }

    suspend fun fetchAllModes(forceRefresh: Boolean = false): ApiResult<List<GameModeDetail>> {
        if (!forceRefresh) {
            cachedModes?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedModes = it.value
        }
    }

    private data class ModeResult(
        val detail: GameModeDetail,
        val isFromCache: Boolean,
        val ageMs: Long
    )

    private suspend fun fetchFromNetwork(
        forceRefresh: Boolean
    ): ApiResult<List<GameModeDetail>> =
        withContext(Dispatchers.IO) {
            try {
                val modeMapMapping = fetchModeMapMapping(forceRefresh)

                val results = MODES.map { mode ->
                    async { fetchMode(mode, modeMapMapping, forceRefresh) }
                }.awaitAll()

                val data = results.filterNotNull()
                if (data.isEmpty()) {
                    ApiResult.Error("获取游戏模式失败", kind = ErrorKind.NETWORK)
                } else {
                    val isOffline = data.any { it.isFromCache }
                    val maxAge = data.maxOfOrNull { it.ageMs } ?: 0L
                    ApiResult.Success(
                        data.map { it.detail },
                        isOffline = isOffline,
                        cacheAgeMs = maxAge
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error("获取游戏模式失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    private suspend fun fetchModeMapMapping(forceRefresh: Boolean): Map<String, List<String>> {
        return try {
            val wikitext = GameModeRemoteSource.fetchModeMapMappingWikitext(forceRefresh) ?: return emptyMap()
            GameModeParsers.parseModeMapMapping(wikitext)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun fetchMode(
        mode: ModeEntry,
        modeMapMapping: Map<String, List<String>>,
        forceRefresh: Boolean
    ): ModeResult? {
        return try {
            val sourceResult = GameModeRemoteSource.fetchModeWikitext(mode.pageName, forceRefresh) ?: return null
            val detail = GameModeParsers.parseModeWikitext(
                mode,
                sourceResult.wikitext,
                modeMapMapping[mode.displayName] ?: emptyList()
            )
            ModeResult(
                detail = detail,
                isFromCache = sourceResult.isFromCache,
                ageMs = sourceResult.ageMs
            )
        } catch (_: Exception) {
            null
        }
    }
}
