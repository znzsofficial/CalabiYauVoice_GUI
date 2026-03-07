package viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class UserInfoViewModelRecentLookupTest {
    @Test
    fun `appendRecentLookupId normalizes deduplicates and moves latest to front`() {
        val result = appendRecentLookupId(listOf("1001", "1002", "1003"), "#1002")

        assertEquals(listOf("1002", "1001", "1003"), result)
    }

    @Test
    fun `appendRecentLookupId respects max history size`() {
        val result = appendRecentLookupId(
            existing = listOf("1001", "1002", "1003", "1004"),
            newId = "1005",
            maxSize = 3
        )

        assertEquals(listOf("1005", "1001", "1002"), result)
    }

    @Test
    fun `appendRecentLookupId ignores blank input`() {
        val existing = listOf("1001", "1002")

        assertEquals(existing, appendRecentLookupId(existing, "   "))
    }
}

