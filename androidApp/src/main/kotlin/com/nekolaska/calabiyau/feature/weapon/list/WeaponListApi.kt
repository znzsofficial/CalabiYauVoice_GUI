package com.nekolaska.calabiyau.feature.weapon.list

import com.nekolaska.calabiyau.core.cache.KeyedCachedWikiApi
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import util.buildWikiUrl
import util.wikiPathEncode

/**
 * 武器列表 API（Android）。
 *
 * 通过 Semantic MediaWiki ask API 获取各分类武器列表，
 * 武器图片通过 `文件:使用者名-weapon.png` 命名规则获取。
 */
object WeaponListApi : KeyedCachedWikiApi<WeaponListApi.WeaponListKey, List<WeaponListApi.WeaponCategoryData>>("WeaponListApi") {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"

    /** 武器分类 */
    enum class WeaponCategory(val displayName: String, val smwCategory: String) {
        PRIMARY("主武器", "主武器"),
        MELEE("近战武器", "近战武器"),
        SECONDARY("副武器", "副武器"),
        TACTICAL("战术道具", "战术道具")
    }

    /** 武器信息 */
    data class WeaponInfo(
        val name: String,
        val user: String,           // 使用者
        val type: String,           // 武器类型（自动步枪、狙击步枪等）
        val description: String,    // 武器介绍
        val wikiUrl: String,
        val imageUrl: String?        // 武器图片 URL
    )

    /** 分类武器数据 */
    data class WeaponCategoryData(
        val category: WeaponCategory,
        val weapons: List<WeaponInfo>
    )

    data class WeaponListKey(val includeImages: Boolean)

    /**
     * 获取所有分类的武器列表（带内存缓存）。
     */
    suspend fun fetchAllCategories(
        forceRefresh: Boolean = false,
        includeImages: Boolean = true,
        cacheOnly: Boolean = false,
        allowMemoryCache: Boolean = true
    ): ApiResult<List<WeaponCategoryData>> {
        if (!forceRefresh && !cacheOnly && allowMemoryCache && !includeImages) {
            getCachedValue(WeaponListKey(false))?.let { return ApiResult.Success(it) }
            getCachedValue(WeaponListKey(true))?.let { return ApiResult.Success(it) }
        }
        if (!forceRefresh && !cacheOnly && allowMemoryCache && includeImages) {
            getCachedValue(WeaponListKey(true))?.let { return ApiResult.Success(it) }
        }
        return fetch(
            WeaponListKey(includeImages),
            forceRefresh = forceRefresh,
            cacheOnly = cacheOnly,
            allowMemoryCache = allowMemoryCache
        )
    }

    /** 内部：带缓存元数据的分类结果 */
    private data class CategoryResult(
        val data: WeaponCategoryData,
        val isFromCache: Boolean,
        val ageMs: Long
    )

