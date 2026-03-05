package data

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 管理 MediaWiki Cookie，支持手动导入字符串格式的 Cookie。
 *
 * Cookie 格式示例（浏览器 Network 面板复制）：
 *   `klbqwiki_session=abc123; klbqwiki_UserID=456; klbqwiki_UserName=SomeUser`
 */
object WikiCookieManager {

    private const val WIKI_HOST = "wiki.biligame.com"
    private const val WIKI_URL = "https://wiki.biligame.com"

    // 当前导入的原始 Cookie 字符串（用于回显）
    private var rawCookieString: String = ""

    // 解析后的 Cookie 列表
    private val importedCookies = mutableListOf<Cookie>()

    /** 是否已导入 Cookie */
    val hasCookies: Boolean get() = importedCookies.isNotEmpty()

    /** 获取当前 Cookie 字符串（用于 UI 回显） */
    val currentCookieString: String get() = rawCookieString

    /**
     * 导入 Cookie 字符串，解析并注入到 WikiEngine 的 OkHttpClient。
     *
     * @param cookieStr 格式为 `key=value; key2=value2` 的 Cookie 字符串
     * @return 成功解析的 Cookie 数量
     */
    fun importCookies(cookieStr: String): Int {
        rawCookieString = cookieStr.trim()
        importedCookies.clear()

        if (rawCookieString.isBlank()) return 0

        val url = WIKI_URL.toHttpUrl()
        val parsed = rawCookieString.split(";")
            .mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx <= 0) return@mapNotNull null
                val name = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim()
                Cookie.Builder()
                    .domain(WIKI_HOST)
                    .path("/")
                    .name(name)
                    .value(value)
                    .build()
            }

        importedCookies.addAll(parsed)

        // 注入到 OkHttpClient 的 CookieJar
        WikiEngine.injectCookies(url, importedCookies)

        return importedCookies.size
    }

    /** 清除所有导入的 Cookie */
    fun clearCookies() {
        rawCookieString = ""
        importedCookies.clear()
        WikiEngine.injectCookies(WIKI_URL.toHttpUrl(), emptyList())
    }

    /**
     * 将已导入的 Cookie 转换为 HTTP Header 格式字符串，
     * 供直接构造请求时使用。
     */
    fun getCookieHeader(): String =
        importedCookies.joinToString("; ") { "${it.name}=${it.value}" }

    /** 尝试从 Cookie 中提取用户名（klbqwiki_UserName） */
    fun extractUserNameFromCookies(): String? =
        importedCookies.firstOrNull { it.name.endsWith("UserName") || it.name == "UserName" }?.value
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }

    /** 尝试从 Cookie 中提取用户 ID（klbqwiki_UserID） */
    fun extractUserIdFromCookies(): String? =
        importedCookies.firstOrNull { it.name.endsWith("UserID") || it.name == "UserID" }?.value
}

