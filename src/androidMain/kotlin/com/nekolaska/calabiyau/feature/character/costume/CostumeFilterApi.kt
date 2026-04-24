package com.nekolaska.calabiyau.feature.character.costume

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
import java.net.URLEncoder

/**
 * 角色时装筛选 API（Android）。
 *
 * 通过 MediaWiki parse API 渲染 `{{#invoke:角色|角色时装筛选}}` Lua 模块，
 * 从返回的 HTML 中提取时装数据（data-param 属性 + img 标签）。
 */
object CostumeFilterApi {

    init {
        MemoryCacheRegistry.register("CostumeFilterApi", ::clearMemoryCache)
    }

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    /** 内存缓存（进程生命周期内有效，避免重复请求） */
    @Volatile
    private var cachedCostumes: List<CostumeInfo>? = null

    fun clearMemoryCache() { cachedCostumes = null }

    /** 品质等级 */
    enum class Quality(val level: Int, val displayName: String) {
        INITIAL(1, "初始"),
        EXQUISITE(2, "精致"),
        SUPERIOR(3, "卓越"),
        PERFECT(4, "完美"),
        LEGENDARY(5, "传说"),
        SECRET(6, "私服");

        companion object {
            fun fromLevel(level: Int): Quality? = entries.find { it.level == level }
            fun fromLevel(level: String): Quality? = level.toIntOrNull()?.let { fromLevel(it) }
        }
    }

    /** 时装信息 */
    data class CostumeInfo(
        val name: String,           // 时装名（如"梅瑞狄斯：箴理-热砂"）
        val character: String,      // 角色名
        val quality: Quality?,      // 品质
        val sources: List<String>,  // 获取方式列表
        val crystalCost: String,    // 巴布洛晶核价格
        val baseCost: String,       // 基弦价格
        val thumbnailUrl: String?,  // 立绘缩略图 URL
        val fullImageUrl: String?,  // 立绘原图 URL
        val screenshotUrl: String?  // 游戏截图 URL
    )


    /**
     * 获取所有时装数据。
     * @param forceRefresh 强制刷新缓存
     */
    suspend fun fetchAllCostumes(forceRefresh: Boolean = false): ApiResult<List<CostumeInfo>> =
        withContext(Dispatchers.IO) {
            try {
                // 优先返回内存缓存
                if (!forceRefresh) {
                    cachedCostumes?.let { return@withContext ApiResult.Success(it) }
                }

                val text = URLEncoder.encode("{{#invoke:角色|角色时装筛选}}", "UTF-8")
                val url = "$API?action=parse&text=$text&prop=text&format=json"
                val result = OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.COSTUMES,
                    key = "all_costumes",
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
                        "无法获取时装数据",
                        kind = ErrorKind.PARSE
                    )

                val costumes = parseCostumeHtml(html)
                if (costumes.isEmpty()) {
                    ApiResult.Error("未找到时装数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    cachedCostumes = costumes
                    ApiResult.Success(
                        costumes,
                        isOffline = result.isFromCache,
                        cacheAgeMs = result.ageMs
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error("获取时装数据失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    /**
     * 从 HTML 解析时装数据。
     *
     * HTML 格式：
     * ```
     * data-param1="角色名" data-param2="品质" data-param3="获取方式" data-param4="晶核" data-param5="基弦"
     * | <img ... src="缩略图URL" ... srcset="原图URL 1.5x" .../><br />时装名
     * ```
     */
    private fun parseCostumeHtml(html: String): List<CostumeInfo> {
        val blockRegex = Regex(
            """\|-\s*class="divsort"\s+data-param1="([^"]*?)"\s+data-param2="([^"]*?)"\s+data-param3="([^"]*?)"\s+data-param4="([^"]*?)"\s+data-param5="([^"]*?)"([\s\S]*?)(?=\|-\s*class="divsort"|$)"""
        )
        val usedKeys = mutableSetOf<String>()

        val items = blockRegex.findAll(html).map { match ->
            val character = match.groupValues[1].trim()
            val info = parseCostumeDetail(match.groupValues[6], character)
            val protectedName = uniqueDisplayName(
                baseName = info.name,
                ownerName = character,
                blockHtml = match.groupValues[6],
                usedKeys = usedKeys
            )

            CostumeInfo(
                name = protectedName,
                character = character,
                quality = Quality.fromLevel(match.groupValues[2]),
                sources = match.groupValues[3].split(",").map { it.trim() }.filter { it.isNotBlank() },
                crystalCost = match.groupValues[4].trim(),
                baseCost = match.groupValues[5].trim(),
                thumbnailUrl = info.thumbnailUrl,
                fullImageUrl = info.fullImageUrl,
                screenshotUrl = info.screenshotUrl
            )
        }.toList()

        return items
    }

    private fun parseCostumeDetail(blockHtml: String, character: String): ParsedVisualInfo {
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

        val name = Regex("""<br\s*/?>\s*([^\n<|]+)""")
            .find(blockHtml)
            ?.groupValues?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "$character：未知"

        return ParsedVisualInfo(
            name = name,
            thumbnailUrl = thumbnailUrl,
            fullImageUrl = fullImageUrl,
            screenshotUrl = screenshotUrl
        )
    }

    private data class ParsedVisualInfo(
        val name: String,
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
        return Regex("""角色时装图鉴\s*(\d+)""")
            .find(blockHtml)
            ?.groupValues?.get(1)
            .orEmpty()
    }

}
