package com.nekolaska.calabiyau.data

import data.ApiResult
import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * 武器外观筛选 API（Android）。
 *
 * 通过 MediaWiki parse API 渲染 `{{#invoke:武器|武器外观筛选}}` Lua 模块，
 * 从返回的 HTML 中提取武器外观数据（data-param 属性 + img 标签）。
 */
object WeaponSkinFilterApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    /** 内存缓存（进程生命周期内有效，避免重复请求） */
    @Volatile
    private var cachedSkins: List<WeaponSkinInfo>? = null

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
        val thumbnailUrl: String?,  // 缩略图 URL
        val fullImageUrl: String?   // 原图 URL
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
                val body = WikiEngine.safeGet(url) ?: return@withContext ApiResult.Error("请求失败")

                val json = SharedJson.parseToJsonElement(body).jsonObject
                val html = json["parse"]?.jsonObject?.get("text")
                    ?.jsonObject?.get("*")?.jsonPrimitive?.content
                    ?: return@withContext ApiResult.Error("无法获取武器外观数据")

                val skins = parseWeaponSkinHtml(html)
                if (skins.isEmpty()) {
                    ApiResult.Error("未找到武器外观数据")
                } else {
                    cachedSkins = skins
                    ApiResult.Success(skins)
                }
            } catch (e: Exception) {
                ApiResult.Error("获取武器外观数据失败: ${e.message}")
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
        val skins = mutableListOf<WeaponSkinInfo>()

        val paramRegex = Regex(
            """data-param1="([^"]*?)"\s+data-param2="([^"]*?)"\s+data-param3="([^"]*?)"\s+data-param4="([^"]*?)"\s+data-param5="([^"]*?)""""
        )

        val imgSrcRegex = Regex("""<img[^>]*\bsrc="([^"]*)"[^>]*>""")
        val srcsetRegex = Regex("""\bsrcset="([^"]*?)\s""")
        val nameRegex = Regex("""/>\s*<br\s*/?\s*>\s*(?:<a[^>]*>([^<]+)</a>)?([^<|]*)""")

        val paramMatches = paramRegex.findAll(html).toList()

        for (paramMatch in paramMatches) {
            val weapon = paramMatch.groupValues[1]
            val qualityStr = paramMatch.groupValues[2]
            val sourcesStr = paramMatch.groupValues[3]
            val crystalCost = paramMatch.groupValues[4]
            val baseCost = paramMatch.groupValues[5]

            val afterParam = html.substring(
                paramMatch.range.last,
                minOf(paramMatch.range.last + 3000, html.length)
            )

            val imgMatch = imgSrcRegex.find(afterParam)
            val thumbnailUrl = imgMatch?.groupValues?.get(1)
            val fullImageUrl = imgMatch?.let { srcsetRegex.find(it.value)?.groupValues?.get(1) }

            // 提取外观名
            val nameMatch = nameRegex.find(afterParam)
            val rawName = buildString {
                nameMatch?.groupValues?.get(1)?.let { if (it.isNotBlank()) append(it) }
                nameMatch?.groupValues?.get(2)?.let { append(it) }
            }.trim()
            val skinName = rawName.ifBlank { "$weapon：未知" }

            skins.add(
                WeaponSkinInfo(
                    name = skinName,
                    weapon = weapon,
                    quality = Quality.fromLevel(qualityStr),
                    sources = sourcesStr.split(",").map { it.trim() }.filter { it.isNotBlank() },
                    crystalCost = crystalCost.replace("无", ""),
                    baseCost = baseCost.replace("无", ""),
                    thumbnailUrl = thumbnailUrl,
                    fullImageUrl = fullImageUrl
                )
            )
        }

        return skins
    }

}
