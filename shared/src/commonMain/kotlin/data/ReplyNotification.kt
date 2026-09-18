package data

import kotlinx.serialization.Serializable

@Serializable
data class ReplyNotification(
    val id: Long,
    val rootId: Long,
    val commentId: Long,
    val createdAt: Long = 0,
    val read: Boolean = false,
    val status: String = "unavailable",
    val content: String? = null,
    val authorBid: String? = null,
    val authorName: String? = null,
    val authorTag: String? = null,
    val authorAvatarUrl: String? = null
) {
    fun displayAuthor(): String = (authorName ?: authorBid ?: "用户") +
        (if (authorBid == "anon" && !authorTag.isNullOrBlank()) "#$authorTag" else "")
}

@Serializable
data class ReplyNotificationsResponse(
    val notifications: List<ReplyNotification> = emptyList(),
    val unreadCount: Int = 0,
    val latestId: Long? = null,
    val nextCursor: String? = null,
    val success: Boolean = false,
    val error: String? = null,
    val errorCode: String? = null
)