    override suspend fun fetchFromCache(key: WeaponListKey): ApiResult<List<WeaponCategoryData>> =
        withContext(Dispatchers.IO) {
            try {
                val results = WeaponCategory.entries.map { category ->
                    async { loadCachedCategory(category, key.includeImages) }
                }.awaitAll()

                val data = results.filterNotNull()
                if (data.isEmpty()) {
                    ApiResult.Error("没有武器列表缓存", kind = ErrorKind.NETWORK)
                } else {
                    ApiResult.Success(
                        data.map { it.data },
                        isOffline = true,
                        cacheAgeMs = data.maxOfOrNull { it.ageMs } ?: 0L
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ApiResult.Error("读取武器列表缓存失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    override suspend fun fetchFromNetwork(
        key: WeaponListKey,
        forceRefresh: Boolean
    ): ApiResult<List<WeaponCategoryData>> =
        withContext(Dispatchers.IO) {
            try {
                val includeImages = key.includeImages
                val results = WeaponCategory.entries.map { category ->
                    async { fetchCategory(category, forceRefresh, includeImages) }
                }.awaitAll()

                val data = results.filterNotNull()
                if (data.isEmpty()) {
                    ApiResult.Error("获取武器列表失败", kind = ErrorKind.NETWORK)
                } else {
                    val isOffline = data.any { it.isFromCache }
                    val maxAge = data.maxOfOrNull { it.ageMs } ?: 0L
                    ApiResult.Success(
                        data.map { it.data },
                        isOffline = isOffline,
                        cacheAgeMs = maxAge
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ApiResult.Error("获取武器列表失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    /**
     * 获取单个分类的武器列表。
     */
    private suspend fun fetchCategory(
        category: WeaponCategory,
        forceRefresh: Boolean,
        includeImages: Boolean
    ): CategoryResult? {
        return try {
            // 统一查询所有属性
            val query = "[[分类:${category.smwCategory}]]|?使用者|?类型|?武器介绍|limit=100"
            val url = buildWikiUrl(API, "action" to "ask", "query" to query, "format" to "json")
            val cacheResult = OfflineCache.fetchWithCache(
                type = OfflineCache.Type.WEAPON_LIST,
                key = "category_${category.name}",
                forceRefresh = forceRefresh
            ) { WikiEngine.safeGet(url) } ?: return null
            val body = cacheResult.payload

            val weapons = parseWeapons(body)

            val weaponsWithImages = if (includeImages) {
                val imageUrls = fetchWeaponImages(weapons, category, forceRefresh)
                weapons.map { weapon -> weapon.copy(imageUrl = imageUrls[weapon.name]) }
            } else {
                weapons
            }

            CategoryResult(
                data = WeaponCategoryData(
                    category = category,
                    weapons = weaponsWithImages
                ),
                isFromCache = cacheResult.isFromCache,
                ageMs = cacheResult.ageMs
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadCachedCategory(
        category: WeaponCategory,
        includeImages: Boolean
    ): CategoryResult? {
        return try {
            val entry = OfflineCache.getEntry(
                type = OfflineCache.Type.WEAPON_LIST,
                key = "category_${category.name}"
            ) ?: return null
            val weapons = parseWeapons(entry.content)
            val weaponsWithImages = if (includeImages) {
                val imageUrls = loadCachedWeaponImages(category)
                weapons.map { weapon -> weapon.copy(imageUrl = imageUrls[weapon.name]) }
            } else {
                weapons
            }

            CategoryResult(
                data = WeaponCategoryData(
                    category = category,
                    weapons = weaponsWithImages
                ),
                isFromCache = true,
                ageMs = entry.ageMs
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun parseWeapons(body: String): List<WeaponInfo> {
        val json = SharedJson.parseToJsonElement(body).jsonObject
        val results = json["query"]?.jsonObject?.get("results")?.jsonObject ?: return emptyList()

        return results.entries.map { (weaponName, value) ->
            val obj = value.jsonObject
            val printouts = obj["printouts"]?.jsonObject

            val user = printouts?.get("使用者")?.jsonArray
                ?.firstOrNull()?.jsonPrimitive?.content ?: ""
            val type = printouts?.get("类型")?.jsonArray
                ?.firstOrNull()?.jsonPrimitive?.content ?: ""
            val desc = printouts?.get("武器介绍")?.jsonArray
                ?.firstOrNull()?.jsonPrimitive?.content?.let(::cleanDescription) ?: ""
            val fullUrl = obj["fullurl"]?.jsonPrimitive?.content
                ?: "$WIKI_BASE${weaponName.wikiPathEncode()}"

            WeaponInfo(
                name = weaponName,
                user = user,
                type = type,
                description = desc,
                wikiUrl = fullUrl,
                imageUrl = null
            )
        }.sortedBy { it.name }
    }

    private fun cleanDescription(raw: String): String {
        if (raw.isBlank()) return ""
        val document = Jsoup.parseBodyFragment(raw)
        document.select(".smwttcontent, .smwtticon").remove()
        return document.body().text()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private suspend fun loadCachedWeaponImages(category: WeaponCategory): Map<String, String> {
        val entry = OfflineCache.getEntry(
            type = OfflineCache.Type.WEAPON_LIST,
            key = "category_images_${category.name}"
        ) ?: return emptyMap()
        val parsed = SharedJson.parseToJsonElement(entry.content).jsonObject
        return parsed.mapValues { (_, value) -> value.jsonPrimitive.content }
    }

    /**
     * 批量获取武器图片 URL。
     * - 主武器命名规则：`文件:使用者名-weapon.png`
     * - 非主武器命名规则：`文件:武器-武器名.png`
     */
    private suspend fun fetchWeaponImages(
        weapons: List<WeaponInfo>,
        category: WeaponCategory,
        forceRefresh: Boolean
    ): Map<String, String> = withContext(Dispatchers.IO) {
        // 构建需要查询的文件标题列表
        val titleMap = mutableMapOf<String, String>() // fileName -> weaponName
        weapons.forEach { weapon ->
            val fileName = if (category == WeaponCategory.PRIMARY && weapon.user.isNotBlank()) {
                "${weapon.user}-weapon.png"
            } else {
                "武器-${weapon.name}.png"
            }
            titleMap[fileName] = weapon.name
        }

        if (titleMap.isEmpty()) return@withContext emptyMap()

        val cacheResult = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.WEAPON_LIST,
            key = "category_images_${category.name}",
            forceRefresh = forceRefresh
        ) {
            val urlMap = WikiEngine.fetchImageUrls(titleMap.keys.toList())
            buildJsonObject {
                titleMap.forEach { (fileName, weaponName) ->
                    urlMap[fileName]?.let { put(weaponName, it) }
                }
            }.toString()
        }

        val cachedJson = cacheResult?.payload ?: return@withContext emptyMap()
        val parsed = SharedJson.parseToJsonElement(cachedJson).jsonObject
        parsed.mapValues { (_, value) -> value.jsonPrimitive.content }
    }

}
