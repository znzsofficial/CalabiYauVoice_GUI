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

    data class CookieImportPreview(
        val normalizedCookieString: String,
        val cookieCount: Int,
        val detectedUserName: String?,
        val detectedUserId: String?
    ) {
        val hasCookies: Boolean get() = cookieCount > 0
    }

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
        val preview = previewCookieImport(cookieStr)
        rawCookieString = preview.normalizedCookieString
        importedCookies.clear()

        if (!preview.hasCookies) return 0

        val url = WIKI_URL.toHttpUrl()
        importedCookies.addAll(parseCookies(preview.normalizedCookieString))
        WikiEngine.injectCookies(url, importedCookies)
        return importedCookies.size
    }

    /**
     * 预览 Cookie 导入效果，返回规范化后的 Cookie 字符串和提取的用户信息。
     *
     * @param cookieStr 原始 Cookie 字符串
     * @return Cookie 导入预览信息
     */
    fun previewCookieImport(cookieStr: String): CookieImportPreview {
        val normalized = normalizeCookieInput(cookieStr)
        val cookies = parseCookies(normalized)
        return CookieImportPreview(
            normalizedCookieString = cookies.joinToString("; ") { "${it.name}=${it.value}" },
            cookieCount = cookies.size,
            detectedUserName = extractUserName(cookies),
            detectedUserId = extractUserId(cookies)
        )
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
    fun extractUserNameFromCookies(): String? = extractUserName(importedCookies)

    /** 尝试从 Cookie 中提取用户 ID（klbqwiki_UserID） */
    fun extractUserIdFromCookies(): String? = extractUserId(importedCookies)

    private fun normalizeCookieInput(cookieStr: String): String {
        val trimmed = cookieStr.trim()
        if (trimmed.isBlank()) return ""

        val headerMatch = Regex("""(?is)cookie\s*:\s*([^\r\n\"']+)""").find(trimmed)
        val candidate = when {
            headerMatch != null -> headerMatch.groupValues[1]
            trimmed.startsWith("Cookie:", ignoreCase = true) -> trimmed.substringAfter(':')
            else -> trimmed
        }

        return candidate
            .replace("\r", "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim('"', '\'', ' ')
            .trimEnd(';')
    }

    private fun parseCookies(cookieStr: String): List<Cookie> {
        if (cookieStr.isBlank()) return emptyList()
        return cookieStr.split(Regex("[;\n]+"))
            .mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx <= 0) return@mapNotNull null
                val name = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim().trim('"', '\'')
                if (name.isBlank() || value.isBlank()) return@mapNotNull null
                Cookie.Builder()
                    .domain(WIKI_HOST)
                    .path("/")
                    .name(name)
                    .value(value)
                    .build()
            }
    }

    private fun extractUserName(cookies: List<Cookie>): String? =
        cookies.firstOrNull { it.name.endsWith("UserName") || it.name == "UserName" }?.value
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }

    private fun extractUserId(cookies: List<Cookie>): String? =
        cookies.firstOrNull { it.name.endsWith("UserID") || it.name == "UserID" }?.value
}
