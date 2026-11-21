package com.nekolaska.calabiyau.core.wiki

import kotlin.test.*

class CookieSessionGuardTest {
    @Test fun voteLoginSurvivesWikiCookieRefreshButNotAccountSwitchOrLogout() {
        val guard = CookieSessionGuard()
        val first = "DedeUserID=42; SESSDATA=A; klbqwiki_session=old; pref=1"
        val login = voteLoginKey(first)!!
        val ticket = guard.capture(login, login)
        val refreshed = "pref=2; klbqwiki_session=new; SESSDATA=A; DedeUserID=42"
        assertTrue(guard.isCurrent(ticket, voteLoginKey(refreshed)))
        assertFalse(guard.isCurrent(ticket, voteLoginKey(refreshed.replace("=42", "=43"))))
        assertFalse(guard.isCurrent(ticket, voteLoginKey(first)))
        val fresh = guard.capture(login, login)
        guard.invalidate()
        assertFalse(guard.isCurrent(fresh, voteLoginKey(refreshed)))
    }
    @Test fun responseCanUpdateOnlyItsUnchangedSession() {
        val guard = CookieSessionGuard()
        var store: String? = "session=A"
        val request = guard.capture("session=A", store)
        assertTrue(guard.applyIfCurrent(request, { store }) { store = "session=A-renewed" })
        assertEquals("session=A-renewed", store)
    }

    @Test fun lateAResponseCannotOverwriteBOrRestoreLoggedOutCookies() {
        for (next in listOf("session=B", null)) {
            val guard = CookieSessionGuard()
            var store: String? = "session=A"
            val request = guard.capture("session=A", store)
            store = next
            assertFalse(guard.applyIfCurrent(request, { store }) { store = "session=A" })
            assertEquals(next, store)
        }
    }

    @Test fun logoutInvalidatesOutstandingRequestsEvenIfTheSameCookieReappears() {
        val guard = CookieSessionGuard()
        val request = guard.capture("A", "A")
        guard.invalidate()
        var writes = 0
        assertFalse(guard.applyIfCurrent(request, { "A" }) { writes++ })
        val fresh = guard.capture("A", "A")
        assertTrue(guard.applyIfCurrent(fresh, { "A" }) { writes++ })
        assertEquals(1, writes)
    }

    @Test fun aQueuedRequestAlreadyUsingAnOldCookieNeverGetsWritePermission() {
        val guard = CookieSessionGuard()
        val old = guard.capture("A", "B")
        assertFalse(guard.isCurrent(old, "B"))
        assertTrue(guard.isCurrent(guard.capture("B", "B"), "B"))
    }

    @Test fun csrfKeysSeparateHashCollisionsAndApiEndpointsWithoutStoringRawCookies() {
        val a = "session=Aa"; val b = "session=BB"
        assertEquals(a.hashCode(), b.hashCode())
        val key = csrfSessionCacheKey("https://example.test/api", a)
        assertNotEquals(key, csrfSessionCacheKey("https://example.test/api", b))
        assertNotEquals(key, csrfSessionCacheKey("https://example.test/other", a))
        assertFalse(key.contains(a)); assertEquals(64, key.substringAfter('#').length)
    }
}
