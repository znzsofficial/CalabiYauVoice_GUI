package com.nekolaska.calabiyau.feature.weapon.skin

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.HtmlText
import com.nekolaska.calabiyau.core.wiki.SkinCatalogMarkup
import com.nekolaska.calabiyau.core.wiki.WikiImageUrls
import com.nekolaska.calabiyau.feature.weapon.list.WeaponListApi
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.ioApiCall
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import util.buildWikiUrl

/**
 * 武器外观筛选 API（Android）。
 *
 * 通过 MediaWiki parse API 渲染 `{{#invoke:武器|武器外观筛选}}` Lua 模块，
 * 从返回的 HTML 中提取武器外观数据（data-param 属性 + img 标签）。
 */
object WeaponSkinFilterApi {

    internal data class Loaded(val skins: List<WeaponSkinInfo>, val complete: Boolean)
    private val catalog = object : CachedWikiApi<Loaded>("WeaponSkinFilterApi") {
        override fun shouldCache(value: Loaded) = value.complete
        override suspend fun fetchFromCache() = readDisk()
        override suspend fun fetchFromNetwork(forceRefresh: Boolean) = load(forceRefresh)
    }

    private const val API = "https://wiki.biligame.com/klbq/api.php"

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
        val weaponCategory: String, // 武器大类（主武器、副武器、近战武器、战术道具）
        val weaponType: String,     // 武器类型（自动步枪、霰弹枪等）
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
     * @param cacheOnly 仅读磁盘缓存，不发起网络请求（用于 stale-while-revalidate 预加载）
     */
    suspend fun fetchAllWeaponSkins(
        forceRefresh: Boolean = false,
        cacheOnly: Boolean = false,
        allowMemoryCache: Boolean = true
    ): ApiResult<List<WeaponSkinInfo>> = when (val result = catalog.fetch(
        forceRefresh = forceRefresh,
        cacheOnly = cacheOnly,
        allowMemoryCache = allowMemoryCache
    )) {
        is ApiResult.Success -> ApiResult.Success(result.value.skins, result.isOffline, result.cacheAgeMs)
        is ApiResult.Error -> result
    }

    private suspend fun readDisk(): ApiResult<Loaded> =
        ioApiCall("获取武器外观数据失败") {
            val entry = OfflineCache.getEntry(OfflineCache.Type.WEAPON_SKINS, "all_weapon_skins")
                ?: return@ioApiCall ApiResult.Error("无离线缓存", kind = ErrorKind.NETWORK)
            val meta = metaSnapshot(WeaponListApi.fetchAllCategories(cacheOnly = true, includeImages = false))
            parseSkinResult(
                entry.content,
                weaponMeta = meta.values,
                isOffline = true,
                cacheAgeMs = maxOf(entry.ageMs, meta.age)
            )
        }

    private suspend fun load(forceRefresh: Boolean): ApiResult<Loaded> =
    ioApiCall("获取武器外观数据失败") {
        // 武器元数据只查询 ask，不获取无关武器图片；与外观渲染互不依赖。
        // 皮肤数据只要 1 次模块渲染（快）。并行执行，关键路径取两者较大值。
        coroutineScope {
            val metaDeferred = async { fetchWeaponMetaMap(forceRefresh) }
            val url = buildWikiUrl(API, "action" to "parse", "text" to "{{#invoke:武器|武器外观筛选}}", "prop" to "text", "format" to "json")
            val result = OfflineCache.fetchWithCache(
                type = OfflineCache.Type.WEAPON_SKINS,
                key = "all_weapon_skins",
                forceRefresh = forceRefresh, validatePayload = { parseSkinResult(it, isOffline = false, cacheAgeMs = 0) is ApiResult.Success }
            ) { WikiEngine.safeGet(url) }
                ?: run {
                    metaDeferred.cancel()
                    return@coroutineScope ApiResult.Error("请求失败，且无离线缓存", kind = ErrorKind.NETWORK)
                }

            val meta = metaDeferred.await()
            assemble(result, meta)
        }
    }

    data class WeaponMeta(
        val category: String,
        val type: String
    )

    internal data class MetaSnapshot(val values: Map<String, WeaponMeta>, val offline: Boolean, val age: Long, val complete: Boolean)

    internal fun assemble(raw: OfflineCache.CacheResult, meta: MetaSnapshot) =
        parseSkinResult(raw.payload, meta.values, raw.isFromCache || meta.offline, maxOf(raw.ageMs, meta.age), meta.complete)

