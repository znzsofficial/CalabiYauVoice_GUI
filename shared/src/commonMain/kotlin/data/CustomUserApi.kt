package data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import util.wikiPathEncode
import java.util.concurrent.TimeUnit

/**
 * 客户端请求 Cloudflare 自定义用户映射服务 API。
 */
object CustomUserApi {

    private const val DEFAULT_BASE_URL = "https://wiki.nekolaska.vip"
    var customBaseUrl: String? = null

    private fun baseUrl(): String = (customBaseUrl ?: DEFAULT_BASE_URL).trimEnd('/')

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = SharedJson

    /**
     * 查询单个用户的自定义资料 (支持按 BID 或 WikiID)
     */
    suspend fun fetchProfile(
        bid: String? = null,
        wikiId: Long? = null
    ): ApiResult<CustomUserProfile?> = withContext(Dispatchers.IO) {
        val queryParam = when {
            !bid.isNullOrBlank() -> "bid=${bid.trim().wikiPathEncode()}"
            wikiId != null && wikiId > 0 -> "wiki_id=$wikiId"
            else -> return@withContext ApiResult.Error("缺少查询参数 bid 或 wiki_id", kind = ErrorKind.UNKNOWN)
        }
        val url = "${baseUrl()}/api/user/profile?$queryParam"

        try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errMsg = runCatching { json.decodeFromString<CustomUserProfileResponse>(body).error }.getOrNull()
                    return@withContext ApiResult.Error(errMsg ?: "请求失败 (${resp.code})", kind = ErrorKind.NETWORK)
                }
                val parsed = json.decodeFromString<CustomUserProfileResponse>(body)
                if (parsed.error != null) {
                    return@withContext ApiResult.Error(parsed.error, kind = ErrorKind.UNKNOWN)
                }
                ApiResult.Success(parsed.profile)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "获取用户档案失败", kind = e.toErrorKind())
        }
    }

    /**
     * 批量查询用户自定义资料映射表 (Key 为 BID)
     */
    suspend fun fetchProfiles(
        bids: List<String> = emptyList(),
        wikiIds: List<Long> = emptyList()
    ): ApiResult<Map<String, CustomUserProfile>> = withContext(Dispatchers.IO) {
        if (bids.isEmpty() && wikiIds.isEmpty()) {
            return@withContext ApiResult.Success(emptyMap())
        }
        val url = "${baseUrl()}/api/user/profiles"
        val requestJson = buildJsonObject {
            if (bids.isNotEmpty()) {
                put("bids", kotlinx.serialization.json.JsonArray(bids.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            }
            if (wikiIds.isNotEmpty()) {
                put("wiki_ids", kotlinx.serialization.json.JsonArray(wikiIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            }
        }.toString()

        try {
            val req = Request.Builder()
                .url(url)
                .post(requestJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errMsg = runCatching { json.decodeFromString<CustomUserProfilesResponse>(body).error }.getOrNull()
                    return@withContext ApiResult.Error(errMsg ?: "批量查询失败 (${resp.code})", kind = ErrorKind.NETWORK)
                }
                val parsed = json.decodeFromString<CustomUserProfilesResponse>(body)
                if (parsed.error != null) {
                    return@withContext ApiResult.Error(parsed.error, kind = ErrorKind.UNKNOWN)
                }
                ApiResult.Success(parsed.profiles)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "批量获取用户档案失败", kind = e.toErrorKind())
        }
    }

    /**
     * 上传用户自定义头像到 R2 存储桶。
     * @param imageBytes 图片二进制数据
     * @param mimeType 图片 MIME 类型 (image/png, image/jpeg, image/webp)
     * @param wikiCookie 用户已登录的 Wiki Cookie 字符串（用于权限校验）
     * @return 上传成功后的 avatarUrl (如 "/api/user/avatar/avatars/123456_xxx.png")
     */
    suspend fun uploadAvatar(
        imageBytes: ByteArray,
        mimeType: String = "image/png",
        wikiCookie: String
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        if (wikiCookie.isBlank()) {
            return@withContext ApiResult.Error("未登录 Wiki，无法上传头像", kind = ErrorKind.UNKNOWN)
        }
        if (imageBytes.isEmpty()) {
            return@withContext ApiResult.Error("图片内容为空", kind = ErrorKind.UNKNOWN)
        }
        val url = "${baseUrl()}/api/user/avatar"

        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "avatar.${mimeType.substringAfterLast('/')}",
                    imageBytes.toRequestBody(mimeType.toMediaType())
                )
                .build()

            val req = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("X-Wiki-Cookie", wikiCookie)
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errMsg = runCatching { json.decodeFromString<CustomUserAvatarUploadResponse>(body).error }.getOrNull()
                    return@withContext ApiResult.Error(errMsg ?: "头像上传失败 (${resp.code})", kind = ErrorKind.NETWORK)
                }
                val parsed = json.decodeFromString<CustomUserAvatarUploadResponse>(body)
                if (!parsed.avatarUrl.isNullOrBlank()) {
                    val fullAvatarUrl = if (parsed.avatarUrl.startsWith("http")) parsed.avatarUrl else "${baseUrl()}${parsed.avatarUrl}"
                    ApiResult.Success(fullAvatarUrl)
                } else {
                    ApiResult.Error(parsed.error ?: "未获取到头像 URL", kind = ErrorKind.UNKNOWN)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "上传头像网络异常", kind = e.toErrorKind())
        }
    }

    /**
     * 更新当前用户的自定义资料（D1 表保存）。
     * @param customName 自定义昵称 (可选)
     * @param avatarUrl 自定义头像 URL (可选)
     * @param bio 个人签名简介 (可选)
     * @param badge 自定义头衔/徽章 (可选)
     * @param wikiCookie 用户已登录的 Wiki Cookie 字符串
     */
    suspend fun updateProfile(
        customName: String? = null,
        avatarUrl: String? = null,
        bio: String? = null,
        badge: String? = null,
        wikiCookie: String
    ): ApiResult<CustomUserProfile> = withContext(Dispatchers.IO) {
        if (wikiCookie.isBlank()) {
            return@withContext ApiResult.Error("未登录 Wiki，无法保存资料", kind = ErrorKind.UNKNOWN)
        }
        val url = "${baseUrl()}/api/user/profile"

        val payload = buildJsonObject {
            if (customName != null) put("customName", customName)
            if (avatarUrl != null) put("avatarUrl", avatarUrl)
            if (bio != null) put("bio", bio)
            if (badge != null) put("badge", badge)
        }.toString()

        try {
            val req = Request.Builder()
                .url(url)
                .put(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("X-Wiki-Cookie", wikiCookie)
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errMsg = runCatching { json.decodeFromString<CustomUserUpdateResponse>(body).error }.getOrNull()
                    return@withContext ApiResult.Error(errMsg ?: "保存失败 (${resp.code})", kind = ErrorKind.NETWORK)
                }
                val parsed = json.decodeFromString<CustomUserUpdateResponse>(body)
                if (parsed.success && parsed.profile != null) {
                    ApiResult.Success(parsed.profile)
                } else {
                    ApiResult.Error(parsed.error ?: "更新失败", kind = ErrorKind.UNKNOWN)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "保存资料网络异常", kind = e.toErrorKind())
        }
    }
}
