package com.nekolaska.calabiyau.data

import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request
import java.net.URLEncoder

/**
 * 武器列表 API（Android）。
 *
 * 通过 Semantic MediaWiki ask API 获取各分类武器列表，
 * 武器图片通过 `文件:使用者名-weapon.png` 命名规则获取。
 */
object WeaponListApi {

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

    sealed interface ApiResult<out T> {
        data class Success<T>(val value: T) : ApiResult<T>
        data class Error(val message: String) : ApiResult<Nothing>
    }

    // ── 内存缓存 ──
    @Volatile
    private var cachedData: List<WeaponCategoryData>? = null

    /**
     * 获取所有分类的武器列表（带内存缓存）。
     */
    suspend fun fetchAllCategories(forceRefresh: Boolean = false): ApiResult<List<WeaponCategoryData>> {
        if (!forceRefresh) {
            cachedData?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork().also {
            if (it is ApiResult.Success) cachedData = it.value
        }
    }

    private suspend fun fetchFromNetwork(): ApiResult<List<WeaponCategoryData>> =
        withContext(Dispatchers.IO) {
            try {
                val results = WeaponCategory.entries.map { category ->
                    async { fetchCategory(category) }
                }.awaitAll()

                val data = results.filterNotNull()
                if (data.isEmpty()) {
                    ApiResult.Error("获取武器列表失败")
                } else {
                    ApiResult.Success(data)
                }
            } catch (e: Exception) {
                ApiResult.Error("获取武器列表失败: ${e.message}")
            }
        }

    /**
     * 获取单个分类的武器列表。
     */
    private suspend fun fetchCategory(category: WeaponCategory): WeaponCategoryData? {
        return try {
            // 统一查询所有属性
            val query = "[[分类:${category.smwCategory}]]|?使用者|?类型|?武器介绍"
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$API?action=ask&query=$encoded|limit=100&format=json"
            val body = httpGet(url) ?: return null

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
            val weaponsWithImages = fetchWeaponImages(weapons, category)

            WeaponCategoryData(
                category = category,
                weapons = weaponsWithImages
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
        category: WeaponCategory
    ): List<WeaponInfo> = withContext(Dispatchers.IO) {
        // 构建需要查询的文件标题列表
        val titleMap = mutableMapOf<String, String>() // fileTitle -> weaponName
        weapons.forEach { weapon ->
            val fileTitle = if (category == WeaponCategory.PRIMARY && weapon.user.isNotBlank()) {
                "文件:${weapon.user}-weapon.png"
            } else {
                "文件:武器-${weapon.name}.png"
            }
            titleMap[fileTitle] = weapon.name
        }

        if (titleMap.isEmpty()) return@withContext weapons

        // 批量查询图片 URL（每次最多 50 个）
        val imageUrls = mutableMapOf<String, String>() // weaponName -> imageUrl
        titleMap.entries.chunked(50).forEach { chunk ->
            val titles = chunk.joinToString("|") { it.key }
            val encoded = URLEncoder.encode(titles, "UTF-8")
            val url = "$API?action=query&titles=$encoded&prop=imageinfo&iiprop=url&format=json"
            val body = httpGet(url) ?: return@forEach

            val json = SharedJson.parseToJsonElement(body).jsonObject
            val pages = json["query"]?.jsonObject?.get("pages")?.jsonObject ?: return@forEach

            pages.values.forEach { pageValue ->
                val pageObj = pageValue.jsonObject
                val pageTitle = pageObj["title"]?.jsonPrimitive?.content ?: return@forEach
                val imgUrl = pageObj["imageinfo"]?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content

                if (imgUrl != null) {
                    val weaponName = titleMap[pageTitle]
                    if (weaponName != null) {
                        imageUrls[weaponName] = imgUrl
                    }
                }
            }
        }

        // 合并图片 URL
        weapons.map { weapon ->
            weapon.copy(imageUrl = imageUrls[weapon.name])
        }
    }

    private fun httpGet(url: String): String? {
        val request = Request.Builder().url(url).build()
        WikiEngine.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body.string()
        }
    }
}
