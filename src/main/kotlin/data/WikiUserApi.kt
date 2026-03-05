package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Request
import java.net.URLEncoder

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
        @SerialName("anon") val isAnon: Boolean = false
    ) {
        val isLoggedIn: Boolean get() = id != 0 && !isAnon
        /** 显示名：优先真实姓名，否则用户名 */
        val displayName: String get() = realName.takeIf { it.isNotBlank() } ?: name
    }

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
        val minor: Boolean = false,
        val top: Boolean = false
    )

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
        val type: String = "",          // "edit" | "new" | "log" | "categorize"
        val comment: String = "",
        val user: String = "",
        @SerialName("revid") val revId: Long = 0,
        @SerialName("pageid") val pageId: Long = 0,
        val minor: Boolean = false,
        @SerialName("anon") val isAnon: Boolean = false
    )

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
     * 返回 null 表示请求失败；[UserInfo.isLoggedIn] == false 表示未登录。
     */
    suspend fun fetchCurrentUserInfo(): UserInfo? = withContext(Dispatchers.IO) {
        val url = "$API?action=query&meta=userinfo" +
                "&uiprop=groups|rights|editcount|registrationdate|email|realname&format=json"
        val resp = fetchString(url) ?: return@withContext null
        if (resp.trimStart().startsWith("<")) return@withContext null
        runCatching {
            json.decodeFromString<UserInfoResponse>(resp).query?.userinfo
        }.getOrNull()
    }

    /**
     * 获取当前用户的最近编辑贡献（最多 [limit] 条，默认 50）。
     */
    suspend fun fetchContributions(
        userName: String,
        limit: Int = 50
    ): List<UserContrib> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(userName, "UTF-8")
        val url = "$API?action=query&list=usercontribs&ucuser=$encoded" +
                "&ucprop=ids|title|timestamp|comment|size|sizediff|flags" +
                "&uclimit=$limit&format=json"
        val resp = fetchString(url) ?: return@withContext emptyList()
        if (resp.trimStart().startsWith("<")) return@withContext emptyList()
        runCatching {
            json.decodeFromString<ContributionsResponse>(resp).query?.usercontribs ?: emptyList()
        }.getOrElse { emptyList() }
    }

    /**
     * 获取当前用户的监视列表最近变更（需要已登录 Cookie，最多 [limit] 条）。
     */
    suspend fun fetchWatchlist(limit: Int = 50): List<WatchlistItem> = withContext(Dispatchers.IO) {
        val url = "$API?action=query&list=watchlist" +
                "&wlprop=ids|title|type|user|comment|timestamp|flags" +
                "&wllimit=$limit&format=json"
        val resp = fetchString(url) ?: return@withContext emptyList()
        if (resp.trimStart().startsWith("<")) return@withContext emptyList()
        runCatching {
            json.decodeFromString<WatchlistResponse>(resp).query?.watchlist ?: emptyList()
        }.getOrElse { emptyList() }
    }

    /**
     * 获取当前用户的操作日志（上传/删除/保护/封禁等，最多 [limit] 条）。
     */
    suspend fun fetchUserLogEvents(userName: String, limit: Int = 50): List<LogEvent> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(userName, "UTF-8")
        val url = "$API?action=query&list=logevents&leuser=$encoded" +
                "&leprop=ids|title|type|user|comment|timestamp" +
                "&lelimit=$limit&format=json"
        val resp = fetchString(url) ?: return@withContext emptyList()
        if (resp.trimStart().startsWith("<")) return@withContext emptyList()
        runCatching {
            json.decodeFromString<LogEventsResponse>(resp).query?.logevents ?: emptyList()
        }.getOrElse { emptyList() }
    }

    // ───── 内部工具 ──────────────────────────────────────────────────

    private fun fetchString(url: String): String? {
        return runCatching {
            WikiEngine.client.newCall(Request.Builder().url(url).build()).execute()
                .use { if (it.isSuccessful) it.body.string() else null }
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

