package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.extraction.api.Catalog
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.source.ExtractionCatalogRemoteSource
import com.nekolaska.calabiyau.feature.wiki.game.api.GameModeApi
import com.nekolaska.calabiyau.feature.wiki.game.model.ModeEntry
import com.nekolaska.calabiyau.feature.wiki.game.parser.GameModeParsers
import com.nekolaska.calabiyau.feature.wiki.hub.WikiRoute
import com.nekolaska.calabiyau.feature.wiki.hub.decodeRoute
import com.nekolaska.calabiyau.feature.wiki.hub.encode
import com.nekolaska.calabiyau.feature.wiki.hub.catalogHubExtractionEntries
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.*

class ExtractionIntegrationTest {
    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/fixtures/extraction/$name")).readText(Charsets.UTF_8)

    @Test fun rawCacheValidationRejectsWafBuildingPagesAndWrongCatalogs() {
        fun payload(html: String) = buildJsonObject { putJsonObject("parse") { putJsonObject("text") { put("*", html) } } }.toString()
        assertTrue(ExtractionCatalogRemoteSource.isValidPayload(payload(fixture("loot.html")), ExtractionCatalogKind.LOOT))
        assertFalse(ExtractionCatalogRemoteSource.isValidPayload(payload(fixture("loot.html")), ExtractionCatalogKind.ATTACHMENTS))
        assertFalse(ExtractionCatalogRemoteSource.isValidPayload(payload("<p>建设中</p>"), ExtractionCatalogKind.DROPS))
        assertFalse(ExtractionCatalogRemoteSource.isValidPayload("<html>WAF</html>", ExtractionCatalogKind.LOOT))
        assertFalse(ExtractionCatalogRemoteSource.isValidPayload("""{"error":{"code":"missingtitle"}}""", ExtractionCatalogKind.LOOT))
        assertTrue(ExtractionCatalogRemoteSource.isValidPayload(payload(fixture("drop_summary_20261103.html")), ExtractionCatalogKind.DROPS))
    }

    @Test fun modeOverviewReadsActualTeachingCaptionsWithoutCatalogNavigation() {
        val entry = GameModeApi.MODES.single { it.pageName == EXTRACTION_MODE_PAGE }
        val detail = GameModeParsers.parseModeWikitext(entry, fixture("overview.wikitext"), emptyList())
        assertEquals(4, detail.summary.lines().size)
        assertTrue(detail.summary.contains("撤离点完成撤离"))
        assertTrue(detail.settings.contains("实体子弹"))
        assertTrue(detail.settings.contains("完美闪避"))
        assertFalse(detail.settings.contains(";枪械"))
        assertEquals(listOf("维里昂裂隙"), detail.maps)
        assertEquals("", detail.winCondition) // No invented win-condition prose.
        assertFalse(detail.summary.contains("{{") || detail.summary.contains("<big>"))
    }

    @Test fun ordinaryModeDoesNotGainGalleryFallback() {
        val detail = GameModeParsers.parseModeWikitext(ModeEntry("其它模式", "战斗模式/其它"), fixture("overview.wikitext"), listOf("旧地图"))
        assertEquals("", detail.summary)
        assertEquals(listOf("旧地图"), detail.maps)
    }

    @Test fun allThreeCatalogEntriesBelongToTheAppearanceAndCatalogHub() {
        assertEquals(ExtractionCatalogKind.entries, catalogHubExtractionEntries.map { it.kind })
        assertEquals(3, catalogHubExtractionEntries.map { it.route }.distinct().size)
        catalogHubExtractionEntries.forEach {
            assertEquals(WikiRoute.ExtractionCatalog(it.kind), it.route)
            assertTrue(it.subtitle.isNotBlank())
        }
        assertFalse(GameModeParsers.parseModeWikitext(ModeEntry("弦溯战线", EXTRACTION_MODE_PAGE),
            fixture("overview.wikitext"), emptyList()).settings.contains("图鉴"))
    }

    @Test fun catalogRouteRoundTripsWithoutAffectingOldSavedRoutes() {
        for (kind in ExtractionCatalogKind.entries) {
            val route = WikiRoute.ExtractionCatalog(kind)
            assertEquals(route, decodeRoute(route.encode()))
        }
        assertNull(decodeRoute("extractionCatalog|UNKNOWN"))
        assertEquals(WikiRoute.GameModes, decodeRoute("gameModes"))
        assertEquals(WikiRoute.Items, decodeRoute("items"))
        assertEquals(3, ExtractionCatalogKind.entries.map { it.cacheKey }.distinct().size)
        assertTrue(ExtractionCatalogKind.entries.all { it.cacheKey.startsWith("extraction_") })
    }

    @Test fun cacheOnlyNeverCallsTheNetworkAndReturnsAge() = runBlocking {
        var calls = 0
        val api = Catalog(ExtractionCatalogKind.LOOT,
            cache = { WikiHtmlPageSourceResult(fixture("loot.html"), true, 456L) },
            network = { _, _ -> calls++; null })
        val result = assertIs<ApiResult.Success<*>>(api.fetch(cacheOnly = true))
        assertEquals(0, calls)
        assertTrue(result.isOffline)
        assertEquals(456L, result.cacheAgeMs)
    }

    @Test fun refreshBypassesMemoryAndMalformedPageDoesNotEraseTheLastSuccess() = runBlocking {
        var malformed = false
        val forces = mutableListOf<Boolean>()
        val api = Catalog(ExtractionCatalogKind.LOOT, cache = { null }, network = { _, force ->
            forces += force
            WikiHtmlPageSourceResult(if (malformed) "<p>建设中</p>" else fixture("loot.html"), false, 0)
        })
        assertIs<ApiResult.Success<*>>(api.fetch())
        assertIs<ApiResult.Success<*>>(api.fetch())
        assertEquals(1, forces.size)
        malformed = true
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(api.fetch(forceRefresh = true)).kind)
        assertEquals(listOf(false, true), forces)
        assertIs<ApiResult.Success<*>>(api.fetch())
        Unit
    }

    @Test fun cancellationIsNotConvertedToAnEmptyCatalog() = runBlocking {
        val api = Catalog(ExtractionCatalogKind.DROPS, cache = { null }, network = { _, _ -> throw CancellationException("cancel") })
        assertFailsWith<CancellationException> { api.fetch() }
        Unit
    }
}
