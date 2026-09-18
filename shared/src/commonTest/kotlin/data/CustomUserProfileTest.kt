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

    @Test
    fun guestTagAndCursorContract() {
        val response = SharedJson.decodeFromString<ProfileCommentsResponse>("""
            {"hasMore":true,"nextCursor":"1720000000:11","comments":[
              {"id":11,"authorBid":"anon","authorName":"小猫","authorTag":"12AB34CD56","content":"hello"}
            ]}
        """.trimIndent())
        assertTrue(response.hasMore)
        assertEquals("1720000000:11", response.nextCursor)
        assertEquals("小猫#12AB34CD56", response.comments.single().displayAuthor())
        assertEquals("访客#12AB34CD56", response.comments.single().copy(authorName = null).displayAuthor())
        assertEquals("小猫", response.comments.single().copy(authorBid = "member").displayAuthor())
    }

    @Test
    fun replyAndDeletedRootContract() {
        val response = SharedJson.decodeFromString<ProfileCommentsResponse>("""
            {"root":{"id":11,"authorBid":"","authorName":"已删除留言","content":"该留言已删除","deleted":true,"replyCount":1},
             "comments":[{"id":12,"rootId":11,"replyToId":11,"replyToName":"已删除留言","authorBid":"anon",
               "authorName":"访客","authorTag":"12AB34CD56","content":"hello","deleted":false}],"hasMore":false}
        """.trimIndent())
        assertTrue(response.root!!.deleted)
        assertEquals("已删除留言", response.root.displayAuthor())
        assertEquals(1, response.root.replyCount)
        assertEquals(11L, response.comments.single().rootId)
        assertEquals(11L, response.comments.single().replyToId)
        assertEquals("已删除留言", response.comments.single().replyToName)
        assertFalse(response.comments.single().deleted)
    }

    @Test
    fun separatePinnedFeedParsesWithoutChangingChronologicalCursor() {
        val response = SharedJson.decodeFromString<ProfileCommentsResponse>("""
            {"pinnedComments":[{"id":11,"authorBid":"Alice","content":"公告","pinnedAt":100}],
             "comments":[{"id":12,"authorBid":"Bob","content":"新留言"}],"nextCursor":"101:12"}
        """.trimIndent())
        assertEquals(100L, response.pinnedComments.single().pinnedAt)
        assertEquals(11L, response.pinnedComments.single().id)
        assertEquals(12L, response.comments.single().id)
        assertEquals("101:12", response.nextCursor)
    }

    @Test
    fun notificationContractParsesUnreadAndUnavailableStates() {
        val response = SharedJson.decodeFromString<ReplyNotificationsResponse>("""
            {"unreadCount":2,"notifications":[
              {"id":9,"rootId":2,"commentId":5,"status":"available","content":"hello","authorBid":"Alice"},
              {"id":8,"rootId":2,"commentId":4,"status":"hidden","content":null,"authorBid":null,"read":true}
            ],"nextCursor":"8"}
        """.trimIndent())
        assertEquals(2, response.unreadCount)
        assertEquals("Alice", response.notifications[0].displayAuthor())
        assertEquals("hidden", response.notifications[1].status)
        assertEquals(null, response.notifications[1].content)
        assertEquals("8", response.nextCursor)
    }
}
