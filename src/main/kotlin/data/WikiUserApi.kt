package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

/**
 * MediaWiki API 用户信息查询。
 * 所有请求都使用 [WikiEngine.client]，因此自动携带通过 [WikiCookieManager] 注入的 Cookie。
 */
object WikiUserApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    // ───── 序列化模型 ─────────────────────────────────────────────────

    @Serializable
    data class UserInfoResponse(
        val query: UserInfoQuery? = null
    )

    @Serializable
    data class UserInfoQuery(
        val userinfo: UserInfo? = null
    )

    /** MediaWiki userinfo 字段 */
    @Serializable
    data class UserInfo(
        val id: Int = 0,
        val name: String = "",
        val groups: List<String> = emptyList(),
        @SerialName("implicitgroups") val implicitGroups: List<String> = emptyList(),
        val rights: List<String> = emptyList(),
        @SerialName("editcount") val editCount: Int = 0,
        @SerialName("registrationdate") val registrationDate: String = "",
        val email: String = "",
        @SerialName("realname") val realName: String = "",
        @SerialName("anon") val anon: JsonElement? = null
    ) {
        val isAnon: Boolean get() = anon != null
        val isLoggedIn: Boolean get() = id != 0 && !isAnon
    }

    // ───── 公开用户信息（任意用户）────────────────────────────────

    @Serializable
    data class PublicUsersResponse(
        val query: PublicUsersQuery? = null
    )

    @Serializable
    data class PublicUsersQuery(
        val users: List<PublicUserInfo>? = null
    )

    /**
     * MediaWiki 对不存在的用户返回 `"missing":""` 或 `"invalid":""`（值为空字符串，不是 boolean）。
     * 用 [JsonElement] 存储，再由 [exists] 推断。
     */
    @Serializable
    data class PublicUserInfo(
        val userid: Int = 0,
        val name: String = "",
        val groups: List<String> = emptyList(),
        @SerialName("editcount") val editCount: Int = 0,
        @SerialName("registration") val registrationDate: String = "",
        // MediaWiki 给缺失/无效用户发送空字符串值的这两个 key
        val missing: JsonElement? = null,
        val invalid: JsonElement? = null
    ) {
        val exists: Boolean get() = missing == null && invalid == null && userid != 0
    }

    // ───── 文件列表（任意用户上传的文件）────────────────────────────

    @Serializable
    data class UserFilesResponse(
        val query: UserFilesQuery? = null,
        @SerialName("continue") val continuation: Map<String, JsonElement>? = null
    )

    @Serializable
    data class UserFilesQuery(
        val allimages: List<UserFile>? = null
    )

    @Serializable
    data class UserFile(
        val name: String = "",
        val title: String = "",
        val timestamp: String = "",
        val url: String = "",
        val size: Long = 0,
        val mime: String = ""
    )


    // ───── 封禁状态 ──────────────────────────────────────────────────

    @Serializable
    data class BlocksResponse(
        val query: BlocksQuery? = null
    )

    @Serializable
    data class BlocksQuery(
        val blocks: List<BlockInfo>? = null
    )

    @Serializable
    data class BlockInfo(
        val id: Long = 0,
        val user: String = "",
        val by: String = "",
        val timestamp: String = "",
        val expiry: String = "",
        val reason: String = "",
        // MediaWiki 将这些标志位发送为 "" 而非 true
        @SerialName("anononly") val anonOnly: JsonElement? = null,
        @SerialName("nocreate") val noCreate: JsonElement? = null,
        @SerialName("autoblock") val autoBlock: JsonElement? = null,
        @SerialName("noemail") val noEmail: JsonElement? = null,
        @SerialName("nousertalk") val noUserTalk: JsonElement? = null
    )

    // ───── 用户贡献 ──────────────────────────────────────────────────

    @Serializable
    data class ContributionsResponse(
        val query: ContribQuery? = null,
        @SerialName("continue") val continuation: Map<String, JsonElement>? = null
    )

    @Serializable
    data class ContribQuery(
        val usercontribs: List<UserContrib>? = null
    )

    @Serializable
    data class UserContrib(
        @SerialName("revid") val revId: Long = 0,
        @SerialName("pageid") val pageId: Long = 0,
        val title: String = "",
        val timestamp: String = "",
        val comment: String = "",
        val size: Int = 0,
        @SerialName("sizediff") val sizeDiff: Int = 0,
        // MediaWiki 将这些标志位发送为 "" 而非 true，使用 JsonElement? 避免反序列化失败
        val minor: JsonElement? = null,
        val top: JsonElement? = null
    ) {
        val isMinor: Boolean get() = minor != null
        val isTop: Boolean get() = top != null
    }

    // ───── 监视列表 ──────────────────────────────────────────────────

    @Serializable
    data class WatchlistResponse(
        val query: WatchlistQuery? = null,
        @SerialName("continue") val continuation: Map<String, JsonElement>? = null
    )

    @Serializable
    data class WatchlistQuery(
        val watchlist: List<WatchlistItem>? = null
    )

    @Serializable
    data class WatchlistItem(
        val title: String = "",
        val timestamp: String = "",
        val type: String = "",
        val comment: String = "",
        val user: String = "",
        @SerialName("revid") val revId: Long = 0,
        @SerialName("pageid") val pageId: Long = 0,
        val minor: JsonElement? = null,
        val anon: JsonElement? = null
    ) {
        val isMinor: Boolean get() = minor != null
        val isAnon: Boolean get() = anon != null
    }

    // ───── 操作日志 ──────────────────────────────────────────────────

    @Serializable
    data class LogEventsResponse(
        val query: LogEventsQuery? = null,
        @SerialName("continue") val continuation: Map<String, JsonElement>? = null
    )

    @Serializable
    data class LogEventsQuery(
        val logevents: List<LogEvent>? = null
    )

    @Serializable
    data class LogEvent(
        @SerialName("logid") val logId: Long = 0,
        val title: String = "",
        val type: String = "",          // "upload" | "delete" | "protect" | "block" | ...
        val action: String = "",
        val user: String = "",
        val timestamp: String = "",
        val comment: String = ""
    )

    // ───── JSON 解析器 ───────────────────────────────────────────────

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // ───── API 方法 ──────────────────────────────────────────────────

    /**
     * 获取当前已登录用户的详细信息（需要有效 Cookie）。
     */
    suspend fun fetchCurrentUserInfo(): UserInfo? = withContext(Dispatchers.IO) {
        val url = buildUrl(
            "action" to "query", "meta" to "userinfo",
            "uiprop" to "groups|rights|editcount|registrationdate|email|realname",
            "format" to "json"
        )
        val resp = fetchString(url) ?: return@withContext null
        runCatching { json.decodeFromString<UserInfoResponse>(resp).query?.userinfo }.getOrNull()
    }

    /**
     * 获取指定用户的最近编辑贡献（最多 [limit] 条）。
     */
    suspend fun fetchContributions(userName: String, limit: Int = 50): List<UserContrib> = withContext(Dispatchers.IO) {
        val url = buildUrl(
            "action" to "query", "list" to "usercontribs", "ucuser" to userName,
            "ucprop" to "ids|title|timestamp|comment|size|sizediff|flags",
            "uclimit" to "$limit", "format" to "json"
        )
        val resp = fetchString(url) ?: return@withContext emptyList()
        runCatching { json.decodeFromString<ContributionsResponse>(resp).query?.usercontribs ?: emptyList() }
            .getOrElse { emptyList() }
    }

    /**
     * 获取当前用户的监视列表最近变更（需要已登录 Cookie）。
     */
    suspend fun fetchWatchlist(limit: Int = 50): List<WatchlistItem> = withContext(Dispatchers.IO) {
        val url = buildUrl(
            "action" to "query", "list" to "watchlist",
            "wlprop" to "ids|title|type|user|comment|timestamp|flags",
            "wllimit" to "$limit", "format" to "json"
        )
        val resp = fetchString(url) ?: return@withContext emptyList()
        runCatching { json.decodeFromString<WatchlistResponse>(resp).query?.watchlist ?: emptyList() }
            .getOrElse { emptyList() }
    }

    /**
     * 获取指定用户的操作日志（上传/删除/保护/封禁等）。
     */
    suspend fun fetchUserLogEvents(userName: String, limit: Int = 50): List<LogEvent> = withContext(Dispatchers.IO) {
        val url = buildUrl(
            "action" to "query", "list" to "logevents", "leuser" to userName,
            "leprop" to "ids|title|type|user|comment|timestamp",
            "lelimit" to "$limit", "format" to "json"
        )
        val resp = fetchString(url) ?: return@withContext emptyList()
        runCatching { json.decodeFromString<LogEventsResponse>(resp).query?.logevents ?: emptyList() }
            .getOrElse { emptyList() }
    }

    /**
     * 通过用户 ID（即该 Wiki 上以数字为用户名的账号）查询用户的公开信息。
     * 该 Wiki 的用户名本身就是数字字符串，直接用 ususers= 查询。
     */
    suspend fun fetchPublicUserInfo(userId: String): PublicUserInfo? = withContext(Dispatchers.IO) {
        val id = userId.trim().trimStart('#')
        if (id.isBlank()) return@withContext null
        val url = buildUrl(
            "action" to "query",
            "list" to "users",
            "ususers" to id,
            "usprop" to "groups|editcount|registration",
            "format" to "json"
        )
        val resp = fetchString(url) ?: return@withContext null
        runCatching {
            json.decodeFromString<PublicUsersResponse>(resp).query?.users?.firstOrNull()
        }.getOrNull()
    }

    /**
     * 获取指定用户上传的文件列表（通过 list=allimages 按上传者筛选）。
     */
    suspend fun fetchUserFiles(userName: String, limit: Int = 50): List<UserFile> = withContext(Dispatchers.IO) {
        val url = buildUrl(
            "action" to "query", "list" to "allimages", "aiuser" to userName,
            "aiprop" to "timestamp|url|size|mime",
            "ailimit" to "$limit", "aisort" to "timestamp", "aidir" to "descending",
            "format" to "json"
        )
        val resp = fetchString(url) ?: return@withContext emptyList()
        runCatching { json.decodeFromString<UserFilesResponse>(resp).query?.allimages ?: emptyList() }
            .getOrElse { emptyList() }
    }



    /**
     * 查询指定用户名的封禁状态，未被封禁时返回 null。
     */
    suspend fun fetchBlockStatus(userName: String): BlockInfo? = withContext(Dispatchers.IO) {
        val url = buildUrl(
            "action" to "query", "list" to "blocks", "bkusers" to userName,
            "bkprop" to "id|user|by|timestamp|expiry|reason|flags",
            "format" to "json"
        )
        val resp = fetchString(url) ?: return@withContext null
        runCatching { json.decodeFromString<BlocksResponse>(resp).query?.blocks?.firstOrNull() }.getOrNull()
    }

    /**
     * 获取指定用户最近一次编辑的时间戳，无编辑记录时返回 null。
     */
    suspend fun fetchLastEditTimestamp(userName: String): String? = withContext(Dispatchers.IO) {
        val url = buildUrl(
            "action" to "query", "list" to "usercontribs", "ucuser" to userName,
            "ucprop" to "timestamp", "uclimit" to "1", "format" to "json"
        )
        val resp = fetchString(url) ?: return@withContext null
        runCatching {
            json.decodeFromString<ContributionsResponse>(resp).query?.usercontribs?.firstOrNull()?.timestamp
        }.getOrNull()
    }

    // ───── 内部工具 ──────────────────────────────────────────────────

    /**
     * 使用 [okhttp3.HttpUrl.Builder] 构造 API 请求，确保所有参数正确编码（包括 `|` 分隔符）。
     * params 中的 vararg 格式为 "key" to "value"。
     */
    private fun buildUrl(vararg params: Pair<String, String>): String {
        val builder = API.toHttpUrlOrNull()?.newBuilder() ?: return ""
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    private fun fetchString(url: String): String? {
        return runCatching {
            WikiEngine.client.newCall(Request.Builder().url(url).build()).execute()
                .use { resp ->
                    val body = resp.body.string()
                    if (body.trimStart().startsWith("<") || !resp.isSuccessful) null else body
                }
        }.getOrNull()
    }

    /** ISO 8601 时间戳转本地可读格式 */
    fun formatTimestamp(ts: String): String {
        if (ts.isBlank()) return "-"
        return try {
            val inst = java.time.Instant.parse(ts)
            val zdt = inst.atZone(java.time.ZoneId.systemDefault())
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(zdt)
        } catch (_: Exception) {
            ts.take(16).replace("T", " ")
        }
    }

    /** 操作日志类型中文映射 */
    fun logTypeLabel(type: String, action: String): String = when (type) {
        "upload" -> when (action) { "upload" -> "上传"; "overwrite" -> "覆盖上传"; "revert" -> "回退上传"; else -> "上传" }
        "delete" -> when (action) { "delete" -> "删除"; "restore" -> "恢复"; "revision" -> "删除版本"; else -> "删除" }
        "protect" -> when (action) { "protect" -> "保护"; "unprotect" -> "取消保护"; "modify" -> "修改保护"; else -> "保护" }
        "block" -> when (action) { "block" -> "封禁"; "unblock" -> "解封"; "reblock" -> "修改封禁"; else -> "封禁" }
        "rights" -> "权限变更"
        "move" -> when (action) { "move" -> "移动"; "move_redir" -> "移动重定向"; else -> "移动" }
        "newusers" -> "新建用户"
        "patrol" -> "标记巡查"
        "review" -> "审核"
        "stable" -> "稳定版本"
        else -> type
    }

    /** 监视列表变更类型中文映射 */
    fun watchTypeLabel(type: String): String = when (type) {
        "edit" -> "编辑"
        "new" -> "新建"
        "log" -> "日志"
        "categorize" -> "分类"
        else -> type
    }
}

