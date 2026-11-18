package com.nekolaska.calabiyau.feature.wiki.hub

import com.nekolaska.calabiyau.feature.wiki.map.api.MapListApi
import com.nekolaska.calabiyau.feature.wiki.map.model.*
import data.ApiResult
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MapSearchRefreshTest {
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
