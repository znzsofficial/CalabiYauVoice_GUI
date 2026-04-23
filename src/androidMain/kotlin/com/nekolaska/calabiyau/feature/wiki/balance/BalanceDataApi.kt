package com.nekolaska.calabiyau.feature.wiki.balance

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 官网平衡数据 API（Android）。
 *
 * 数据来源：https://klbq.idreamsky.com/balanceData
 * 后端 API：https://klbq-prod-www.idreamsky.com
 *
 * 两步获取：
 * 1. GET  /api/pages/KLBQ_BALANCE/index → 设置选项（模式/地图/段位/赛季/角色列表）
 * 2. POST /api/common/ide              → 平衡数据（胜率/选取率/KD/伤害/评分）
 */
object BalanceDataApi {

    init {
        MemoryCacheRegistry.register("BalanceDataApi", ::clearMemoryCache)
    }

    private const val BASE_URL = "https://klbq-prod-www.idreamsky.com"
    private const val CHART_ID = "338985"
    private const val IDE_TOKEN = "b7FM3m"

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /** 独立的 OkHttpClient，不使用 Wiki 的 UA/Referer */
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = SharedJson

    // ── 数据模型 ──



    /** 筛选选项（code + 显示名） */
    data class FilterOption(val code: String, val name: String)

    /** 角色元信息 */
    data class CharacterMeta(
        val code: String,
        val name: String,
        val positionCode: String,
        val campCode: Int,
        val imageUrl: String
    )

    /** 职业元信息 */
    data class PositionMeta(
        val code: String,
        val name: String,
        val imageUrl: String
    )

    /** 设置选项集合 */
    data class BalanceSettings(
        val modes: List<FilterOption>,
        val maps: List<FilterOption>,
        val ranks: List<FilterOption>,
        val seasons: List<FilterOption>,
        val positions: List<PositionMeta>,
        val characters: List<CharacterMeta>
    )

    /** 单个角色的平衡数据 */
    data class HeroBalanceData(
        val id: Int,
        val heroName: String,
        val winRate: Double,
        val selectRate: Double,
        val kd: Double,
        val damageAve: Double,
        val score: Double
    )

    /** 平衡数据结果（进攻方/防守方） */
    data class BalanceResult(
        val attackers: List<HeroBalanceData>,
        val defenders: List<HeroBalanceData>
    )

    // ── 缓存 ──

    private var cachedSettings: BalanceSettings? = null

    fun clearMemoryCache() { cachedSettings = null }

    // ── 公共 API ──

    /** 获取筛选设置选项 */
    suspend fun fetchSettings(forceRefresh: Boolean = false): ApiResult<BalanceSettings> {
        if (!forceRefresh) cachedSettings?.let { return ApiResult.Success(it) }

        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/api/pages/KLBQ_BALANCE/index"
                val request = Request.Builder().url(url).get().build()
                val body = client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext ApiResult.Error(
                        "HTTP ${resp.code}",
                        kind = when (resp.code) {
                            403, 429, 503 -> ErrorKind.CDN_BLOCKED
                            404 -> ErrorKind.NOT_FOUND
                            else -> ErrorKind.NETWORK
                        }
                    )
                    resp.body.string()
                }

                val root = json.parseToJsonElement(body).jsonObject
                if (root["code"]?.jsonPrimitive?.intOrNull != 0) {
                    return@withContext ApiResult.Error(
                        root["msg"]?.jsonPrimitive?.contentOrNull ?: "未知错误",
                        kind = ErrorKind.PARSE
                    )
                }

                val value = root["data"]!!.jsonObject["value"]!!.jsonObject
                val setting = value["setting"]!!.jsonObject
                val roleList = value["role_list"]!!.jsonObject

                // 解析筛选选项
                fun parseOptions(arr: JsonArray): List<FilterOption> = arr.map { item ->
                    val content = json.parseToJsonElement(
                        item.jsonObject["content"]!!.jsonPrimitive.content
                    ).jsonObject
                    FilterOption(
                        code = content["code"]!!.jsonPrimitive.content,
                        name = content["name"]!!.jsonPrimitive.content
                    )
                }

