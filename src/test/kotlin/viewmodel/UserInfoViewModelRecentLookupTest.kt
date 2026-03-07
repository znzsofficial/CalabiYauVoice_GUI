package viewmodel

import data.UserLookupMode
import org.junit.Assert.assertEquals
import org.junit.Test

class UserInfoViewModelRecentLookupTest {
    @Test
    fun `appendRecentLookup keeps trimmed BID queries`() {
        val result = appendRecentLookup(UserLookupMode.BID, listOf("oldBid"), "  TestBid  ")

        assertEquals(listOf("TestBid", "oldBid"), result)
    }

    @Test
    fun `appendRecentLookup normalizes wiki ids deduplicates and moves latest to front`() {
        val result = appendRecentLookup(UserLookupMode.WIKI_ID, listOf("1001", "1002", "1003"), "#1002")

        assertEquals(listOf("1002", "1001", "1003"), result)
    }

    @Test
    fun `appendRecentLookup ignores non digit wiki id entries`() {
        val existing = listOf("1001", "1002")

        assertEquals(existing, appendRecentLookup(UserLookupMode.WIKI_ID, existing, "Alice"))
    }

    @Test
    fun `normalizeLookupQuery uses different rules for bid and wiki id`() {
        assertEquals("TestBid", normalizeLookupQuery(UserLookupMode.BID, "  TestBid  "))
        assertEquals("5205017", normalizeLookupQuery(UserLookupMode.WIKI_ID, " #5205017 "))
    }

    @Test
    fun `appendRecentLookup respects max history size`() {
        val result = appendRecentLookup(
            mode = UserLookupMode.WIKI_ID,
            existing = listOf("1001", "1002", "1003", "1004"),
            newValue = "1005",
            maxSize = 3
        )

        assertEquals(listOf("1005", "1001", "1002"), result)
    }

    @Test
    fun `appendRecentLookup ignores blank input`() {
        val existing = listOf("1001", "1002")

        assertEquals(existing, appendRecentLookup(UserLookupMode.BID, existing, "   "))
    }
}
