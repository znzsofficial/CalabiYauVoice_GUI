package com.nekolaska.calabiyau.feature.character.list

import com.nekolaska.calabiyau.feature.character.list.CharacterListApi.FactionData
import data.ApiResult
import data.ErrorKind
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FactionResultsTest {
    @Test fun mixedResultsKeepTheirFactionSlotsAndOldestCacheAge() = runBlocking {
        val factions = CharacterListApi.FACTIONS
        val result = CharacterListApi.loadFactions("全部失败") { faction ->
            when (faction) {
                factions[1] -> ApiResult.Error("缺少缓存", ErrorKind.NOT_FOUND)
                factions[2] -> ApiResult.Success(FactionData(faction, emptyList()), isOffline = true, cacheAgeMs = 800)
                else -> ApiResult.Success(FactionData(faction, emptyList()))
            }
        }
        assertTrue(result is ApiResult.Success)
        assertEquals(factions, result.value.map { it.faction })
        assertEquals(listOf(null, "缺少缓存", null, null), result.value.map { it.loadError })
        assertTrue(result.isOffline)
        assertEquals(800L, result.cacheAgeMs)
    }

    @Test fun totalFailureUsesFirstFactionErrorInDisplayOrder() = runBlocking {
        val first = CharacterListApi.FACTIONS.first()
        val result = CharacterListApi.loadFactions("所有阵营缓存加载失败") { faction ->
            ApiResult.Error(faction, if (faction == first) ErrorKind.PARSE else ErrorKind.NETWORK)
        }
        assertEquals(ApiResult.Error("所有阵营缓存加载失败: $first", ErrorKind.PARSE), result)
    }
}