                val modes = parseOptions(setting["mode"]!!.jsonArray)
                val maps = parseOptions(setting["map"]!!.jsonArray)
                val ranks = parseOptions(setting["rank"]!!.jsonArray)
                val seasons = parseOptions(setting["season"]!!.jsonArray)

                // 解析职业
                val positions = roleList["position"]!!.jsonArray.map { item ->
                    val content = json.parseToJsonElement(
                        item.jsonObject["content"]!!.jsonPrimitive.content
                    ).jsonObject
                    PositionMeta(
                        code = content["position_code"]!!.jsonPrimitive.content,
                        name = content["position_name"]!!.jsonPrimitive.content,
                        imageUrl = content["position_image"]!!.jsonPrimitive.content
                    )
                }

                // 解析角色列表
                val characters = roleList["role_list"]!!.jsonArray.map { item ->
                    val content = json.parseToJsonElement(
                        item.jsonObject["content"]!!.jsonPrimitive.content
                    ).jsonObject
                    CharacterMeta(
                        code = content["character_code"]!!.jsonPrimitive.content,
                        name = content["character_name"]!!.jsonPrimitive.content,
                        positionCode = content["position_code"]!!.jsonPrimitive.content,
                        campCode = content["camp_code"]!!.jsonPrimitive.intOrNull ?: 0,
                        imageUrl = content["character_image"]!!.jsonPrimitive.content
                    )
                }

                val result = BalanceSettings(modes, maps, ranks, seasons, positions, characters)
                cachedSettings = result
                ApiResult.Success(result)
            } catch (e: Exception) {
                ApiResult.Error(e.message ?: "网络错误", kind = e.toErrorKind())
            }
        }
    }

    /** 获取平衡数据 */
    suspend fun fetchBalanceData(
        modeCode: String,
        mapCode: String,
        rankCodes: List<String>,
        season1Code: String,
        season2Code: String = "0"
    ): ApiResult<BalanceResult> = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject {
                put("iChartId", CHART_ID)
                put("iSubChartId", CHART_ID)
                put("sIdeToken", IDE_TOKEN)
                put("mode", modeCode)
                put("map", mapCode)
                putJsonArray("rank") { rankCodes.forEach { add(it) } }
                put("season1", season1Code)
                put("season2", season2Code)
            }

            val request = Request.Builder()
                .url("$BASE_URL/api/common/ide")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()

            val body = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext ApiResult.Error(
                    "HTTP ${resp.code}",
                    kind = when (resp.code) {
                        403, 429, 503 -> ErrorKind.CDN_BLOCKED
                        404 -> ErrorKind.NOT_FOUND
                        else -> ErrorKind.NETWORK
                    }
                )
                resp.body.string()
            }

            val root = json.parseToJsonElement(body).jsonObject
            val jData = root["jData"]?.jsonObject
                ?: return@withContext ApiResult.Error(
                    "返回数据格式异常",
                    kind = ErrorKind.PARSE
                )

            val iRet = jData["iRet"]?.jsonPrimitive?.contentOrNull
            if (iRet != "0") {
                return@withContext ApiResult.Error(
                    jData["sMsg"]?.jsonPrimitive?.contentOrNull ?: "查询失败",
                    kind = ErrorKind.NOT_FOUND
                )
            }

            val data1 = jData["data1"]!!.jsonObject

            fun parseHeroList(arr: JsonArray): List<HeroBalanceData> = arr.mapNotNull { elem ->
                val obj = elem.jsonObject
                HeroBalanceData(
                    id = obj["id"]!!.jsonPrimitive.int,
                    heroName = obj["heroName"]!!.jsonPrimitive.content,
                    winRate = obj["winRate"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    selectRate = obj["selectRate"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    kd = obj["kd"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    damageAve = obj["damageAve"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    score = obj["score"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                )
            }

            val side1 = data1["side1"]?.jsonArray?.let { parseHeroList(it) } ?: emptyList()
            val side2 = data1["side2"]?.jsonArray?.let { parseHeroList(it) } ?: emptyList()

            ApiResult.Success(BalanceResult(attackers = side1, defenders = side2))
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "网络错误", kind = e.toErrorKind())
        }
    }
}
