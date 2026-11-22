package com.nekolaska.calabiyau.feature.wiki.hub

import com.nekolaska.calabiyau.feature.wiki.map.api.MapListApi
import com.nekolaska.calabiyau.feature.wiki.map.model.*
import data.ApiResult
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MapSearchRefreshTest {
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
