package com.nekolaska.calabiyau.feature.wiki.bio.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiAuthHelper

object BioDeckShareRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    fun getWikiCookies(): String? {
        return WikiAuthHelper.getWikiCookies()
    }

    suspend fun fetchCsrfToken(cookies: String): String? {
        return WikiAuthHelper.fetchCsrfToken(API, cookies)
    }

    suspend fun httpGet(url: String): String? {
        return WikiAuthHelper.httpGet(url, expectJson = true)
    }

    suspend fun httpGetWithCache(
        url: String,
        cacheKey: String,
        forceRefresh: Boolean,
        validate: (String) -> Boolean
    ): OfflineCache.CacheResult? {
        return OfflineCache.fetchWithCache(
            type = OfflineCache.Type.BIO_CARDS,
            key = cacheKey,
            forceRefresh = forceRefresh, validatePayload = validate
        ) { httpGet(url) }
    }

}
