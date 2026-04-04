package com.nekolaska.calabiyau.data

import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.net.URLEncoder

/**
 * 武器详情 API（Android）。
 *
 * 通过 MediaWiki parse API 获取武器页面的 wikitext，
 * 解析 `{{武器|...}}` 和 `{{武器伤害|...}}` 模板参数。
 */
object WeaponDetailApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    /** 武器详情 */
    data class WeaponDetail(
        val name: String,
        val user: String,           // 使用者
        val type: String,           // 类型（自动步枪等）
        val obtainMethod: String,   // 获得方式
        val description: String,    // 介绍
        val fireRate: String,       // 射速
        val mobileFireRate: String, // 移动端射速
        val aimSpeed: String,       // 瞄准速度
        val spreadControl: String,  // 散射控制
        val recoilControl: String,  // 后坐力控制
        val reloadSpeed: String,    // 装填速度
        val moveSpeedChange: String,// 移速变化
        val stringDamage: String,   // 弦化伤害
        val maxAmmo: String,        // 最大备弹数
        val magCapacity: String,    // 弹匣容量
        val mobileMagCapacity: String, // 移动端弹匣容量
        val secondaryAttack: String,// 辅助攻击
        val fireMode: String,       // 开火模式
        val magnification: String,  // 放大倍率
        val damageTable: List<DamageRow>, // 伤害表
        val baseDamage: String,     // 基础伤害
        val headMultiplier: String, // 头部倍率
        val upperMultiplier: String,// 上肢倍率
        val lowerMultiplier: String,// 下肢倍率
        val imageUrl: String?,      // 武器图片
        val subPages: List<SubPage> // 子页面
    )

    data class DamageRow(
        val distance: String,   // 距离（10米、20米等）
        val head: String,
        val upper: String,
        val lower: String
    )

    data class SubPage(
        val title: String,
        val displayName: String,
        val wikiUrl: String
    )

    sealed interface ApiResult<out T> {
        data class Success<T>(val value: T) : ApiResult<T>
        data class Error(val message: String) : ApiResult<Nothing>
    }

    /**
     * 获取武器详情。
     * @param weaponName 武器名（如"警探"）
     */
    suspend fun fetchWeaponDetail(weaponName: String): ApiResult<WeaponDetail> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(weaponName, "UTF-8")
                val url = "$API?action=parse&page=$encoded&prop=wikitext&format=json"
                val body = httpGet(url) ?: return@withContext ApiResult.Error("请求失败")

                val json = SharedJson.parseToJsonElement(body).jsonObject
                val parseObj = json["parse"]?.jsonObject
                    ?: return@withContext ApiResult.Error("无法获取页面内容")
                val wikitext = parseObj["wikitext"]?.jsonObject?.get("*")
                    ?.jsonPrimitive?.content
                    ?: return@withContext ApiResult.Error("无法获取页面内容")

                val detail = parseWeaponWikitext(weaponName, wikitext)
                    ?: return@withContext ApiResult.Error("未找到武器信息模板")

                ApiResult.Success(detail)
            } catch (e: Exception) {
                ApiResult.Error("获取武器详情失败: ${e.message}")
            }
        }

    private fun parseWeaponWikitext(name: String, wikitext: String): WeaponDetail? {
        // 尝试多种模板名：{{武器|...}}、{{武器-近战武器|...}}、{{武器-副武器|...}}、{{武器-战术道具|...}}
        val templateNames = listOf("武器-近战武器", "武器-副武器", "武器-战术道具", "武器")
        var weaponParams: Map<String, String> = emptyMap()
        for (tpl in templateNames) {
            val content = extractTemplate(wikitext, tpl)
            if (content != null) {
                weaponParams = parseTemplateParams(content)
                break
            }
        }
        if (weaponParams.isEmpty()) return null

        // 解析 {{武器伤害|...}} 模板（主武器专用）
        val damageContent = extractTemplate(wikitext, "武器伤害")
        val damageParams = if (damageContent != null) parseTemplateParams(damageContent) else emptyMap()

        // 构建伤害表
        val damageTable = if (damageParams.isNotEmpty()) {
            // 主武器：从 {{武器伤害}} 模板
            val distances = listOf("10", "20", "30", "40", "50")
            distances.mapNotNull { d ->
                val head = damageParams["${d}米头部"]
                val upper = damageParams["${d}米上肢"]
                val lower = damageParams["${d}米下肢"]
                if (head != null || upper != null || lower != null) {
                    DamageRow("${d}米", head ?: "-", upper ?: "-", lower ?: "-")
                } else null
            }
        } else {
            // 副武器/近战武器：从 wiki 表格中解析
            parseWikiTableDamage(wikitext)
        }

        // 解析子页面
        val navContent = extractTemplate(wikitext, "页顶导航")
        val subPages = if (navContent != null) parseSubPages(navContent) else emptyList()

        // 获取武器图片
        val user = weaponParams["使用者"] ?: ""
        val explicitImage = weaponParams["武器图片"] ?: ""
        val imageUrl = when {
            explicitImage.isNotBlank() -> fetchImageUrl(explicitImage)
            user.isNotBlank() -> fetchImageUrl("${user}-weapon.png")
            else -> fetchImageUrl("武器-${name}.png")
        }

        // 清理 description 中的 wiki 标记
        val rawDesc = weaponParams["介绍"] ?: weaponParams["武器介绍"] ?: ""
        val cleanDesc = rawDesc
            .replace(Regex("""\{\{黑幕\|([^}]*)\}\}"""), "$1")  // {{黑幕|text}} → text
            .replace(Regex("""\{\{#info:[^}]*\}\}"""), "")           // {{#info:...}}
            .replace(Regex("""<br\s*/?>\s*"""), "\n")                  // <br /> → 换行
            .replace(Regex("""<[^>]+>"""), "")                            // 其他 HTML 标签
            .replace(Regex("""\n{3,}"""), "\n\n")                       // 多余空行
            .trim()

        return WeaponDetail(
            name = name,
            user = user,
            type = weaponParams["类型"] ?: weaponParams["武器种类"] ?: "",
            obtainMethod = weaponParams["获得方式"] ?: weaponParams["获取方式"] ?: "",
            description = cleanDesc,
            fireRate = weaponParams["射速"] ?: "",
            mobileFireRate = weaponParams["移动端射速"] ?: "",
            aimSpeed = weaponParams["瞄准速度"] ?: "",
            spreadControl = weaponParams["散射控制"] ?: "",
            recoilControl = weaponParams["后坐力控制"] ?: "",
            reloadSpeed = weaponParams["装填速度"] ?: "",
            moveSpeedChange = weaponParams["移速变化"] ?: "",
            stringDamage = weaponParams["弦化伤害"] ?: "",
            maxAmmo = weaponParams["最大备弹数"] ?: "",
            magCapacity = weaponParams["弹匣容量"] ?: "",
            mobileMagCapacity = weaponParams["移动端弹匣容量"] ?: "",
            secondaryAttack = weaponParams["辅助攻击"] ?: "",
            fireMode = weaponParams["开火模式"] ?: "",
            magnification = weaponParams["放大倍率"] ?: "",
            damageTable = damageTable,
            baseDamage = damageParams["基础伤害"] ?: "",
            headMultiplier = damageParams["头部倍率"] ?: "",
            upperMultiplier = damageParams["上肢倍率"] ?: "",
            lowerMultiplier = damageParams["下肢倍率"] ?: "",
            imageUrl = imageUrl,
            subPages = subPages
        )
    }

    /**
     * 从 wiki 表格中解析伤害数据（副武器/近战武器）。
     * 表格格式：
     * ```
     * ! 10米
     * | 21 | 17 | 11
     * ```
     */
    /**
     * 从 wiki 表格中解析伤害数据（副武器/近战武器）。
     * 副武器表格格式：`! style="..."|10米\n| 21\n| 17\n| 11`
     * 近战武器格式不同（文字描述），此方法只处理数值表格。
     */
    private fun parseWikiTableDamage(wikitext: String): List<DamageRow> {
        val rows = mutableListOf<DamageRow>()
        // 匹配 "! [可选style]距离\n| 数值\n| 数值\n| 数值" 格式
        val rowRegex = Regex("""!\s*(?:style="[^"]*"\|)?\s*(\d+米)\s*\n\|\s*(\d+)\s*\n\|\s*(\d+)\s*\n\|\s*(\d+)""")
        rowRegex.findAll(wikitext).forEach { match ->
            rows.add(
                DamageRow(
                    distance = match.groupValues[1],
                    head = match.groupValues[2],
                    upper = match.groupValues[3],
                    lower = match.groupValues[4]
                )
            )
        }
        // 只取第一组表格（PC端数据），避免重复取移动端数据
        val seen = mutableSetOf<String>()
        return rows.filter { seen.add(it.distance) }
    }

    /** 提取指定名称的模板内容（处理嵌套大括号）。
     *  模板名后必须紧跟 `|`、`\n` 或 `}}` 以避免前缀误匹配。 */
    private fun extractTemplate(wikitext: String, templateName: String): String? {
        val startMarker = "{{$templateName"
        var searchFrom = 0
        while (true) {
            val startIdx = wikitext.indexOf(startMarker, searchFrom)
            if (startIdx == -1) return null

            // 检查模板名后的字符，确保是精确匹配而非前缀
            val afterName = startIdx + startMarker.length
            if (afterName < wikitext.length) {
                val nextChar = wikitext[afterName]
                if (nextChar != '|' && nextChar != '\n' && nextChar != '\r' && nextChar != '}') {
                    searchFrom = afterName
                    continue
                }
            }

            var depth = 0
            var i = startIdx
            while (i < wikitext.length - 1) {
                if (wikitext[i] == '{' && wikitext[i + 1] == '{') {
                    depth++; i += 2
                } else if (wikitext[i] == '}' && wikitext[i + 1] == '}') {
                    depth--
                    if (depth == 0) {
                        return wikitext.substring(afterName, i).trimStart()
                    }
                    i += 2
                } else {
                    i++
                }
            }
            return null
        }
    }

    /** 解析模板参数 */
    private fun parseTemplateParams(content: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        splitTemplateParams(content).forEach { part ->
            val eqIdx = part.indexOf('=')
            if (eqIdx > 0) {
                val key = part.substring(0, eqIdx).trim()
                val value = part.substring(eqIdx + 1).trim()
                if (key.isNotEmpty()) params[key] = value
            }
        }
        return params
    }

    /** 按 | 分割模板参数，跳过嵌套 {{...}} 内的 | */
    private fun splitTemplateParams(content: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        for (i in content.indices) {
            val c = content[i]
            if (i < content.length - 1 && c == '{' && content[i + 1] == '{') {
                depth++; current.append(c)
            } else if (i < content.length - 1 && c == '}' && content[i + 1] == '}') {
                depth--; current.append(c)
            } else if (c == '|' && depth == 0) {
                parts.add(current.toString()); current.clear()
            } else {
                current.append(c)
            }
        }
        if (current.isNotEmpty()) parts.add(current.toString())
        return parts
    }

    /** 解析子页面 */
    private fun parseSubPages(navContent: String): List<SubPage> {
        val pages = mutableListOf<SubPage>()
        navContent.split("|").forEach { part ->
            val eqIdx = part.indexOf('=')
            if (eqIdx > 0) {
                val pageTitle = part.substring(0, eqIdx).trim()
                val displayName = part.substring(eqIdx + 1).trim()
                if (pageTitle.isNotBlank() && displayName.isNotBlank()) {
                    val enc = URLEncoder.encode(pageTitle, "UTF-8").replace("+", "%20")
                    pages.add(SubPage(pageTitle, displayName, "https://wiki.biligame.com/klbq/$enc"))
                }
            }
        }
        return pages
    }

    /** 获取图片 URL */
    private fun fetchImageUrl(fileName: String): String? {
        return try {
            val fileTitle = URLEncoder.encode("文件:$fileName", "UTF-8")
            val url = "$API?action=query&titles=$fileTitle&prop=imageinfo&iiprop=url&format=json"
            val body = httpGet(url) ?: return null
            val json = SharedJson.parseToJsonElement(body).jsonObject
            json["query"]?.jsonObject?.get("pages")?.jsonObject?.values
                ?.firstOrNull()?.jsonObject?.get("imageinfo")
                ?.let { it as? kotlinx.serialization.json.JsonArray }
                ?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
        } catch (_: Exception) { null }
    }

    private fun httpGet(url: String): String? {
        val request = Request.Builder().url(url).build()
        WikiEngine.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body.string()
        }
    }
}
