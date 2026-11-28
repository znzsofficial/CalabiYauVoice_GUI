package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.core.wiki.WikiHtmlPageSourceResult
import com.nekolaska.calabiyau.feature.wiki.extraction.api.Catalog
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionCatalogParsers
import com.nekolaska.calabiyau.feature.wiki.extraction.source.ExtractionCatalogRemoteSource
import com.nekolaska.calabiyau.feature.wiki.game.api.GameModeApi
import com.nekolaska.calabiyau.feature.wiki.game.model.ModeEntry
import com.nekolaska.calabiyau.feature.wiki.game.parser.GameModeParsers
import com.nekolaska.calabiyau.feature.wiki.hub.WikiRoute
import com.nekolaska.calabiyau.feature.wiki.hub.decodeRoute
import com.nekolaska.calabiyau.feature.wiki.hub.encode
import com.nekolaska.calabiyau.feature.wiki.hub.extractionEquipmentLinks
import com.nekolaska.calabiyau.feature.wiki.hub.extractionGuideLinks
import com.nekolaska.calabiyau.feature.wiki.hub.matchingHubEntries
import com.nekolaska.calabiyau.feature.wiki.hub.nativeWikiRoute
import com.nekolaska.calabiyau.feature.wiki.hub.toWikiRoute
import com.nekolaska.calabiyau.feature.wiki.map.model.RIFT_MAP
import com.nekolaska.calabiyau.feature.wiki.map.model.RIFT_WIDGET_LABEL
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assume.assumeTrue
import java.io.File
import java.net.URI
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
        assertEquals(WikiRoute.ExtractionMode, nativeWikiRoute(detail.wikiUrl))
        assertEquals(4, detail.summary.lines().size)
        assertTrue(detail.summary.contains("撤离点完成撤离"))
        assertTrue(detail.settings.contains("实体子弹"))
        assertTrue(detail.settings.contains("完美闪避"))
        assertFalse(detail.settings.contains(";枪械"))
        assertFalse(detail.settings.contains("图鉴"))
        assertEquals(listOf("维里昂裂隙"), detail.maps)
        assertEquals("", detail.winCondition) // No invented win-condition prose.
        assertFalse(detail.summary.contains("{{") || detail.summary.contains("<big>"))
    }

    @Test fun ordinaryModeDoesNotGainGalleryFallback() {
        val detail = GameModeParsers.parseModeWikitext(ModeEntry("其它模式", "战斗模式/其它"), fixture("overview.wikitext"), listOf("旧地图"))
        assertEquals("", detail.summary)
        assertEquals(listOf("旧地图"), detail.maps)
    }

    @Test fun splitCatalogUsesSuppliesAndKeepsUnnamedAttachmentQualityDistinct() {
        val path = System.getenv("EXTRACTION_SPLIT_AUDIT_DIR")
        assumeTrue("Set EXTRACTION_SPLIT_AUDIT_DIR to the November 21 evening captures", !path.isNullOrBlank())
        val root = File(requireNotNull(path))
        val loot = ExtractionCatalogKind.LOOT
        fun captured(stem: String, kind: ExtractionCatalogKind = loot) =
            ExtractionCatalogParsers.parseResult(root.resolve("$stem.html").readText(Charsets.UTF_8), kind)

        val route = decodeRoute("extractionCatalog|LOOT")
        assertEquals(WikiRoute.ExtractionCatalog(loot), route)
        assertEquals("extraction_loot_v1", loot.cacheKey)
        assertEquals("$EXTRACTION_MODE_PAGE/物资图鉴", loot.pageName)
        assertEquals(route, nativeWikiRoute("/klbq/${loot.pageName}"))
        assertEquals(route, nativeWikiRoute("/klbq/$EXTRACTION_MODE_PAGE/物品图鉴"))
        assertEquals(WikiRoute.ExtractionGuns, nativeWikiRoute("/klbq/$EXTRACTION_MODE_PAGE/枪械图鉴"))
        assertEquals(WikiRoute.ExtractionArmor, nativeWikiRoute("/klbq/$EXTRACTION_MODE_PAGE/防具图鉴"))
        for (other in listOf("loot", "guns", "armor")) assertFalse(captured(other).canCache, other)
        assertTrue(ExtractionCatalogParsers.parseResult(fixture("loot.html"), loot).canCache) // old offline cards
        val supplies = captured("supplies"); assertTrue(supplies.canCache)
        assertEquals(188, supplies.entries.size)
        val attachments = captured("attachments", ExtractionCatalogKind.ATTACHMENTS); assertTrue(attachments.canCache)
        assertEquals(142, attachments.entries.size)
        val q8 = filterExtractionEntries(attachments.entries, "", null, 8, null, ExtractionSort.DEFAULT, false)
        assertEquals(setOf("4084706", "4084707"), q8.map { it.id }.toSet())
        val artwork = buildDropArtworkIndex(supplies.entries + attachments.entries)
        for (entry in q8) {
            assertEquals("Q8", entry.qualityLabel)
            val q4 = attachments.entries.single { it.name == entry.name && it.quality == 4 }
            assertEquals(assertNotNull(q4.imageUrl), artwork.imageFor(entry.name, "Q4 完美"))
            assertEquals(assertNotNull(entry.imageUrl), artwork.imageFor(entry.name, "Q8"))
            assertNull(artwork.imageFor(entry.name, "Q8 特殊"))
        }
    }

    @Test fun topicSearchLeadsToTheHubButSpecificQueriesStillOpenTheirDestinations() {
        val loot = WikiRoute.ExtractionCatalog(ExtractionCatalogKind.LOOT)
        val attachments = WikiRoute.ExtractionCatalog(ExtractionCatalogKind.ATTACHMENTS)
        val drops = WikiRoute.ExtractionCatalog(ExtractionCatalogKind.DROPS)
        for ((query, expected) in listOf(
            "搜打撤" to WikiRoute.ExtractionHub, "弦溯战线" to WikiRoute.ExtractionHub, "弦溯战线专题" to WikiRoute.ExtractionHub,
            "芯元" to WikiRoute.ExtractionHub, "阵列" to WikiRoute.ExtractionHub, "芯元阵列" to WikiRoute.ExtractionHub,
            "搜打撤图鉴" to loot, "弦溯战线物品图鉴" to loot, "物资图鉴" to loot,
            "搜打撤枪械" to WikiRoute.ExtractionGuns, "防具图鉴" to WikiRoute.ExtractionArmor,
            "搜打撤工坊" to WikiRoute.ExtractionWorkshop,
            "弦溯战线配件图鉴" to attachments, "掉落图鉴" to drops, "找物品" to drops, "查来源" to WikiRoute.ExtractionSources,
            "掉落查询" to drops, "按物品" to drops, "按来源" to WikiRoute.ExtractionSources
        )) assertEquals(expected, matchingHubEntries(query).firstOrNull()?.targetRoute, query)
    }

    @Test fun topicDirectoryRoutesNativeArticlesAndKeepsWikiOriginalsOnTheWeb() {
        val links = extractionEquipmentLinks + extractionGuideLinks
        assertEquals(listOf("阵列芯元", RIFT_WIDGET_LABEL, "掉落表格"), links.filter { it.route == null }.map { it.label })
        assertEquals("战斗模式/弦溯战线/阵列芯元", extractionGuideLinks.single { it.label == "阵列芯元" }.article)
        assertEquals(setOf(WikiRoute.ExtractionGuns, WikiRoute.ExtractionArmor, WikiRoute.ExtractionWorkshop,
            WikiRoute.ExtractionCatalog(ExtractionCatalogKind.LOOT), WikiRoute.ExtractionCatalog(ExtractionCatalogKind.ATTACHMENTS),
            WikiRoute.ExtractionMode, WikiRoute.MapDetail(RIFT_MAP, null)), links.mapNotNull { it.route }.toSet())
        for (link in links) {
            val uri = URI(link.originalUrl)
            assertEquals("https", uri.scheme)
            assertEquals("wiki.biligame.com", uri.rawAuthority)
            assertEquals("/klbq/${link.article}", uri.path)
            assertFalse(uri.rawPath.contains("%2F", ignoreCase = true))
            assertNull(uri.rawQuery)
            assertNull(uri.rawFragment)
            assertEquals(link.route, nativeWikiRoute(link.originalUrl))
            assertNull(nativeWikiRoute("${link.originalUrl}?action=edit"))
            // Article routing above also covers MapDetail. Its saved-state codec uses Android Uri;
            // the fixed extraction route tokens can additionally round-trip on the plain JVM.
            link.route?.takeUnless { it is WikiRoute.MapDetail }?.let { assertEquals(it, decodeRoute(it.encode())) }
        }
    }

    @Test fun catalogRouteRoundTripsWithoutAffectingOldSavedRoutes() {
        for (kind in ExtractionCatalogKind.entries) {
            val route = WikiRoute.ExtractionCatalog(kind)
            assertEquals(route, decodeRoute("extractionCatalog|${kind.name}"))
            assertEquals(route, decodeRoute(route.encode()))
        }
        val sources = DropTask.SOURCES.toWikiRoute()
        assertEquals(WikiRoute.ExtractionSources, sources)
        assertEquals(decodeRoute("extractionCatalog|DROPS"), DropTask.ITEMS.toWikiRoute())
        val stack = listOf(WikiRoute.Home, WikiRoute.ExtractionHub, sources)
        assertEquals(stack, stack.map { decodeRoute(it.encode()) })
        assertEquals(WikiRoute.ExtractionMode, decodeRoute(WikiRoute.ExtractionMode.encode()))
        assertEquals(WikiRoute.QuickEntryEditor, decodeRoute(WikiRoute.QuickEntryEditor.encode()))
        assertEquals(WikiRoute.ExtractionMode, nativeWikiRoute("/klbq/$EXTRACTION_MODE_PAGE"))
        assertNull(nativeWikiRoute("/klbq/$EXTRACTION_MODE_PAGE#模式设定"))
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

    @Test fun survivingCardsCannotReplaceCompleteMemoryCacheAfterTableDamage() = runBlocking {
        val good = fixture("drops.html") + fixture("drop_summary_20261103.html")
        var html = good
        val api = Catalog(ExtractionCatalogKind.DROPS, cache = { null }, network = { _, _ -> WikiHtmlPageSourceResult(html, false, 0) })
        val first = assertIs<ApiResult.Success<List<ExtractionEntry>>>(api.fetch())
        html = good.replace("掉落池条目（按稀有度分行）", "掉落池条目（未知表头）")
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(api.fetch(forceRefresh = true)).kind)
        assertEquals(first.value, assertIs<ApiResult.Success<List<ExtractionEntry>>>(api.fetch()).value)
    }
}
