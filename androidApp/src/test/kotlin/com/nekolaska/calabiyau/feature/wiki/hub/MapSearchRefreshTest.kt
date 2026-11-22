package com.nekolaska.calabiyau.feature.wiki.hub

import com.nekolaska.calabiyau.feature.wiki.map.api.MapListApi
import com.nekolaska.calabiyau.feature.wiki.map.model.*
import data.ApiResult
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MapSearchRefreshTest {
    @Test fun previewPickerKeepsOriginalPositionsForDisplayAndTemplateMatches() {
        val modes = MapListApi.GAME_MODES.map { (name, template) -> GameModeData(name, template, emptyList()) }
        assertEquals(modes.indices.toList(), matchingMapModes(modes, " ").map { it.index })
        assertEquals("爆破/团队乱斗", matchingMapModes(modes, "一般爆破").single().value.displayName)
        val match = matchingMapModes(modes, " 弦溯 ").single()
        assertEquals("弦溯战线", modes[match.index].templateName)
        assertEquals(emptyList(), matchingMapModes(modes, "不存在的模式"))
    }

    @Test fun mapWithoutThumbnailCanUseAnotherModesImageOrFallBackToDetailArtwork() {
        fun mode(name: String, image: String) = GameModeData(name, name, listOf(MapInfo("同一张图", "/wiki/map", image)))
        fun target(vararg modes: GameModeData) = buildDynamicSearchEntries(emptyList(), emptyList(), modes.toList())
            .single { it.kind == SearchEntryKind.Map && it.title == "同一张图" }.targetRoute

        val missing = mode("模式一", "")
        val withImage = mode("模式二", "https://example.test/map.png")
        assertEquals(WikiRoute.MapDetail("同一张图", null), target(missing))
        assertEquals(WikiRoute.MapDetail("同一张图", withImage.maps.single().imageUrl), target(missing, withImage))
    }

    @Test fun modeSearchTargetsTheNamedFilterBothBeforeAndAfterLoading() {
        val modes = listOf(GameModeData("排位爆破", "排位爆破", emptyList()), GameModeData("爆破/团队乱斗", "一般爆破", emptyList()))
        for (data in listOf(emptyList(), modes)) {
            val entries = buildDynamicSearchEntries(emptyList(), emptyList(), data)
            assertEquals(WikiRoute.ModeMaps("一般爆破"), entries.single { it.kind == SearchEntryKind.MapMode && it.title == "爆破/团队乱斗" }.targetRoute)
            assertEquals(WikiRoute.ModeMaps("排位爆破"), entries.single { it.kind == SearchEntryKind.MapMode && it.title == "排位爆破" }.targetRoute)
        }
        assertEquals(WikiRoute.Maps, decodeRoute("maps"))
    }

    @Test fun completeRefreshCanRemoveHistoricalFallbackFromMapListAndSearchTogether() = runBlocking {
        val historical = missingFallbackMaps(emptyList()).first()
        fun modes(name: String) = MapListApi.GAME_MODES.map { (display, template) ->
            GameModeData(display, template, listOf(MapInfo(name, "/wiki/$name", ""))) }
        var snapshot: List<GameModeData>? = null
        for (name in listOf(historical, "改名后")) {
            HubListDataSnapshot.capture("testMapSearch", { ApiResult.Success(modes(name)) },
                { it.all { m -> m.loadError == null } }, { snapshot = it })
            val current = snapshot!!
            assertEquals(setOf(name), current.flatMap { it.maps }.map { it.name }.toSet())
            assertTrue(missingFallbackMaps(current).isEmpty())
        }
        assertContains(missingFallbackMaps(snapshot!!.drop(1)), historical)
    }
}
