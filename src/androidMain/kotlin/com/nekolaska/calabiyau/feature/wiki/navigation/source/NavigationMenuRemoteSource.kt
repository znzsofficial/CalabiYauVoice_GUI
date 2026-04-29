package com.nekolaska.calabiyau.feature.wiki.navigation.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.SharedJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class NavigationMenuSourceResult(
    val sidebar: String,
    val isFromCache: Boolean,
    val ageMs: Long
)

object NavigationMenuRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    suspend fun fetchSidebar(forceRefresh: Boolean): NavigationMenuSourceResult? {
        val url = "$API?action=query&meta=allmessages&ammessages=sidebar&format=json"
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.NAVIGATION,
            key = "sidebar",
            forceRefresh = forceRefresh
        ) { WikiEngine.safeGet(url) } ?: return null

        val root = SharedJson.parseToJsonElement(result.payload).jsonObject
        val sidebar = root["query"]
            ?.jsonObject?.get("allmessages")
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("*")
            ?.jsonPrimitive?.content
            ?: return null

        return NavigationMenuSourceResult(
            sidebar = sidebar,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }
}
