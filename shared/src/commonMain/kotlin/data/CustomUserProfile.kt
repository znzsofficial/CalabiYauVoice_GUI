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
data class CustomUserProfilesResponse(
    val profiles: Map<String, CustomUserProfile> = emptyMap(),
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
