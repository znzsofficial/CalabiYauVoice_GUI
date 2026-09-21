package data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
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

    private fun baseUrl(): String = DEFAULT_BASE_URL.trimEnd('/')

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = SharedJson

    // 同一 Cookie 的 session 结果短期复用：留言板等页面每次进入都会确认身份，
    // 60 秒内免一次 Worker→SMW 两跳请求。账号切换由 Cookie 变化自然失效。
    private var sessionCache: Pair<String, Pair<TimeSource.Monotonic.ValueTimeMark, UserSession>>? = null

    suspend fun fetchSession(wikiCookie: String): ApiResult<UserSession> = withContext(Dispatchers.IO) {
        sessionCache?.let { (ck, cached) ->
            val (mark, session) = cached
            if (ck == wikiCookie && mark.elapsedNow() < 60.seconds) {
                return@withContext ApiResult.Success(session)
            }
        }
        try {
            val request = Request.Builder().url("${baseUrl()}/api/user/session")
                .header("X-Wiki-Cookie", wikiCookie).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                nonJsonApiMessage(body)?.let { return@withContext ApiResult.Error(it, ErrorKind.NETWORK) }
                val parsed = json.decodeFromString<UserSessionResponse>(body)
                if (!response.isSuccessful || parsed.user == null) {
                    ApiResult.Error(parsed.error ?: "身份验证失败 (${response.code})", ErrorKind.NETWORK,
                        response.code, parsed.errorCode)
                } else ApiResult.Success(parsed.user).also {
                    sessionCache = wikiCookie to (TimeSource.Monotonic.markNow() to parsed.user)
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { ApiResult.Error("身份验证失败，请稍后重试", e.toErrorKind()) }
    }

    suspend fun fetchNotifications(wikiCookie: String, before: String? = null): ApiResult<ReplyNotificationsResponse> =
        notificationRequest(wikiCookie, before, null, null)

    suspend fun readNotifications(wikiCookie: String, id: Long? = null, throughId: Long? = null): ApiResult<ReplyNotificationsResponse> =
        notificationRequest(wikiCookie, null, id, throughId)

    private suspend fun notificationRequest(
        cookie: String, before: String?, id: Long?, throughId: Long?
    ): ApiResult<ReplyNotificationsResponse> = withContext(Dispatchers.IO) {
        if (cookie.isBlank()) return@withContext ApiResult.Error("请先登录 Wiki", kind = ErrorKind.UNKNOWN)
        try {
            val builder = Request.Builder().url("${baseUrl()}/api/user/notifications" +
                (before?.let { "?before=${it.wikiPathEncode()}" } ?: "")).header("X-Wiki-Cookie", cookie)
            if (id != null || throughId != null) {
                val payload = buildJsonObject {
                    if (id != null) put("id", id) else put("throughId", throughId!!)
                }.toString()
                builder.put(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            }
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body.string()
                nonJsonApiMessage(body)?.let { return@withContext ApiResult.Error(it, kind = ErrorKind.NETWORK) }
                val result = json.decodeFromString<ReplyNotificationsResponse>(body)
                if (!response.isSuccessful || result.error != null) ApiResult.Error(result.error ?: "通知请求失败 (${response.code})", kind = ErrorKind.NETWORK, httpStatus = response.code, apiCode = result.errorCode)
                else ApiResult.Success(result)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { ApiResult.Error("通知请求失败，请稍后重试", kind = e.toErrorKind()) }
    }

    /**
     * 查询单个用户的自定义资料。同一用户 60 秒内复用内存结果
     * （资料弹窗/留言板头像高频重复打开）。
     */
    private val profileCache = java.util.concurrent.ConcurrentHashMap<String, Pair<TimeSource.Monotonic.ValueTimeMark, CustomUserProfile?>>()

    suspend fun fetchProfile(
        bid: String? = null,
        wikiId: Long? = null
    ): ApiResult<CustomUserProfile?> = withContext(Dispatchers.IO) {
        val queryParam = when {
            !bid.isNullOrBlank() -> "bid=${bid.trim().wikiPathEncode()}"
            wikiId != null && wikiId > 0 -> "wiki_id=$wikiId"
            else -> return@withContext ApiResult.Error("缺少查询参数 bid 或 wiki_id", kind = ErrorKind.UNKNOWN)
        }
        val cacheKey = bid?.trim() ?: "wiki_id=$wikiId"
        profileCache[cacheKey]?.let { (mark, profile) ->
            if (mark.elapsedNow() < 60.seconds) return@withContext ApiResult.Success(profile)
        }
        val url = "${baseUrl()}/api/user/profile?$queryParam"

        try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body.string()
                nonJsonApiMessage(body)?.let { message ->
                    return@withContext ApiResult.Error(message, kind = ErrorKind.NETWORK)
                }
                if (!resp.isSuccessful) {
                    val errMsg = runCatching { json.decodeFromString<CustomUserProfileResponse>(body).error }.getOrNull()
                    return@withContext ApiResult.Error(errMsg ?: "请求失败 (${resp.code})", kind = ErrorKind.NETWORK)
                }
                val parsed = json.decodeFromString<CustomUserProfileResponse>(body)
                if (parsed.error != null) {
                    return@withContext ApiResult.Error(parsed.error, kind = ErrorKind.UNKNOWN)
                }
                ApiResult.Success(parsed.profile).also {
                    parsed.profile?.let { profile ->
                        profileCache[cacheKey] = TimeSource.Monotonic.markNow() to profile
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "获取用户档案失败", kind = e.toErrorKind())
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
                val body = resp.body.string()
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
                val body = resp.body.string()
                if (!resp.isSuccessful) {
                    val errMsg = runCatching { json.decodeFromString<CustomUserUpdateResponse>(body).error }.getOrNull()
                    return@withContext ApiResult.Error(errMsg ?: "保存失败 (${resp.code})", kind = ErrorKind.NETWORK)
                }
                val parsed = json.decodeFromString<CustomUserUpdateResponse>(body)
                if (parsed.success && parsed.profile != null) {
                    // 资料已变更：失效读取缓存，避免弹窗重开显示旧资料
                    profileCache.clear()
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

    // ───── 留言板与点赞 ──────────────────────────────────────────────

    /**
     * 拉取指定用户的留言列表（按时间倒序）。
     */
    suspend fun fetchComments(
        bid: String,
        page: Int = 1,
        size: Int = 20,
        before: String? = null,
        rootId: Long? = null,
        focusId: Long? = null
    ): ApiResult<ProfileCommentsResponse> = withContext(Dispatchers.IO) {
        val path = if (rootId == null) "comments?bid=${bid.trim().wikiPathEncode()}&page=$page" else "replies?rootId=$rootId"
        val url = "${baseUrl()}/api/user/$path&size=$size" +
            (before?.let { "&before=${it.wikiPathEncode()}" } ?: "") +
            (focusId?.let { "&focusId=$it" } ?: "")
        try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body.string()
                nonJsonApiMessage(body, replies = rootId != null)?.let { message ->
                    return@withContext ApiResult.Error(message, kind = ErrorKind.NETWORK)
                }
                if (!resp.isSuccessful) {
                    val errMsg = runCatching { json.decodeFromString<ProfileCommentsResponse>(body).error }.getOrNull()
                    val parsed = runCatching { json.decodeFromString<ProfileCommentsResponse>(body) }.getOrNull()
                    return@withContext ApiResult.Error(parsed?.error ?: errMsg ?: "获取留言失败 (${resp.code})", kind = ErrorKind.NETWORK, httpStatus = resp.code, apiCode = parsed?.errorCode)
                }
                val parsed = json.decodeFromString<ProfileCommentsResponse>(body)
                if (parsed.error != null) {
                    return@withContext ApiResult.Error(parsed.error, kind = ErrorKind.UNKNOWN)
                }
                ApiResult.Success(parsed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "获取留言网络异常", kind = e.toErrorKind())
        }
    }

    /**
     * 发表留言。登录用户自动带身份署名（10 秒限流）；
     * 未登录（Cookie 为空）以访客身份匿名发言，可提供昵称（30 秒 IP 限流）。
     * 内容上限 200 字。
     */
    suspend fun postComment(
        targetBid: String,
        content: String,
        wikiCookie: String? = null,
        authorName: String? = null,
        requestId: String,
        guestId: String? = null,
        replyToId: Long? = null
    ): ApiResult<ProfileComment> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("targetBid", targetBid)
            put("content", content)
            if (replyToId != null) put("replyToId", replyToId)
            if (authorName != null && authorName.isNotBlank()) put("authorName", authorName.trim())
        }.toString()
        val url = "${baseUrl()}/api/user/${if (replyToId == null) "comments" else "replies"}"

        try {
            val req = Request.Builder()
                .url(url)
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Idempotency-Key", requestId)
                .apply { if (!guestId.isNullOrBlank()) header("X-Guest-Id", guestId) }
                .apply { if (!wikiCookie.isNullOrBlank()) header("X-Wiki-Cookie", wikiCookie) }
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body.string()
                nonJsonApiMessage(body, replies = replyToId != null)?.let { message ->
                    return@withContext ApiResult.Error(message, kind = ErrorKind.NETWORK)
                }
                if (!resp.isSuccessful) {
                    val errMsg = runCatching { json.decodeFromString<ProfileCommentPostResponse>(body).error }.getOrNull()
                    val parsed = runCatching { json.decodeFromString<ProfileCommentPostResponse>(body) }.getOrNull()
                    return@withContext ApiResult.Error(parsed?.error ?: errMsg ?: "发表留言失败 (${resp.code})", kind = ErrorKind.NETWORK, httpStatus = resp.code, apiCode = parsed?.errorCode)
                }
                val parsed = json.decodeFromString<ProfileCommentPostResponse>(body)
                if (parsed.success && parsed.comment != null) {
                    ApiResult.Success(parsed.comment)
                } else {
                    ApiResult.Error(parsed.error ?: "发表留言失败", kind = ErrorKind.UNKNOWN)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "发表留言网络异常", kind = e.toErrorKind())
        }
    }

    /**
     * 删除自己的留言（需 Wiki 登录 Cookie，仅作者本人可删）。
     */
    suspend fun deleteComment(
        commentId: Long,
        wikiCookie: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (wikiCookie.isBlank()) {
            return@withContext ApiResult.Error("未登录 Wiki，无法删除留言", kind = ErrorKind.UNKNOWN)
        }
        val url = "${baseUrl()}/api/user/comments?id=$commentId"
        try {
            val req = Request.Builder()
                .url(url)
                .delete()
                .header("X-Wiki-Cookie", wikiCookie)
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body.string()
                if (!resp.isSuccessful) {
                    val errMsg = runCatching { json.decodeFromString<ProfileDeleteResponse>(body).error }.getOrNull()
                    return@withContext ApiResult.Error(errMsg ?: "删除留言失败 (${resp.code})", kind = ErrorKind.NETWORK)
                }
                ApiResult.Success(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "删除留言网络异常", kind = e.toErrorKind())
        }
    }

    /**
     * 查询点赞数；传入 Cookie 时附带当前用户是否已赞。
     */
    suspend fun fetchLikes(
        bid: String,
        wikiCookie: String? = null
    ): ApiResult<ProfileLikesResponse> = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/user/likes?bid=${bid.trim().wikiPathEncode()}"
        try {
            val req = Request.Builder().url(url).get().apply {
                if (!wikiCookie.isNullOrBlank()) header("X-Wiki-Cookie", wikiCookie)
            }.build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body.string()
                if (!resp.isSuccessful) {
                    val errMsg = runCatching { json.decodeFromString<ProfileLikesResponse>(body).error }.getOrNull()
                    return@withContext ApiResult.Error(errMsg ?: "获取点赞失败 (${resp.code})", kind = ErrorKind.NETWORK)
                }
                val parsed = json.decodeFromString<ProfileLikesResponse>(body)
                if (parsed.error != null) {
                    return@withContext ApiResult.Error(parsed.error, kind = ErrorKind.UNKNOWN)
                }
                ApiResult.Success(parsed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "获取点赞网络异常", kind = e.toErrorKind())
        }
    }

    /**
     * 设置点赞状态；重试相同请求不会反转状态。
     */
    suspend fun setLike(
        targetBid: String,
        liked: Boolean,
        wikiCookie: String
    ): ApiResult<ProfileLikeToggleResponse> = withContext(Dispatchers.IO) {
        if (wikiCookie.isBlank()) {
            return@withContext ApiResult.Error("未登录 Wiki，无法点赞", kind = ErrorKind.UNKNOWN)
        }
        val payload = buildJsonObject { put("targetBid", targetBid) }.toString()
        val url = "${baseUrl()}/api/user/likes"

        try {
            val req = Request.Builder()
                .url(url)
                .method(if (liked) "PUT" else "DELETE", payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("X-Wiki-Cookie", wikiCookie)
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body.string()
                if (!resp.isSuccessful) {
                    val errMsg = runCatching { json.decodeFromString<ProfileLikeToggleResponse>(body).error }.getOrNull()
                    return@withContext ApiResult.Error(errMsg ?: "点赞操作失败 (${resp.code})", kind = ErrorKind.NETWORK)
                }
                val parsed = json.decodeFromString<ProfileLikeToggleResponse>(body)
                if (parsed.success) {
                    ApiResult.Success(parsed)
                } else {
                    ApiResult.Error(parsed.error ?: "点赞操作失败", kind = ErrorKind.UNKNOWN)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "点赞网络异常", kind = e.toErrorKind())
        }
    }
}
