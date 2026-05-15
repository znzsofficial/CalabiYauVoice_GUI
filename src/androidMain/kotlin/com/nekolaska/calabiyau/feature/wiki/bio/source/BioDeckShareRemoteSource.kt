package com.nekolaska.calabiyau.feature.wiki.bio.source

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiAuthHelper

object BioDeckShareRemoteSource {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    fun buildUrl(vararg params: Pair<String, String>): String {
        return WikiAuthHelper.buildUrl(API, *params)
    }

    fun getWikiCookies(): String? {
        return WikiAuthHelper.getWikiCookies()
    }

    fun fetchCsrfToken(cookies: String): String? {
        return WikiAuthHelper.fetchCsrfToken(API, cookies)
    }

    fun httpGet(url: String): String? {
        return WikiAuthHelper.httpGet(url, expectJson = true)
    }

    suspend fun httpGetWithCache(
        url: String,
        cacheKey: String,
        forceRefresh: Boolean
    ): String? {
        return OfflineCache.fetchWithCache(
            type = OfflineCache.Type.BIO_CARDS,
            key = cacheKey,
            forceRefresh = forceRefresh
        ) { httpGet(url) }?.payload
    }

    fun httpGetWithCookies(url: String, cookies: String): String? {
        return WikiAuthHelper.httpGetWithCookies(url, cookies, expectJson = true)
    }
}
