package com.nekolaska.calabiyau.feature.weapon.skin

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * 武器外观筛选 API（Android）。
 *
 * 通过 MediaWiki parse API 渲染 `{{#invoke:武器|武器外观筛选}}` Lua 模块，
 * 从返回的 HTML 中提取武器外观数据（data-param 属性 + img 标签）。
 */
object WeaponSkinFilterApi {

    init {
        MemoryCacheRegistry.register("WeaponSkinFilterApi", ::clearMemoryCache)
    }

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    /** 内存缓存（进程生命周期内有效，避免重复请求） */
    @Volatile
    private var cachedSkins: List<WeaponSkinInfo>? = null

    fun clearMemoryCache() { cachedSkins = null }

    /** 品质等级 */
    enum class Quality(val level: Int, val displayName: String) {
        EXQUISITE(2, "精致"),
        SUPERIOR(3, "卓越"),
        PERFECT(4, "完美"),
        LEGENDARY(5, "传说"),
        COLLECTION(8, "臻藏");

        companion object {
            fun fromLevel(level: Int): Quality? = entries.find { it.level == level }
            fun fromLevel(level: String): Quality? = level.toIntOrNull()?.let { fromLevel(it) }
        }
    }

    /** 武器外观信息 */
    data class WeaponSkinInfo(
        val name: String,           // 外观名（如"独舞：回味时光"）
        val weapon: String,         // 武器名（如"独舞"）
        val quality: Quality?,      // 品质
        val sources: List<String>,  // 获取方式列表
        val crystalCost: String,    // 巴布洛晶核价格
        val baseCost: String,       // 基弦价格
        val description: String,    // 外观简介
        val thumbnailUrl: String?,  // 立绘缩略图 URL
        val fullImageUrl: String?,  // 立绘原图 URL
        val screenshotUrl: String?  // 游戏截图 URL
    )


