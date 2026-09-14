package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

        val errorResponse = """{"error": "D1 error"}"""
        val parsedError = SharedJson.decodeFromString<CustomUserProfileResponse>(errorResponse)
        assertNull(parsedError.profile)
        assertEquals("D1 error", parsedError.error)
    }

    @Test
    fun testCommentsAndLikesParsing() {
        val comments = """
            {
                "total": 2,
                "page": 1,
                "size": 20,
                "comments": [
                    {
                        "id": 11,
                        "authorBid": "10086",
                        "authorName": "星绘Official",
                        "authorAvatarUrl": "https://wiki.nekolaska.vip/api/user/avatar/avatars/10086_a.webp",
                        "content": "路过留个爪",
                        "createdAt": 1720000000
                    },
                    {
                        "id": 12,
                        "authorBid": "20099",
                        "content": "没有档案的作者",
                        "createdAt": 1720000001
                    }
                ]
            }
        """.trimIndent()
        val parsed = SharedJson.decodeFromString<ProfileCommentsResponse>(comments)
        assertEquals(2, parsed.total)
        assertEquals(2, parsed.comments.size)
        assertEquals("星绘Official", parsed.comments[0].authorName)
        assertEquals("10086", parsed.comments[0].authorBid)
        assertNull(parsed.comments[1].authorName)
        assertNull(parsed.comments[1].authorAvatarUrl)

        val likes = """{"count": 42, "likedByMe": true}"""
        val parsedLikes = SharedJson.decodeFromString<ProfileLikesResponse>(likes)
        assertEquals(42, parsedLikes.count)
        assertTrue(parsedLikes.likedByMe)

        val toggle = """{"success": true, "liked": false, "count": 41}"""
        val parsedToggle = SharedJson.decodeFromString<ProfileLikeToggleResponse>(toggle)
        assertTrue(parsedToggle.success)
        assertFalse(parsedToggle.liked)
        assertEquals(41, parsedToggle.count)
    }
}
