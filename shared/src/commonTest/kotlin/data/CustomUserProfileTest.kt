package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomUserProfileTest {

    @Test
    fun testDisplayNameFallback() {
        val withCustom = CustomUserProfile(
            bid = "123456",
            wikiUserId = 789,
            customName = "卡丘引航者",
            avatarUrl = "https://wiki.nekolaska.vip/api/user/avatar/avatars/123456_abc.png"
        )
        assertEquals("卡丘引航者", withCustom.displayName())

        val emptyCustom = CustomUserProfile(
            bid = "123456",
            customName = "   "
        )
        assertEquals("123456", emptyCustom.displayName())

        val nullCustom = CustomUserProfile(
            bid = "123456",
            customName = null
        )
        assertEquals("123456", nullCustom.displayName())
    }

    @Test
    fun testJsonSerialization() {
        val jsonStr = """
            {
                "bid": "10086",
                "wikiUserId": 34750,
                "customName": "星绘Official",
                "avatarUrl": "/api/user/avatar/avatars/10086_test.webp",
                "bio": "这是测试签名",
                "badge": "开发组",
                "updatedAt": 1720000000
            }
        """.trimIndent()

        val parsed = SharedJson.decodeFromString<CustomUserProfile>(jsonStr)
        assertEquals("10086", parsed.bid)
        assertEquals(34750L, parsed.wikiUserId)
        assertEquals("星绘Official", parsed.customName)
        assertEquals("/api/user/avatar/avatars/10086_test.webp", parsed.avatarUrl)
        assertEquals("这是测试签名", parsed.bio)
        assertEquals("开发组", parsed.badge)
        assertEquals(1720000000L, parsed.updatedAt)
    }

    @Test
    fun testResponseParsing() {
        val singleSuccess = """{"profile": {"bid": "999", "customName": "测试"}}"""
        val parsedSingle = SharedJson.decodeFromString<CustomUserProfileResponse>(singleSuccess)
        assertEquals("999", parsedSingle.profile?.bid)
        assertEquals("测试", parsedSingle.profile?.customName)
        assertNull(parsedSingle.error)

        val batchSuccess = """
            {
                "profiles": {
                    "111": {"bid": "111", "customName": "A"},
                    "222": {"bid": "222", "customName": "B"}
                }
            }
        """.trimIndent()
        val parsedBatch = SharedJson.decodeFromString<CustomUserProfilesResponse>(batchSuccess)
        assertEquals(2, parsedBatch.profiles.size)
        assertEquals("A", parsedBatch.profiles["111"]?.customName)
        assertEquals("B", parsedBatch.profiles["222"]?.customName)

        val errorResponse = """{"error": "D1 error"}"""
        val parsedError = SharedJson.decodeFromString<CustomUserProfileResponse>(errorResponse)
        assertNull(parsedError.profile)
        assertEquals("D1 error", parsedError.error)
    }
}