    /**
     * 获取所有武器外观数据。
     * @param forceRefresh 强制刷新缓存
     */
    suspend fun fetchAllWeaponSkins(forceRefresh: Boolean = false): ApiResult<List<WeaponSkinInfo>> =
        withContext(Dispatchers.IO) {
            try {
                // 优先返回内存缓存
                if (!forceRefresh) {
                    cachedSkins?.let { return@withContext ApiResult.Success(it) }
                }

                val text = URLEncoder.encode("{{#invoke:武器|武器外观筛选}}", "UTF-8")
                val url = "$API?action=parse&text=$text&prop=text&format=json"
                val result = OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.WEAPON_SKINS,
                    key = "all_weapon_skins",
                    forceRefresh = forceRefresh
                ) { WikiEngine.safeGet(url) }
                    ?: return@withContext ApiResult.Error(
                        "请求失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )
                val body = result.json

                val json = SharedJson.parseToJsonElement(body).jsonObject
                val html = json["parse"]?.jsonObject?.get("text")
                    ?.jsonObject?.get("*")?.jsonPrimitive?.content
                    ?: return@withContext ApiResult.Error(
                        "无法获取武器外观数据",
                        kind = ErrorKind.PARSE
                    )

                val skins = parseWeaponSkinHtml(html)
                if (skins.isEmpty()) {
                    ApiResult.Error("未找到武器外观数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    cachedSkins = skins
                    ApiResult.Success(
                        skins,
                        isOffline = result.isFromCache,
                        cacheAgeMs = result.ageMs
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error("获取武器外观数据失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    /**
     * 从 HTML 解析武器外观数据。
     *
     * HTML 格式（与时装筛选页相同）：
     * ```
     * data-param1="武器名" data-param2="品质" data-param3="获取方式" data-param4="晶核" data-param5="基弦"
     * | <img ... src="缩略图URL" ... srcset="原图URL 1.5x" .../><br />武器名：外观名
     * ```
     */
    private fun parseWeaponSkinHtml(html: String): List<WeaponSkinInfo> {
        val blockRegex = Regex(
            """\|-\s*class="divsort"\s+data-param1="([^"]*?)"\s+data-param2="([^"]*?)"\s+data-param3="([^"]*?)"\s+data-param4="([^"]*?)"\s+data-param5="([^"]*?)"([\s\S]*?)(?=\|-\s*class="divsort"|$)"""
        )
        val usedKeys = mutableSetOf<String>()

        val items = blockRegex.findAll(html).map { match ->
            val weapon = match.groupValues[1].trim()
            val info = parseWeaponSkinDetail(match.groupValues[6], weapon)
            val protectedName = uniqueDisplayName(
                baseName = info.name,
                ownerName = weapon,
                blockHtml = match.groupValues[6],
                usedKeys = usedKeys
            )

            WeaponSkinInfo(
                name = protectedName,
                weapon = weapon,
                quality = Quality.fromLevel(match.groupValues[2]),
                sources = match.groupValues[3].split(",").map { it.trim() }.filter { it.isNotBlank() },
                crystalCost = match.groupValues[4].replace("无", "").trim(),
                baseCost = match.groupValues[5].replace("无", "").trim(),
                description = info.description,
                thumbnailUrl = info.thumbnailUrl,
                fullImageUrl = info.fullImageUrl,
                screenshotUrl = info.screenshotUrl
            )
        }.toList()

        return items
    }

    private fun parseWeaponSkinDetail(blockHtml: String, weapon: String): ParsedSkinVisualInfo {
        val document = Jsoup.parse(blockHtml)
        val previewImage = document.selectFirst("img[alt]")
        val hiddenLargeImage = document.select("span[style*=display:none] img").firstOrNull()

        val thumbnailUrl = previewImage?.attr("src")
        val fullImageUrl = previewImage?.attr("srcset")
            ?.split(',')
            ?.lastOrNull()
            ?.substringBeforeLast(' ')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: previewImage?.attr("src")?.takeIf { it.isNotBlank() }

        val screenshotUrl = hiddenLargeImage?.attr("srcset")
            ?.split(',')
            ?.lastOrNull()
            ?.substringBeforeLast(' ')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: hiddenLargeImage?.attr("src")?.takeIf { it.isNotBlank() }

        val name = Regex("""<br\s*/?>\s*(?:<a[^>]*>(.*?)</a>)?\s*[:：]?\s*([^<\n|]+)""", RegexOption.DOT_MATCHES_ALL)
            .find(blockHtml)
            ?.let { match ->
                val weaponLabel = Jsoup.parse(match.groupValues[1]).text().replace(" ", "").trim()
                val skinLabel = Jsoup.parse(match.groupValues[2]).text().trim()
                when {
                    weaponLabel.isNotBlank() && skinLabel.isNotBlank() -> "$weaponLabel：$skinLabel"
                    skinLabel.isNotBlank() -> if (skinLabel.contains('：') || skinLabel.contains(':')) skinLabel else "$weapon：$skinLabel"
                    else -> ""
                }
            }
            ?.removePrefix("|")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "$weapon：未知"
        val description = parseDescription(blockHtml)

        return ParsedSkinVisualInfo(
            name = name,
            description = description,
            thumbnailUrl = thumbnailUrl,
            fullImageUrl = fullImageUrl,
            screenshotUrl = screenshotUrl
        )
    }

    private fun parseDescription(blockHtml: String): String {
        val columns = splitTableColumns(blockHtml)
        if (columns.size < 2) return ""

        val firstContentIndex = columns.indexOfFirst { htmlColumnToText(it).isNotBlank() }
        if (firstContentIndex == -1) return ""

        val lastIndex = columns.lastIndex
        val lastText = htmlColumnToText(columns[lastIndex])
        val descriptionIndex = if (lastText.isLikelyRemark() && lastIndex - 1 > firstContentIndex) {
            lastIndex - 1
        } else {
            lastIndex
        }

        return htmlColumnToText(columns.getOrNull(descriptionIndex).orEmpty())
            .takeIf { it.isLikelyDescription() }
            .orEmpty()
    }

    private fun splitTableColumns(blockHtml: String): List<String> {
        return Regex("""(?m)^\|\s*""")
            .split(blockHtml)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun htmlColumnToText(html: String): String {
        if (html.isBlank()) return ""
        val normalizedHtml = html
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        return Jsoup.parse(normalizedHtml)
            .body()
            .textWithLineBreaks()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun String.isLikelyDescription(): Boolean {
        val compact = replace(Regex("""\s+"""), "")
        if (compact.length < 12) return false
        if (Regex("""^\d+(晶核|基弦)?$""").matches(compact)) return false
        if (compact.contains("精致") || compact.contains("卓越") || compact.contains("完美") ||
            compact.contains("传说") || compact.contains("臻藏")) return false
        return true
    }

    private fun String.isLikelyRemark(): Boolean {
        val compact = replace(Regex("""\s+"""), "")
        if (compact.isBlank()) return false
        if (compact.contains('：') || compact.contains(':')) return true
        return compact.contains("原价") || compact.contains("限时") || compact.contains("特效") ||
                compact.contains("套装") || compact.contains("兑换") || compact.contains("获得")
    }

    private fun Element.textWithLineBreaks(): String {
        val builder = StringBuilder()
        fun appendNode(element: Element) {
            element.childNodes().forEach { node ->
                when (node) {
                    is org.jsoup.nodes.TextNode -> builder.append(node.text())
                    is Element -> {
                        if (node.tagName().equals("br", ignoreCase = true)) {
                            builder.append('\n')
                        } else {
                            appendNode(node)
                            if (node.tagName().equals("p", ignoreCase = true)) builder.append('\n')
                        }
                    }
                }
            }
        }
        appendNode(this)
        return builder.toString()
    }

    private data class ParsedSkinVisualInfo(
        val name: String,
        val description: String,
        val thumbnailUrl: String?,
        val fullImageUrl: String?,
        val screenshotUrl: String?
    )

    private fun uniqueDisplayName(
        baseName: String,
        ownerName: String,
        blockHtml: String,
        usedKeys: MutableSet<String>
    ): String {
        val normalizedBase = baseName.ifBlank { "$ownerName：未知" }
        val identity = extractIdentitySuffix(blockHtml)
        var candidate = normalizedBase
        var key = candidate + ownerName

        if (usedKeys.add(key)) return candidate

        if (identity.isNotBlank()) {
            candidate = "$normalizedBase#$identity"
            key = candidate + ownerName
            if (usedKeys.add(key)) return candidate
        }

        var index = 2
        while (true) {
            candidate = "$normalizedBase#$index"
            key = candidate + ownerName
            if (usedKeys.add(key)) return candidate
            index++
        }
    }

    private fun extractIdentitySuffix(blockHtml: String): String {
        return Regex("""武器外观图鉴\s*(\d+)""")
            .find(blockHtml)
            ?.groupValues?.get(1)
            .orEmpty()
    }

}
