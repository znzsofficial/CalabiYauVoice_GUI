package com.nekolaska.calabiyau.data

import data.ApiResult
import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        val subPages: List<SubPage>,// 子页面
        val cooldowns: Map<String, Int> = emptyMap() // 冷却时间（模式名→秒数），仅战术道具
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


    /**
     * 获取武器详情。
     * @param weaponName 武器名（如"警探"）
     */
    suspend fun fetchWeaponDetail(weaponName: String): ApiResult<WeaponDetail> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(weaponName, "UTF-8")
                val url = "$API?action=parse&page=$encoded&prop=wikitext&format=json"

                val (body, isOffline) = OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.WEAPON_DETAIL,
                    key = weaponName
                ) { WikiEngine.safeGet(url) }
                    ?: return@withContext ApiResult.Error("请求失败，且无离线缓存")

                val json = SharedJson.parseToJsonElement(body).jsonObject
                val parseObj = json["parse"]?.jsonObject
                    ?: return@withContext ApiResult.Error("无法获取页面内容")
                val wikitext = parseObj["wikitext"]?.jsonObject?.get("*")
                    ?.jsonPrimitive?.content
                    ?: return@withContext ApiResult.Error("无法获取页面内容")

                val detail = parseWeaponWikitext(weaponName, wikitext)
                    ?: return@withContext ApiResult.Error("未找到武器信息模板")

                ApiResult.Success(detail, isOffline = isOffline)
            } catch (e: Exception) {
                ApiResult.Error("获取武器详情失败: ${e.message}")
            }
        }

    private fun parseWeaponWikitext(name: String, wikitext: String, isTactical: Boolean = false): WeaponDetail? {
        // 尝试多种模板名：{{武器|...}}、{{武器-近战武器|...}}、{{武器-副武器|...}}、{{武器-战术道具|...}}
        val templateNames = listOf("武器-近战武器", "武器-副武器", "武器-战术道具", "武器")
        var weaponParams: Map<String, String> = emptyMap()
        var matchedTemplate = ""
        for (tpl in templateNames) {
            val content = extractTemplate(wikitext, tpl)
            if (content != null) {
                weaponParams = parseTemplateParams(content)
                matchedTemplate = tpl
                break
            }
        }
        if (weaponParams.isEmpty()) return null

        // 判断是否为战术道具
        val isTacticalEquipment = isTactical || matchedTemplate == "武器-战术道具"

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

        // 战术道具：获取冷却时间
        val cooldowns = if (isTacticalEquipment) fetchCooldowns(name) else emptyMap()

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
            subPages = subPages,
            cooldowns = cooldowns
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

        // 格式1：每列一行 "! [style]距离\n| 数值\n| 数值\n| 数值"
        val format1 = Regex("""!\s*(?:style="[^"]*"\|)?\s*(\d+米)\s*\n\|\s*(\d+)\s*\n\|\s*(\d+)\s*\n\|\s*(\d+)""")
        format1.findAll(wikitext).forEach { match ->
            rows.add(DamageRow(match.groupValues[1], match.groupValues[2], match.groupValues[3], match.groupValues[4]))
        }

        // 格式2：行内多列 "| [style] || 10米 || 15米 || 20米" + "| 头部 || 85 || 49 || 36"
        if (rows.isEmpty()) {
            // 提取 ==武器伤害== 区域的第一个 wiki 表格
            val damageSection = wikitext.substringAfter("==武器伤害==", "")
            val tableStart = damageSection.indexOf("{|")
            val tableEnd = damageSection.indexOf("|}", tableStart)
            if (tableStart >= 0 && tableEnd > tableStart) {
                val table = damageSection.substring(tableStart, tableEnd + 2)
                val tableRows = table.split("|-").drop(1) // 跳过表头定义行

                // 找到距离行（包含 "米"）和数据行（头部/上身/上半身/下身/下半身）
                var distances = emptyList<String>()
                val bodyParts = mutableListOf<Pair<String, List<String>>>() // (部位名, 数值列表)

                for (row in tableRows) {
                    val cells = row.split("||").map { cell ->
                        cell.replace(Regex("""^\s*\|?\s*"""), "")
                            .replace(Regex("""style="[^"]*"\|"""), "")
                            .replace(Regex("""\|\}\s*$"""), "")  // 去掉残留的 |}
                            .trim()
                    }.filter { it.isNotBlank() && it != "\n" }

                    if (cells.isEmpty()) continue

                    if (cells.any { it.contains("米") }) {
                        // 距离行
                        distances = cells.filter { it.matches(Regex("""\d+米""")) }
                    } else if (cells.size >= 2) {
                        val label = cells[0]
                        if (label.contains("头") || label.contains("上") || label.contains("下") || label.contains("身")) {
                            bodyParts.add(label to cells.drop(1))
                        }
                    }
                }

                // 按距离组装 DamageRow
                if (distances.isNotEmpty() && bodyParts.isNotEmpty()) {
                    for (i in distances.indices) {
                        val head = bodyParts.firstOrNull { it.first.contains("头") }?.second?.getOrNull(i) ?: "-"
                        val upper = bodyParts.firstOrNull { it.first.contains("上") }?.second?.getOrNull(i) ?: "-"
                        val lower = bodyParts.firstOrNull { it.first.contains("下") }?.second?.getOrNull(i) ?: "-"
                        rows.add(DamageRow(distances[i], head, upper, lower))
                    }
                }
            }
        }

        // 格式3：通用 wiki 表格（副武器/近战武器）
        // 支持两种子格式：
        //   a) 行内多列：| key || value（焚焰者）— 遍历所有表格，用 |+ 标题作为分组
        //   b) 换行键值：! key\n| value（忍锋/战镰，value 可含 ; 和 * 列表）
        if (rows.isEmpty()) {
            val damageSection = wikitext.substringAfter("==武器伤害==", "")
            // 手动提取所有表格（处理缺少 |} 的情况：遇到新 {| 视为上一个表格结束）
            val allTables = extractAllWikiTables(damageSection)

            for (tableText in allTables) {
                // 提取表格标题（|+ 后面的文字）
                val captionMatch = Regex("""\|\+\s*(.+)""").find(tableText)
                val caption = captionMatch?.groupValues?.get(1)?.trim() ?: ""

                // 按 |- 分割行
                val tableRows = tableText.split("|-").drop(1)
                for (row in tableRows) {
                    val trimmed = row.trim()
                    if (trimmed.isEmpty() || trimmed == "|}") continue
                    // 跳过嵌套表格开头
                    if (trimmed.startsWith("{|")) continue

                    // 子格式 a：行内 || 分隔
                    if (trimmed.contains("||")) {
                        val cells = trimmed.split("||").map { cell ->
                            cell.replace(Regex("""^\s*[|!]\s*"""), "")
                                .replace(Regex("""style="[^"]*"\|"""), "")
                                .replace(Regex("""\|\}\s*$"""), "")  // 去掉残留的 |}
                                .trim()
                        }.filter { it.isNotBlank() }
                        if (cells.size >= 2) {
                            // 如果有标题，用 "标题：key" 作为 distance
                            val key = if (caption.isNotBlank()) "$caption：${cells[0]}" else cells[0]
                            rows.add(DamageRow(
                                distance = key,
                                head = cells.drop(1).joinToString("，"),
                                upper = "", lower = ""
                            ))
                        }
                        continue
                    }

                    // 子格式 b：! key\n| value（多行内容）
                    val headerMatch = Regex("""!\s*(?:style="[^"]*"\|)?\s*(.+)""").find(trimmed)
                    if (headerMatch != null) {
                        val key = headerMatch.groupValues[1].trim()
                        // value 是 ! 行之后、| 开头的所有内容
                        val valueStart = trimmed.indexOf('\n', headerMatch.range.last)
                        if (valueStart >= 0) {
                            val rawValue = trimmed.substring(valueStart + 1)
                                .replace(Regex("""^\|\s*"""), "")  // 去掉开头的 |
                                .replace(Regex("""\|?\}\s*$"""), "") // 去掉结尾的 |} 或 }
                                .replace(Regex("""\|\}"""), "")    // 去掉中间的 |}
                                .replace(Regex(""";\s*"""), "")     // ; 分组标记
                                .replace(Regex("""\*\s*"""), "• ") // * → •
                                .replace(Regex("""\n{2,}"""), "\n")
                                .trim()
                            if (rawValue.isNotBlank()) {
                                rows.add(DamageRow(
                                    distance = key,
                                    head = rawValue,
                                    upper = "", lower = ""
                                ))
                            }
                        }
                    }
                }
            }
        }

        // 只取第一组表格（PC端数据），避免重复取移动端数据
        val seen = mutableSetOf<String>()
        return rows.filter { seen.add(it.distance) }
    }

    /**
     * 从 wikitext 中提取所有 wiki 表格。
     * 处理缺少 `|}` 结束标记的情况：遇到新的 `{|` 时视为上一个表格结束。
     */
    private fun extractAllWikiTables(text: String): List<String> {
        val tables = mutableListOf<String>()
        var i = 0
        while (i < text.length - 1) {
            if (text[i] == '{' && text[i + 1] == '|') {
                // 找到表格开始
                val start = i
                i += 2
                var foundEnd = false
                while (i < text.length - 1) {
                    if (text[i] == '|' && text[i + 1] == '}') {
                        // 正常结束
                        tables.add(text.substring(start, i + 2))
                        i += 2
                        foundEnd = true
                        break
                    } else if (text[i] == '{' && text[i + 1] == '|') {
                        // 遇到新表格开始 → 上一个表格缺少 |}，截断
                        tables.add(text.substring(start, i).trimEnd())
                        // 不递增 i，让外层循环重新处理这个新表格
                        foundEnd = true
                        break
                    }
                    i++
                }
                if (!foundEnd && i >= text.length - 1) {
                    // 到达文本末尾仍未找到结束标记
                    tables.add(text.substring(start).trimEnd())
                }
            } else {
                i++
            }
        }
        return tables
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
            val body = WikiEngine.safeGet(url) ?: return null
            val json = SharedJson.parseToJsonElement(body).jsonObject
            json["query"]?.jsonObject?.get("pages")?.jsonObject?.values
                ?.firstOrNull()?.jsonObject?.get("imageinfo")
                ?.let { it as? kotlinx.serialization.json.JsonArray }
                ?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
        } catch (_: Exception) { null }
    }

    /**
     * 从「战术道具冷却时间表」页面获取指定道具的冷却时间。
     * @return 模式名→冷却秒数 的映射，如 {"极限推进模式" to 40, "弦区争夺模式" to 60}
     */
    private fun fetchCooldowns(weaponName: String): Map<String, Int> {
        return try {
            val encoded = URLEncoder.encode("战术道具冷却时间表", "UTF-8")
            val url = "$API?action=parse&page=$encoded&prop=wikitext&format=json"
            val body = WikiEngine.safeGet(url) ?: return emptyMap()
            val json = SharedJson.parseToJsonElement(body).jsonObject
            val wikitext = json["parse"]?.jsonObject?.get("wikitext")
                ?.jsonObject?.get("*")?.jsonPrimitive?.content ?: return emptyMap()
            parseCooldownTable(wikitext, weaponName)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * 解析冷却时间表 wikitext，提取指定道具在各模式下的冷却时间。
     *
     * 表格格式：
     * ```
     * {| class="klbqtable"
     * |+ 极限推进模式
     * |-
     * ! 道具 !! 冷却时间/秒
     * |-
     * | 手雷 || 40
     * ...
     * |}
     * ```
     */
    private fun parseCooldownTable(wikitext: String, weaponName: String): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        // 按 {| ... |} 分割出每个表格
        val tableRegex = Regex("""\{\|[\s\S]*?\|\}""")
        for (tableMatch in tableRegex.findAll(wikitext)) {
            val table = tableMatch.value
            // 提取模式名（|+ 后面的文字）
            val captionMatch = Regex("""\|\+\s*(.+)""").find(table)
            val modeName = captionMatch?.groupValues?.get(1)?.trim() ?: continue

            // 按 |- 分割行
            val rows = table.split("|-").drop(1) // 跳过表头定义
            for (row in rows) {
                val trimmed = row.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("|}") || trimmed.startsWith("!")) continue

                // 解析 "| 道具名 || 冷却秒数" 或 "| 道具名\n| 冷却秒数"
                val cells = if (trimmed.contains("||")) {
                    trimmed.split("||").map { it.replace(Regex("""^\s*\|\s*"""), "").trim() }
                } else {
                    trimmed.split("\n")
                        .filter { it.trimStart().startsWith("|") }
                        .map { it.replace(Regex("""^\s*\|\s*"""), "").trim() }
                }

                if (cells.size >= 2) {
                    val itemName = cells[0]
                        .replace(Regex("""\[\[([^|\]]*\|)?([^\]]+)\]\]"""), "$2") // [[链接|显示名]] → 显示名
                        .trim()
                    val cooldownStr = cells[1].trim()
                    val cooldown = cooldownStr.toIntOrNull()
                    if (cooldown != null && itemName == weaponName) {
                        result[modeName] = cooldown
                    }
                }
            }
        }
        return result
    }

}
