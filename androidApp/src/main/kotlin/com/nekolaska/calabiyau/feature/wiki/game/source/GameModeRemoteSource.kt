package com.nekolaska.calabiyau.feature.wiki.game.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiParseSource
import com.nekolaska.calabiyau.core.wiki.WikiArticleLoader
import com.nekolaska.calabiyau.core.wiki.WikiArticleRequest
import com.nekolaska.calabiyau.core.wiki.WikiArticleSnapshot
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.feature.wiki.game.parser.GameModeParsers
import com.nekolaska.calabiyau.feature.wiki.game.model.GameModeDetail
import com.nekolaska.calabiyau.feature.wiki.game.model.ModeEntry
import com.nekolaska.calabiyau.feature.wiki.extraction.model.EXTRACTION_MODE_PAGE
import data.SharedJson
import kotlinx.serialization.json.*
import util.buildParseUrl

object GameModeRemoteSource {

    suspend fun fetchModeMapMappingWikitext(forceRefresh: Boolean): String? {
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.GAME_MODES, key = "mode_map_mapping", forceRefresh = forceRefresh,
            validatePayload = { mappingText(it)?.let(GameModeParsers::parseModeMapMapping)?.isNotEmpty() == true }
        ) { WikiEngine.safeGet(buildParseUrl("https://wiki.biligame.com/klbq/api.php", "模板:卡拉彼丘", "wikitext")) }
        return result?.payload?.let(::mappingText)
    }

    private fun mappingText(payload: String): String? = runCatching {
        val root = SharedJson.parseToJsonElement(payload).jsonObject
        if (root["error"] != null) return@runCatching null
        root["parse"]?.jsonObject?.get("wikitext")?.jsonObject?.get("*")?.jsonPrimitive?.content
    }.getOrNull()

    suspend fun loadCachedModeMapMappingWikitext(): String? {
        return WikiParseSource.loadCachedWikitext(
            cacheType = OfflineCache.Type.GAME_MODES,
            cacheKey = "mode_map_mapping"
        )?.wikitext
    }

    internal suspend fun readMode(mode: ModeEntry, maps: List<String>, force: Boolean, cacheOnly: Boolean,
        load: WikiArticleLoader): WikiArticleSnapshot<GameModeDetail>? {
        val type = OfflineCache.Type.GAME_MODES
        val key = "mode_${mode.pageName}"
        if (mode.pageName == EXTRACTION_MODE_PAGE) {
            return WikiParseSource.readArticle(WikiArticleRequest(mode.pageName, type, key, force, cacheOnly), load) { wt, html ->
                wt?.let { GameModeParsers.validatedExtraction(mode, it, maps, html) }
            }
        }
        val result = (if (cacheOnly) WikiParseSource.loadCachedWikitext(type, key)
            else WikiParseSource.fetchWikitext(mode.pageName, type, key, force)) ?: return null
        val detail = GameModeParsers.parseModeWikitext(mode, result.wikitext ?: return null, maps)
        if (detail.summary.isBlank() && detail.winCondition.isBlank() && detail.settings.isBlank() && detail.maps.isEmpty()) return null
        return WikiArticleSnapshot(detail, result.isFromCache, result.ageMs)
    }
}
