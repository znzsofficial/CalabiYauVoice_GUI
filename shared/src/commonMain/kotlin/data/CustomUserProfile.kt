package data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cloudflare D1 存储的自定义 Wiki 用户档案映射数据模型。
 */
@Serializable
data class CustomUserProfile(
    @SerialName("bid") val bid: String,
    @SerialName("wikiUserId") val wikiUserId: Long? = null,
    @SerialName("customName") val customName: String? = null,
    @SerialName("avatarUrl") val avatarUrl: String? = null,
    @SerialName("bio") val bio: String? = null,
    @SerialName("badge") val badge: String? = null,
    @SerialName("updatedAt") val updatedAt: Long = 0L
) {
    /** 优先显示自定义昵称，若无则回退到原始 BID */
    fun displayName(fallbackBid: String = bid): String =
        customName?.takeIf { it.isNotBlank() } ?: fallbackBid
}

@Serializable
data class CustomUserProfileResponse(
    val profile: CustomUserProfile? = null,
    val error: String? = null
)

@Serializable
data class CustomUserAvatarUploadResponse(
    val avatarUrl: String? = null,
    val objectKey: String? = null,
    val error: String? = null
)

@Serializable
data class CustomUserUpdateResponse(
    val success: Boolean = false,
    val profile: CustomUserProfile? = null,
    val error: String? = null
)

/** 档案留言（作者名/头像来自其自定义档案，未设置时客户端回退 BID；匿名留言带访客编号 authorTag） */
@Serializable
data class ProfileComment(
    val id: Long,
    val authorBid: String,
    val authorWikiUserId: Long? = null,
    val authorName: String? = null,
    val authorTag: String? = null,
    val authorAvatarUrl: String? = null,
    val content: String,
    val createdAt: Long = 0L,
    val rootId: Long? = null,
    val replyToId: Long? = null,
    val replyToName: String? = null,
    val replyToTag: String? = null,
    val replyCount: Int = 0,
    val deleted: Boolean = false,
    val pinnedAt: Long? = null,
    val targetBid: String? = null
) {
    fun displayAuthor(): String {
        if (deleted) return "已删除留言"
        val name = authorName?.takeIf { it.isNotBlank() } ?: if (authorBid == "anon") "访客" else authorBid
        return if (authorBid == "anon" && !authorTag.isNullOrBlank()) "$name#$authorTag" else name
    }
}

@Serializable
data class ProfileCommentsResponse(
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 20,
    val comments: List<ProfileComment> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val root: ProfileComment? = null,
    val pinnedComments: List<ProfileComment> = emptyList(),
    val error: String? = null,
    val errorCode: String? = null
)

@Serializable
data class ProfileCommentPostResponse(
    val success: Boolean = false,
    val comment: ProfileComment? = null,
    val error: String? = null,
    val errorCode: String? = null
)

@Serializable
data class UserSession(val bid: String, val wikiUserId: Long)

@Serializable
data class UserSessionResponse(
    val user: UserSession? = null,
    val error: String? = null,
    val errorCode: String? = null
)

@Serializable
data class ProfileLikesResponse(
    val count: Int = 0,
    val likedByMe: Boolean = false,
    val error: String? = null
)

@Serializable
data class ProfileLikeToggleResponse(
    val success: Boolean = false,
    val liked: Boolean = false,
    val count: Int = 0,
    val error: String? = null
)

@Serializable
data class ProfileDeleteResponse(
    val success: Boolean = false,
    val error: String? = null
)
