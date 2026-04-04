package com.nekolaska.calabiyau.data

import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.net.URLEncoder

/**
 * 角色时装筛选 API（Android）。
 *
 * 通过 MediaWiki parse API 渲染 `{{#invoke:角色|角色时装筛选}}` Lua 模块，
 * 从返回的 HTML 中提取时装数据（data-param 属性 + img 标签）。
 */
object CostumeFilterApi {

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
        val thumbnailUrl: String?,  // 缩略图 URL（256x256）
        val fullImageUrl: String?   // 原图 URL
    )

    sealed interface ApiResult<out T> {
        data class Success<T>(val value: T) : ApiResult<T>
        data class Error(val message: String) : ApiResult<Nothing>
    }

    /**
     * 获取所有时装数据。
     */
    suspend fun fetchAllCostumes(): ApiResult<List<CostumeInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val text = URLEncoder.encode("{{#invoke:角色|角色时装筛选}}", "UTF-8")
                val url = "$API?action=parse&text=$text&prop=text&format=json"
                val body = httpGet(url) ?: return@withContext ApiResult.Error("请求失败")

                val json = SharedJson.parseToJsonElement(body).jsonObject
                val html = json["parse"]?.jsonObject?.get("text")
                    ?.jsonObject?.get("*")?.jsonPrimitive?.content
                    ?: return@withContext ApiResult.Error("无法获取时装数据")

                val costumes = parseCostumeHtml(html)
                if (costumes.isEmpty()) {
                    ApiResult.Error("未找到时装数据")
                } else {
                    ApiResult.Success(costumes)
                }
            } catch (e: Exception) {
                ApiResult.Error("获取时装数据失败: ${e.message}")
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
        val costumes = mutableListOf<CostumeInfo>()

        // 匹配 data-param 行
        val paramRegex = Regex(
            """data-param1="([^"]*?)"\s+data-param2="([^"]*?)"\s+data-param3="([^"]*?)"\s+data-param4="([^"]*?)"\s+data-param5="([^"]*?)"""")

        // 匹配图片 src 和可选的 srcset
        val imgSrcRegex = Regex("""<img[^>]*\bsrc="([^"]*)"[^>]*>""")
        val srcsetRegex = Regex("""\bsrcset="([^"]*?)\s""")
        // 匹配时装名（img 标签后的 <br/> 后面的文字）
        val nameRegex = Regex("""/>\s*<br\s*/?\s*>\s*([^<|]+)""")

        val paramMatches = paramRegex.findAll(html).toList()

        for (paramMatch in paramMatches) {
            val character = paramMatch.groupValues[1]
            val qualityStr = paramMatch.groupValues[2]
            val sourcesStr = paramMatch.groupValues[3]
            val crystalCost = paramMatch.groupValues[4]
            val baseCost = paramMatch.groupValues[5]

            // 从 paramMatch 位置向后搜索图片和名称
            val afterParam = html.substring(
                paramMatch.range.last,
                minOf(paramMatch.range.last + 2000, html.length)
            )

            val imgMatch = imgSrcRegex.find(afterParam)
            val thumbnailUrl = imgMatch?.groupValues?.get(1)
            // 尝试从 img 标签中提取 srcset 作为高清图
            val fullImageUrl = imgMatch?.let { srcsetRegex.find(it.value)?.groupValues?.get(1) }
            val nameMatch = nameRegex.find(afterParam)
            val costumeName = nameMatch?.groupValues?.get(1)?.trim() ?: "$character：未知"

            costumes.add(
                CostumeInfo(
                    name = costumeName,
                    character = character,
                    quality = Quality.fromLevel(qualityStr),
                    sources = sourcesStr.split(",").map { it.trim() }.filter { it.isNotBlank() },
                    crystalCost = crystalCost,
                    baseCost = baseCost,
                    thumbnailUrl = thumbnailUrl,
                    fullImageUrl = fullImageUrl
                )
            )
        }

        return costumes
    }

    private fun httpGet(url: String): String? {
        val request = Request.Builder().url(url).build()
        WikiEngine.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body.string()
        }
    }
}
