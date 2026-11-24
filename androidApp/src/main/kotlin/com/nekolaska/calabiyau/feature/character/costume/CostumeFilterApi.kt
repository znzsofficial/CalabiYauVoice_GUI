package com.nekolaska.calabiyau.feature.character.costume

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.HtmlText
import com.nekolaska.calabiyau.core.wiki.SkinCatalogMarkup
import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.ioApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import util.buildWikiUrl

/**
 * 角色时装筛选 API（Android）。
 *
 * 通过 MediaWiki parse API 渲染 `{{#invoke:角色|角色时装筛选}}` Lua 模块，
 * 从返回的 HTML 中提取时装数据（data-param 属性 + img 标签）。
 */
object CostumeFilterApi : CachedWikiApi<List<CostumeFilterApi.CostumeInfo>>("CostumeFilterApi") {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

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
        val description: String,    // 时装简介
        val thumbnailUrl: String?,  // 立绘缩略图 URL
        val fullImageUrl: String?,  // 立绘原图 URL
        val screenshotUrl: String?  // 游戏截图 URL
    )

    /**
     * 获取所有时装数据。
     * @param forceRefresh 强制刷新缓存
     * @param cacheOnly 仅读磁盘缓存，不发起网络请求（用于 stale-while-revalidate 预加载）
     */
    suspend fun fetchAllCostumes(
        forceRefresh: Boolean = false,
        cacheOnly: Boolean = false,
        allowMemoryCache: Boolean = true
    ): ApiResult<List<CostumeInfo>> = fetch(
        forceRefresh = forceRefresh,
        cacheOnly = cacheOnly,
        allowMemoryCache = allowMemoryCache
    )

    override suspend fun fetchFromCache(): ApiResult<List<CostumeInfo>> =
        withContext(Dispatchers.IO) {
            ioApiCall("获取时装数据失败") {
                val entry = OfflineCache.getEntry(OfflineCache.Type.COSTUMES, "all_costumes")
                    ?: return@ioApiCall ApiResult.Error("无离线缓存", kind = ErrorKind.NETWORK)
                parseCostumeResult(entry.content, isOffline = true, cacheAgeMs = entry.ageMs)
            }
        }

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<List<CostumeInfo>> =
        withContext(Dispatchers.IO) {
            ioApiCall("获取时装数据失败") {
                val url = buildWikiUrl(API, "action" to "parse", "text" to "{{#invoke:角色|角色时装筛选}}", "prop" to "text", "format" to "json")
                val result = OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.COSTUMES,
                    key = "all_costumes",
                    forceRefresh = forceRefresh
                ) { WikiEngine.safeGet(url) }
                    ?: return@ioApiCall ApiResult.Error(
                        "请求失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )
                parseCostumeResult(result.payload, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
            }
        }

    private fun parseCostumeResult(
        jsonBody: String,
        isOffline: Boolean,
        cacheAgeMs: Long
    ): ApiResult<List<CostumeInfo>> {
        val json = SharedJson.parseToJsonElement(jsonBody).jsonObject
        val html = json["parse"]?.jsonObject?.get("text")
            ?.jsonObject?.get("*")?.jsonPrimitive?.content
            ?: return ApiResult.Error("无法获取时装数据", kind = ErrorKind.PARSE)

        val costumes = parseCostumeHtml(html)
        return if (costumes.isEmpty()) {
            ApiResult.Error("未找到时装数据", kind = ErrorKind.NOT_FOUND)
        } else {
            ApiResult.Success(costumes, isOffline = isOffline, cacheAgeMs = cacheAgeMs)
        }
    }

    /**
     * 从 HTML 解析时装数据。
     *
     * 现网为 `gallerygrid-item klbq-skin-card` 卡片；旧版为 `tr.divsort` 表格。
     */
    internal fun parseCostumeHtml(html: String): List<CostumeInfo> {
        val document = Jsoup.parse(html)
        val cards = document.select(".gallerygrid-item.klbq-skin-card, .klbq-skin-card")
        if (cards.isNotEmpty()) {
            val usedKeys = mutableSetOf<String>()
            return cards.mapNotNull { card -> parseCostumeCard(card, usedKeys) }
        }
        return parseLegacyCostumeTable(html)
    }

    private fun parseCostumeCard(card: Element, usedKeys: MutableSet<String>): CostumeInfo? {
        val fields = SkinCatalogMarkup.cardFields(card)
        val character = fields.owner
        if (character.isBlank()) return null

        val name = card.firstBySkinClass("name")?.text()?.trim()
            .orEmpty()
            .ifBlank { "$character：未知" }
        val description = HtmlText.detail(card.firstBySkinClass("desc")?.firstBySkinClass("value"))
        val previewImage = card.firstBySkinClass("imagebox")?.selectFirst("img")
            ?: card.selectFirst("img[alt*=图鉴]")
        val screenshotImage = card.select("img").firstOrNull { image ->
            val alt = image.attr("alt")
            alt.isNotBlank() && "图鉴" !in alt && "图标" !in alt
        }
        val identity = previewImage?.attr("alt").orEmpty()
        val protectedName = uniqueDisplayName(name, character, identity, usedKeys)

        return CostumeInfo(
            name = protectedName,
            character = character,
            quality = Quality.fromLevel(fields.qualityLevel),
            sources = fields.sources,
            crystalCost = fields.crystalCost,
            baseCost = fields.baseCost,
            description = description,
            thumbnailUrl = previewImage?.attr("src")?.takeIf { it.isNotBlank() },
            fullImageUrl = originalImageUrl(previewImage),
            screenshotUrl = originalImageUrl(screenshotImage)
        )
    }

    private fun Element.firstBySkinClass(suffix: String): Element? {
        val target = "klbq-skin-card__$suffix"
        return sequenceOf(this).plus(select("*")).firstOrNull { element ->
            element.classNames().any { className ->
                className.replace("&#95;", "_") == target
            }
        }
    }

    private fun originalImageUrl(image: Element?): String? {
        if (image == null) return null
        return WikiImageUrls.originalFromThumbnail(
            image.attr("srcset")
                .split(',')
                .lastOrNull()
                ?.substringBeforeLast(' ')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: image.attr("src").takeIf { it.isNotBlank() }
        )
    }

    private fun parseLegacyCostumeTable(html: String): List<CostumeInfo> {
        val usedKeys = mutableSetOf<String>()

        return SkinCatalogMarkup.legacyRows(html).map { (fields, body) ->
            val character = fields.owner
            val info = parseCostumeDetail(body, character)
            val protectedName = uniqueDisplayName(
                baseName = info.name,
                ownerName = character,
                identity = extractIdentitySuffix(body),
                usedKeys = usedKeys
            )

            CostumeInfo(
                name = protectedName,
                character = character,
                quality = Quality.fromLevel(fields.qualityLevel),
                sources = fields.sources,
                crystalCost = fields.crystalCost,
                baseCost = fields.baseCost,
                description = info.description,
                thumbnailUrl = info.thumbnailUrl,
                fullImageUrl = info.fullImageUrl,
                screenshotUrl = info.screenshotUrl
            )
        }.toList()
    }

    private fun parseCostumeDetail(blockHtml: String, character: String): ParsedVisualInfo {
        val document = Jsoup.parse(blockHtml)
        val previewImage = document.selectFirst("img[alt]")
        val hiddenLargeImage = document.select("span[style*=display:none] img").firstOrNull()

        val thumbnailUrl = previewImage?.attr("src")
        val fullImageUrl = originalImageUrl(previewImage)
        val screenshotUrl = originalImageUrl(hiddenLargeImage)

        // Legacy cell markup: "<br/>外观名|…". Name is the text right after <br>,
        // cut at the wikitext cell separator; Jsoup decodes entities.
        val name = document.body().selectFirst("br")?.nextSibling()
            ?.let { sibling -> (sibling as? org.jsoup.nodes.TextNode)?.text() }
            ?.trim()
            ?.substringBefore("|")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "$character：未知"
        val description = SkinCatalogMarkup.legacyDescription(blockHtml) { compact ->
            qualityPrefix.containsMatchIn(compact) || compact.contains("单时装价格")
        }

        return ParsedVisualInfo(
            name = name,
            description = description,
            thumbnailUrl = thumbnailUrl,
            fullImageUrl = fullImageUrl,
            screenshotUrl = screenshotUrl
        )
    }

    private val qualityPrefix = Regex("""^\d(初始|精致|卓越|完美|传说|私服)""")
    private val identitySuffix = Regex("""角色时装图鉴\s*(\d+)""")

    private data class ParsedVisualInfo(
        val name: String,
        val description: String,
        val thumbnailUrl: String?,
        val fullImageUrl: String?,
        val screenshotUrl: String?
    )

    private fun uniqueDisplayName(
        baseName: String,
        ownerName: String,
        identity: String,
        usedKeys: MutableSet<String>
    ): String {
        val normalizedBase = baseName.ifBlank { "$ownerName：未知" }
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
        return identitySuffix
            .find(blockHtml)
            ?.groupValues?.get(1)
            .orEmpty()
    }

}
