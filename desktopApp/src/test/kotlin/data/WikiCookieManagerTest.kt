package data

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WikiCookieManagerTest {
    @AfterTest
    fun cleanup() {
        WikiCookieManager.clearCookies()
    }

    @Test
    fun invalidImportClearsManagerAndCookieJarState() {
        assertEquals(2, WikiCookieManager.importCookies("session=abc; user=123"))
        assertTrue(WikiCookieManager.hasCookies)

        assertEquals(0, WikiCookieManager.importCookies("not-a-cookie"))
        assertFalse(WikiCookieManager.hasCookies)
        assertEquals("", WikiCookieManager.currentCookieString)
        assertEquals("", WikiCookieManager.getCookieHeader())
    }
}
