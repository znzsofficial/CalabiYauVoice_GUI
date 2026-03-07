package data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiCookieManagerTest {
    @Test
    fun `previewCookieImport parses cookie header line`() {
        val preview = WikiCookieManager.previewCookieImport(
            "Cookie: gamecenter_wiki_UserID=5205017; gamecenter_wiki_UserName=Test%20User; SESSDATA=abc"
        )

        assertTrue(preview.hasCookies)
        assertEquals(3, preview.cookieCount)
        assertEquals("Test User", preview.detectedUserName)
        assertEquals("5205017", preview.detectedUserId)
        assertEquals(
            "gamecenter_wiki_UserID=5205017; gamecenter_wiki_UserName=Test%20User; SESSDATA=abc",
            preview.normalizedCookieString
        )
    }

    @Test
    fun `previewCookieImport extracts cookie data from request headers`() {
        val preview = WikiCookieManager.previewCookieImport(
            """
            GET /demo HTTP/1.1
            Host: wiki.biligame.com
            Cookie: foo=bar; gamecenter_wiki_UserID=42
            User-Agent: Test
            """.trimIndent()
        )

        assertTrue(preview.hasCookies)
        assertEquals(2, preview.cookieCount)
        assertEquals("42", preview.detectedUserId)
    }

    @Test
    fun `previewCookieImport rejects unrelated header text`() {
        val preview = WikiCookieManager.previewCookieImport(
            """
            GET /demo HTTP/1.1
            Host: wiki.biligame.com
            User-Agent: Test
            """.trimIndent()
        )

        assertFalse(preview.hasCookies)
        assertEquals(0, preview.cookieCount)
        assertEquals("", preview.normalizedCookieString)
    }
}

