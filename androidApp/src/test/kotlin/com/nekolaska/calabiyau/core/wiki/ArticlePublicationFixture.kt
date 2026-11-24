package com.nekolaska.calabiyau.core.wiki

import com.nekolaska.calabiyau.core.cache.OfflineCache
import kotlinx.serialization.json.*

/** Only the transport and disk are in memory; execute the production validation and selection policy. */
internal class ArticlePublicationFixture(var response: String?) {
    var disk: String? = null
    var requests = 0
    val load: WikiArticleLoader = { request, valid ->
        WikiParseSource.fetchArticlePayload(request.page, valid, online = !request.cacheOnly, tryNetworkOnMiss = false,
            network = { requests++; response }, read = { disk?.let { OfflineCache.CacheResult(it, true, 123L) } },
            publish = { disk = it })
    }
}

internal fun articlePayload(title: String, wt: String?, html: String?) = buildJsonObject {
    putJsonObject("parse") {
        put("title", title)
        if (wt != null) putJsonObject("wikitext") { put("*", wt) }
        if (html != null) putJsonObject("text") { put("*", html) }
    }
}.toString()
