package com.nekolaska.calabiyau.feature.weapon.list

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URLEncoder

/**
 * 武器列表 API（Android）。
 *
 * 通过 Semantic MediaWiki ask API 获取各分类武器列表，
 * 武器图片通过 `文件:使用者名-weapon.png` 命名规则获取。
 */
object WeaponListApi {

    init {
        MemoryCacheRegistry.register("WeaponListApi", ::clearMemoryCache)
    }

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



    // ── 内存缓存 ──
    @Volatile
    private var cachedData: List<WeaponCategoryData>? = null

    fun clearMemoryCache() { cachedData = null }

    /**
     * 获取所有分类的武器列表（带内存缓存）。
     */
    suspend fun fetchAllCategories(forceRefresh: Boolean = false): ApiResult<List<WeaponCategoryData>> {
        if (!forceRefresh) {
            cachedData?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedData = it.value
        }
    }

    /** 内部：带缓存元数据的分类结果 */
    private data class CategoryResult(
        val data: WeaponCategoryData,
        val isFromCache: Boolean,
        val ageMs: Long
    )

    private suspend fun fetchFromNetwork(
        forceRefresh: Boolean
    ): ApiResult<List<WeaponCategoryData>> =
        withContext(Dispatchers.IO) {
            try {
                val results = WeaponCategory.entries.map { category ->
                    async { fetchCategory(category, forceRefresh) }
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
            } catch (e: Exception) {
                ApiResult.Error("获取武器列表失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    /**
     * 获取单个分类的武器列表。
     */
    private suspend fun fetchCategory(
        category: WeaponCategory,
        forceRefresh: Boolean
    ): CategoryResult? {
        return try {
            // 统一查询所有属性
            val query = "[[分类:${category.smwCategory}]]|?使用者|?类型|?武器介绍|limit=100"
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$API?action=ask&query=$encoded&format=json"
            val cacheResult = OfflineCache.fetchWithCache(
                type = OfflineCache.Type.WEAPON_LIST,
                key = "category_${category.name}",
                forceRefresh = forceRefresh
            ) { WikiEngine.safeGet(url) } ?: return null
            val body = cacheResult.payload

            val json = SharedJson.parseToJsonElement(body).jsonObject
            val results = json["query"]?.jsonObject?.get("results")?.jsonObject ?: return null

            val weapons = results.entries.map { (weaponName, value) ->
                val obj = value.jsonObject
                val printouts = obj["printouts"]?.jsonObject

                val user = printouts?.get("使用者")?.jsonArray
                    ?.firstOrNull()?.jsonPrimitive?.content ?: ""
                val type = printouts?.get("类型")?.jsonArray
                    ?.firstOrNull()?.jsonPrimitive?.content ?: ""
                val desc = printouts?.get("武器介绍")?.jsonArray
                    ?.firstOrNull()?.jsonPrimitive?.content ?: ""
                val fullUrl = obj["fullurl"]?.jsonPrimitive?.content
                    ?: "${WIKI_BASE}${URLEncoder.encode(weaponName, "UTF-8").replace("+", "%20")}"

                WeaponInfo(
                    name = weaponName,
                    user = user,
                    type = type,
                    description = desc,
                    wikiUrl = fullUrl,
                    imageUrl = null // 图片稍后批量获取
                )
            }.sortedBy { it.name }

            // 批量获取武器图片
            val imageUrls = fetchWeaponImages(weapons, category, forceRefresh)
            val weaponsWithImages = weapons.map { weapon ->
                weapon.copy(imageUrl = imageUrls[weapon.name])
            }

            CategoryResult(
                data = WeaponCategoryData(
                    category = category,
                    weapons = weaponsWithImages
                ),
                isFromCache = cacheResult.isFromCache,
                ageMs = cacheResult.ageMs
            )
        } catch (_: Exception) {
            null
        }
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
