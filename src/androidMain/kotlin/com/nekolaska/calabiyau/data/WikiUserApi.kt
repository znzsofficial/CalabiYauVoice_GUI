package com.nekolaska.calabiyau.data

import android.webkit.CookieManager
import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.Request

/**
 * Android 端 MediaWiki 用户信息 API。
 * 通过 WebView CookieManager 获取登录 Cookie 并注入到 OkHttp 请求中，
 * 调用 MediaWiki API 获取当前登录用户的详细信息。
 */
object WikiUserApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    // ───── 数据模型 ──────────────────────────────────────────────────

    @Serializable
    data class UserInfoResponse(val query: UserInfoQuery? = null)

    @Serializable
    data class UserInfoQuery(val userinfo: UserInfo? = null)

    @Serializable
    data class UserInfo(
        val id: Long = 0,
        val name: String = "",
        val groups: List<String> = emptyList(),
        @SerialName("implicitgroups") val implicitGroups: List<String> = emptyList(),
        val rights: List<String> = emptyList(),
        @SerialName("editcount") val editCount: Int = 0,
        @SerialName("registrationdate") val registrationDate: String = "",
        val email: String = "",
        @SerialName("realname") val realName: String = "",
        val anon: kotlinx.serialization.json.JsonElement? = null
    ) {
        /** 匿名用户时 anon 字段会出现 */
        val isAnon: Boolean get() = anon != null
        val isLoggedIn: Boolean get() = !isAnon && id > 0

        /** 获取用户可读的权限组名称（排除隐式组 * 和 user） */
        val displayGroups: List<String>
            get() = groups.filter { it != "*" && it != "user" }
    }

    sealed interface ApiResult<out T> {
        data class Success<T>(val value: T) : ApiResult<T>
        data class Error(val message: String) : ApiResult<Nothing>
    }

    // ───── 用户贡献数据模型 ───────────────────────────────────────

    @Serializable
    data class ContributionsResponse(
        val query: ContribQuery? = null
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
        val minor: JsonElement? = null,
        val top: JsonElement? = null,
        @SerialName("new") val isNew: JsonElement? = null
    ) {
        val isMinor: Boolean get() = minor != null
        val isNewPage: Boolean get() = isNew != null
    }

    // ───── 监视列表数据模型 ─────────────────────────────────────────

    @Serializable
    data class WatchlistResponse(
        val query: WatchlistQuery? = null
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
        @SerialName("old_revid") val oldRevId: Long = 0,
        val minor: JsonElement? = null,
        val anon: JsonElement? = null
    ) {
        val isMinor: Boolean get() = minor != null
        val isAnon: Boolean get() = anon != null
    }

    // ───── JSON 解析器 ───────────────────────────────────────────────

    private val json = SharedJson

    // ───── API 方法 ──────────────────────────────────────────────────

    /**
     * 获取当前已登录用户的详细信息（通过 WebView Cookie）。
     */
    suspend fun fetchCurrentUserInfo(): ApiResult<UserInfo?> = withContext(Dispatchers.IO) {
        val cookies = getWikiCookies()
        if (cookies.isNullOrBlank()) {
            return@withContext ApiResult.Error("未检测到登录 Cookie")
        }

        val url = buildUrl(
            "action" to "query",
            "meta" to "userinfo",
            "uiprop" to "groups|rights|editcount|registrationdate|email|realname",
            "format" to "json"
        )

        fetchWithCookies(url, cookies)
    }

    /**
     * 获取指定用户的最近编辑贡献（通过 WebView Cookie）。
     */
    suspend fun fetchContributions(
        userName: String,
        limit: Int = 20
    ): ApiResult<List<UserContrib>> = withContext(Dispatchers.IO) {
        val cookies = getWikiCookies()
        if (cookies.isNullOrBlank()) {
            return@withContext ApiResult.Error("未检测到登录 Cookie")
        }

        val url = buildUrl(
            "action" to "query",
            "list" to "usercontribs",
            "ucuser" to userName,
            "ucprop" to "ids|title|timestamp|comment|size|sizediff|flags",
            "uclimit" to "$limit",
            "format" to "json"
        )

        fetchJsonWithCookies(url, cookies, "解析编辑贡献失败") {
            json.decodeFromString<ContributionsResponse>(it).query?.usercontribs ?: emptyList()
        }
    }

    /**
     * 获取当前用户的监视列表最近变更（需要已登录 Cookie）。
     */
    suspend fun fetchWatchlist(
        limit: Int = 20
    ): ApiResult<List<WatchlistItem>> = withContext(Dispatchers.IO) {
        val cookies = getWikiCookies()
        if (cookies.isNullOrBlank()) {
            return@withContext ApiResult.Error("未检测到登录 Cookie")
        }

        val url = buildUrl(
            "action" to "query",
            "list" to "watchlist",
            "wlprop" to "ids|title|type|user|comment|timestamp|flags",
            "wllimit" to "$limit",
            "format" to "json"
        )

        fetchJsonWithCookies(url, cookies, "解析监视列表失败") {
            json.decodeFromString<WatchlistResponse>(it).query?.watchlist ?: emptyList()
        }
    }

    /** 监视列表变更类型中文映射 */
    fun watchTypeLabel(type: String): String = when (type) {
        "edit" -> "编辑"
        "new" -> "新建"
        "log" -> "日志"
        "categorize" -> "分类"
        else -> type
    }

    // ───── 内部工具 ──────────────────────────────────────────────────

    /**
     * 从 Android WebView CookieManager 获取 Wiki 站点的 Cookie 字符串。
     * 需要合并根路径和 /klbq/ 路径的 Cookie，因为关键的 session Cookie path=/klbq/。
     */
    private fun getWikiCookies(): String? {
        return try {
            val cm = CookieManager.getInstance()
            val rootCookies = cm.getCookie("https://wiki.biligame.com") ?: ""
            val klbqCookies = cm.getCookie("https://wiki.biligame.com/klbq/") ?: ""
            val cookieMap = mutableMapOf<String, String>()
            (rootCookies + "; " + klbqCookies).split(";").forEach { part ->
                val trimmed = part.trim()
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    cookieMap[trimmed.substring(0, eq).trim()] = trimmed.substring(eq + 1).trim()
                }
            }
            if (cookieMap.isEmpty()) return null
            cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 构造 API URL。
     */
    private fun buildUrl(vararg params: Pair<String, String>): String {
        val query = params.joinToString("&") { (k, v) ->
            "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
        }
        return "$API?$query"
    }

    /**
     * 用 OkHttp 发起带 Cookie 的请求并解析 UserInfo。
     */
    private fun fetchWithCookies(url: String, cookies: String): ApiResult<UserInfo?> {
        return fetchJsonWithCookies(url, cookies, "解析用户信息失败") {
            json.decodeFromString<UserInfoResponse>(it).query?.userinfo
        }
    }

    /**
     * 通用：用 OkHttp 发起带 Cookie 的 GET 请求，返回 JSON 解析结果。
     */
    private inline fun <T> fetchJsonWithCookies(
        url: String,
        cookies: String,
        parseErrorMessage: String,
        crossinline transform: (String) -> T
    ): ApiResult<T> {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookies)
                .header("User-Agent", "CalabiYauVoice/1.3 (Android)")
                .build()

            WikiEngine.client.newCall(request).execute().use { resp ->
                val body = resp.body.string()
                when {
                    !resp.isSuccessful ->
                        ApiResult.Error("请求失败：HTTP ${resp.code}")
                    body.isBlank() ->
                        ApiResult.Error("响应为空")
                    body.trimStart().startsWith("<") ->
                        ApiResult.Error("返回了非 JSON 页面，可能未登录或被网关拦截")
                    else -> {
                        runCatching { ApiResult.Success(transform(body)) }
                            .getOrElse { ApiResult.Error(parseErrorMessage) }
                    }
                }
            }
        }.getOrElse { e ->
            ApiResult.Error(e.message ?: "网络请求失败")
        }
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

    /** 用户组中文映射 */
    fun groupLabel(group: String): String = when (group) {
        "sysop" -> "管理员"
        "bureaucrat" -> "行政员"
        "bot" -> "机器人"
        "autoconfirmed" -> "自动确认用户"
        "patroller" -> "巡查员"
        "rollbacker" -> "回退员"
        "confirmed" -> "确认用户"
        "flood" -> "机器用户"
        "suppress" -> "监督员"
        "interface-admin" -> "界面管理员"
        "autoreviewer" -> "自动复审员"
        else -> group
    }
}
