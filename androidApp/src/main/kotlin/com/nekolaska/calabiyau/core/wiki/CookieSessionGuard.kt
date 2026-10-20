package com.nekolaska.calabiyau.core.wiki

import java.security.MessageDigest

/** Caller serializes cookie-store reads/writes. Explicit logout invalidates even an identical-cookie ABA. */
internal class CookieSessionGuard {
    class Ticket internal constructor(internal val revision: Long, internal val cookies: String)
    private var revision = 0L
    private var initialized = false
    private var observed: String? = null

    private fun observe(current: String?) {
        if (!initialized || current != observed) { revision++; observed = current; initialized = true }
    }
    fun capture(requestCookies: String, currentCookies: String?): Ticket {
        observe(currentCookies)
        return Ticket(revision, requestCookies)
    }
    fun isCurrent(ticket: Ticket, currentCookies: String?): Boolean {
        observe(currentCookies)
        return ticket.revision == revision && ticket.cookies == currentCookies
    }
    fun applyIfCurrent(ticket: Ticket, readCookies: () -> String?, write: () -> Unit): Boolean {
        if (!isCurrent(ticket, readCookies())) return false
        write()
        return true
    }
    fun invalidate() { revision++; initialized = false; observed = null }
}

internal fun csrfSessionCacheKey(apiUrl: String, cookies: String): String = "$apiUrl#" +
    MessageDigest.getInstance("SHA-256").digest(cookies.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