    private suspend fun fetchWeaponMetaMap(forceRefresh: Boolean): MetaSnapshot =
        metaSnapshot(WeaponListApi.fetchAllCategories(forceRefresh = forceRefresh, includeImages = false))

    internal fun metaSnapshot(result: ApiResult<List<WeaponListApi.WeaponCategoryData>>): MetaSnapshot = when (result) {
            is ApiResult.Success -> MetaSnapshot(result.value
                .flatMap { categoryData ->
                    categoryData.weapons.map { weapon ->
                        weapon.name to WeaponMeta(
                            category = categoryData.category.displayName,
                            type = weapon.type
                        )
                    }
                }
                .toMap(), result.isOffline, result.cacheAgeMs,
                result.value.map { it.category }.toSet() == WeaponListApi.WeaponCategory.entries.toSet() && result.value.all { it.loadError == null })
            is ApiResult.Error -> MetaSnapshot(emptyMap(), false, 0, false)
    }

    private fun parseSkinResult(
        jsonBody: String,
        weaponMeta: Map<String, WeaponMeta> = emptyMap(),
        isOffline: Boolean,
        cacheAgeMs: Long,
        complete: Boolean = false
    ): ApiResult<Loaded> {
        val json = SharedJson.parseToJsonElement(jsonBody).jsonObject
        val html = json["parse"]?.jsonObject?.get("text")
            ?.jsonObject?.get("*")?.jsonPrimitive?.content
            ?: return ApiResult.Error("无法获取武器外观数据", kind = ErrorKind.PARSE)

        val skins = parseWeaponSkinHtml(html, weaponMeta)
        return if (skins.isEmpty()) {
            ApiResult.Error("未找到武器外观数据", kind = ErrorKind.NOT_FOUND)
        } else {
            ApiResult.Success(Loaded(skins, complete), isOffline = isOffline, cacheAgeMs = cacheAgeMs)
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
    internal fun parseWeaponSkinHtml(html: String, weaponMeta: Map<String, WeaponMeta> = emptyMap()): List<WeaponSkinInfo> {
        // Current markup: the Lua module renders div.klbq-skin-card[data-param*] cards.
        val cards = Jsoup.parse(html).select("div.gallerygrid-item[data-param1], div.klbq-skin-card[data-param1]")
        if (cards.isNotEmpty()) {
            val parsed = cards.map { parseSkinCard(it, weaponMeta) }
            // 页面偶发完全重复卡按整行去重，避免列表 key 冲突
            return parsed.distinctBy { it }
        }

        // Legacy fallback: wikitext table rows |- class="divsort" data-param1=...
        val usedKeys = mutableSetOf<String>()

        return SkinCatalogMarkup.legacyRows(html).map { (fields, body) ->
            val weapon = fields.owner
            val info = parseWeaponSkinDetail(body, weapon)
            val protectedName = uniqueDisplayName(
                baseName = info.name,
                ownerName = weapon,
                blockHtml = body,
                usedKeys = usedKeys
            )

            WeaponSkinInfo(
                name = protectedName,
                weapon = weapon,
                weaponCategory = weaponMeta[weapon]?.category ?: "其他",
                weaponType = weaponMeta[weapon]?.type.orEmpty(),
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

    /** Parses one div.klbq-skin-card element from the current Lua module markup. */
    private fun parseSkinCard(card: Element, weaponMeta: Map<String, WeaponMeta>): WeaponSkinInfo {
        val fields = SkinCatalogMarkup.cardFields(card)
        val weapon = fields.owner
        val fullTextName = card.selectFirst("[class*=name]")?.text()?.trim().orEmpty()
        // The card name is already "武器：外观名"; fall back to weapon-prefixed forms.
        val name = when {
            fullTextName.contains('：') || fullTextName.contains(':') -> fullTextName
            fullTextName.isNotBlank() -> "$weapon：$fullTextName"
            else -> "$weapon：未知"
        }
        val previewImage = card.selectFirst("[class*=imagebox] img[alt]")
            ?: card.selectFirst("img[alt]")
        val hiddenLargeImage = card.select("span[style*=display:none] img").firstOrNull()

        return WeaponSkinInfo(
            name = name,
            weapon = weapon,
            weaponCategory = weaponMeta[weapon]?.category ?: "其他",
            weaponType = weaponMeta[weapon]?.type.orEmpty(),
            quality = Quality.fromLevel(fields.qualityLevel),
            sources = fields.sources,
            crystalCost = fields.crystalCost,
            baseCost = fields.baseCost,
            description = HtmlText.detail(card.selectFirst("[class*=desc] [class*=value]")),
            thumbnailUrl = previewImage?.attr("src")?.takeIf { it.isNotBlank() },
            fullImageUrl = WikiImageUrls.originalFromThumbnail(
                previewImage?.attr("srcset")
                    ?.split(',')
                    ?.lastOrNull()
                    ?.substringBeforeLast(' ')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: previewImage?.attr("src")?.takeIf { it.isNotBlank() }
            ),
            screenshotUrl = WikiImageUrls.originalFromThumbnail(
                hiddenLargeImage?.attr("srcset")
                    ?.split(',')
                    ?.lastOrNull()
                    ?.substringBeforeLast(' ')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: hiddenLargeImage?.attr("src")?.takeIf { it.isNotBlank() }
            )
        )
    }

    private fun parseWeaponSkinDetail(blockHtml: String, weapon: String): ParsedSkinVisualInfo {
        val document = Jsoup.parse(blockHtml)
        val previewImage = document.selectFirst("img[alt]")
        val hiddenLargeImage = document.select("span[style*=display:none] img").firstOrNull()

        val thumbnailUrl = previewImage?.attr("src")
        val fullImageUrl = WikiImageUrls.originalFromThumbnail(
            previewImage?.attr("srcset")
                ?.split(',')
                ?.lastOrNull()
                ?.substringBeforeLast(' ')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: previewImage?.attr("src")?.takeIf { it.isNotBlank() }
        )

        val screenshotUrl = WikiImageUrls.originalFromThumbnail(
            hiddenLargeImage?.attr("srcset")
                ?.split(',')
                ?.lastOrNull()
                ?.substringBeforeLast(' ')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: hiddenLargeImage?.attr("src")?.takeIf { it.isNotBlank() }
        )

        // Legacy cell markup: "<br/><a>武器</a>外观名<br/>简介…". The <a> holds the weapon
        // label and the following text node the skin name; later segments are description.
        // Jsoup decodes entities, so no manual &amp;/&middot; handling is needed.
        val anchorAfterBr = document.body().selectFirst("a")
        val linkedWeapon = anchorAfterBr?.text()?.replace(" ", "")?.trim().orEmpty()
        val nameLine = anchorAfterBr?.nextSibling()
            ?.let { sibling -> (sibling as? org.jsoup.nodes.TextNode)?.text() }
            ?.substringBefore('\n')
            ?.substringBefore('|')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: document.body().childNodes()
                .filterIsInstance<org.jsoup.nodes.TextNode>()
                .map { it.text().trim() }
                .firstOrNull { it.isNotBlank() && !it.contains('|') }
        val name = nameLine
            ?.removePrefix("|")
            ?.trim()
            ?.let { line ->
                val colon = line.indexOfFirst { it == '：' || it == ':' }
                val weaponLabel = if (colon > 0) line.substring(0, colon).replace(" ", "").trim() else linkedWeapon
                val skinLabel = if (colon >= 0) line.substring(colon + 1).trim() else line
                when {
                    weaponLabel.isNotBlank() && skinLabel.isNotBlank() -> "$weaponLabel：$skinLabel"
                    skinLabel.isNotBlank() -> if (skinLabel.contains('：') || skinLabel.contains(':')) skinLabel else "$weapon：$skinLabel"
                    else -> ""
                }
            }
            ?.takeIf { it.isNotBlank() }
            ?: "$weapon：未知"
        val description = SkinCatalogMarkup.legacyDescription(blockHtml) { compact ->
            qualityPrefix.containsMatchIn(compact) || compact.contains("单外观价格") || compact.contains("武器皮肤套装")
        }

        return ParsedSkinVisualInfo(
            name = name,
            description = description,
            thumbnailUrl = thumbnailUrl,
            fullImageUrl = fullImageUrl,
            screenshotUrl = screenshotUrl
        )
    }

    private val qualityPrefix = Regex("""^\d(精致|卓越|完美|传说|臻藏)""")
    private val identitySuffix = Regex("""武器外观图鉴\s*(\d+)""")

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
        var candidate = normalizedBase
        var key = candidate + ownerName

        if (usedKeys.add(key)) return candidate

        val identity = identitySuffix.find(blockHtml)?.groupValues?.get(1).orEmpty()
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

}
