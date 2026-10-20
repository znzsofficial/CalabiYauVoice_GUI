package com.nekolaska.calabiyau.feature.wiki.hub

import java.net.URI
import java.net.URLDecoder

internal sealed interface WikiNavigation {
    data class Internal(val url: String) : WikiNavigation
    data class External(val url: String, val intent: Boolean = false) : WikiNavigation
    data object Blocked : WikiNavigation
}

/** Shared by link callbacks, native loads, POST interception and page-start origin checks. */
internal object WikiNavigationPolicy {
    private val allowed = setOf("wiki.biligame.com", "patchwiki.biligame.com", "biligame.com", "bilibili.com",
        "line.biligame.net", "passport.bilibili.com", "api.bilibili.com", "calabiyauwiki.pages.dev", "nekolaska.vip")
    private val blocked = setOf("d.bilibili.com")
    private val unsafeSchemes = setOf("", "javascript", "file", "content", "about", "data", "blob")

    fun documentUrl(url: String): String? = (classify(url) as? WikiNavigation.Internal)?.url
    fun mayLoadDocument(url: String): Boolean = documentUrl(url) == url

    fun classify(url: String): WikiNavigation {
        if (url.any { it.isISOControl() }) return WikiNavigation.Blocked
        // Android accepts intent:#Intent;...;end, which java.net.URI rejects for an empty scheme-specific part.
        if (url.substringBefore(':', "").equals("intent", ignoreCase = true)) {
            val fallback = intentFallback(runCatching { URI(url) }.getOrNull(), url)
            if (fallback != null) {
                val target = runCatching { URI(fallback) }.getOrNull()
                return if (target?.scheme?.lowercase() in setOf("http", "https")) classify(fallback)
                else WikiNavigation.Blocked
            }
            return WikiNavigation.External(url, intent = true)
        }
        val uri = runCatching { URI(url) }.getOrNull() ?: return WikiNavigation.Blocked
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val normalizedHost = host.trimEnd('.')
        if (blocked.any { normalizedHost == it || normalizedHost.endsWith(".$it") } || scheme in unsafeSchemes) {
            return WikiNavigation.Blocked
        }
        if (scheme == "https" && uri.userInfo == null && allowed.any { host == it || host.endsWith(".$it") }) {
            return WikiNavigation.Internal(url)
        }
        if (scheme in setOf("http", "https") && host.isEmpty()) return WikiNavigation.Blocked
        return WikiNavigation.External(url)
    }

    private fun intentFallback(uri: URI?, raw: String): String? {
        // java.net.URI safely returns null for opaque URIs; Android Uri.getQueryParameter throws.
        val rawQuery = uri?.rawQuery ?: raw.substringBefore('#').substringAfter('?', "").takeIf { it.isNotEmpty() }
        val query = rawQuery?.split('&')?.firstOrNull { it.substringBefore('=') == "browser_fallback_url" }
            ?.substringAfter('=', "")
        val fragment = Regex("(?:#Intent;|;)S\\.browser_fallback_url=([^;]+)").find(raw)?.groupValues?.get(1)
        val encoded = query ?: fragment ?: return null
        return runCatching { URLDecoder.decode(if (query == null) encoded.replace("+", "%2B") else encoded, "UTF-8") }
            .getOrDefault("") // Present but invalid fallback must be blocked, not mistaken for no fallback.
    }
}
