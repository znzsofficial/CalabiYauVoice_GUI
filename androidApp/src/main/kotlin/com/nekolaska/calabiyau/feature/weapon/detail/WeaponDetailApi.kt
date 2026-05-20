package com.nekolaska.calabiyau.feature.weapon.detail

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
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
    suspend fun fetchWeaponDetail(
        weaponName: String,
        forceRefresh: Boolean = false
    ): ApiResult<WeaponDetail> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(weaponName, "UTF-8")
                val url = "$API?action=parse&page=$encoded&prop=wikitext|text&format=json"

                val result = OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.WEAPON_DETAIL,
                    key = weaponName,
                    forceRefresh = forceRefresh
                ) { WikiEngine.safeGet(url) }
                    ?: return@withContext ApiResult.Error(
                        "请求失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )
                val body = result.payload

                val json = SharedJson.parseToJsonElement(body).jsonObject
                val parseObj = json["parse"]?.jsonObject
                    ?: return@withContext ApiResult.Error(
                        "无法获取页面内容",
                        kind = ErrorKind.PARSE
                    )
                val wikitext = parseObj["wikitext"]?.jsonObject?.get("*")
                    ?.jsonPrimitive?.content
                    ?: return@withContext ApiResult.Error(
                        "无法获取页面内容",
                        kind = ErrorKind.PARSE
                    )
                val renderedHtml = parseObj["text"]?.jsonObject?.get("*")
                    ?.jsonPrimitive?.content
                    .orEmpty()

                val imageUrl = resolveWeaponImageUrl(
                    weaponName = weaponName,
                    wikitext = wikitext,
                    forceRefresh = forceRefresh
                )

                val detail = parseWeaponWikitext(
                    weaponName,
                    wikitext,
                    renderedHtml = renderedHtml,
                    resolvedImageUrl = imageUrl
                )
                    ?: return@withContext ApiResult.Error(
                        "未找到武器信息模板",
                        kind = ErrorKind.NOT_FOUND
                    )

                ApiResult.Success(
                    detail,
                    isOffline = result.isFromCache,
                    cacheAgeMs = result.ageMs
                )
            } catch (e: Exception) {
                ApiResult.Error("获取武器详情失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    private fun parseWeaponWikitext(
        name: String,
        wikitext: String,
        isTactical: Boolean = false,
        renderedHtml: String = "",
        resolvedImageUrl: String? = null
    ): WeaponDetail? {
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
            // 副武器/近战武器：从渲染 HTML 表格中解析
            parseWeaponDamageFromHtml(renderedHtml)
        }

        // 解析子页面
        val navContent = extractTemplate(wikitext, "页顶导航")
        val subPages = if (navContent != null) parseSubPages(navContent) else emptyList()

        // 获取武器图片
        val user = weaponParams["使用者"] ?: ""
        val imageUrl = resolvedImageUrl

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

    private suspend fun resolveWeaponImageUrl(
        weaponName: String,
        wikitext: String,
        forceRefresh: Boolean
    ): String? {
        val fileName = extractWeaponImageFileName(weaponName, wikitext) ?: return null
        val cacheResult = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.WEAPON_DETAIL,
            key = "image_$weaponName",
            forceRefresh = forceRefresh
        ) { fetchImageUrl(fileName) }
        return cacheResult?.payload?.takeIf { it.isNotBlank() }
    }

    private fun extractWeaponImageFileName(weaponName: String, wikitext: String): String? {
        val templateNames = listOf("武器-近战武器", "武器-副武器", "武器-战术道具", "武器")
        for (tpl in templateNames) {
            val content = extractTemplate(wikitext, tpl) ?: continue
            val params = parseTemplateParams(content)
            val explicitImage = params["武器图片"].orEmpty()
            val user = params["使用者"].orEmpty()
            return when {
                explicitImage.isNotBlank() -> explicitImage
                user.isNotBlank() -> "${user}-weapon.png"
                else -> "武器-${weaponName}.png"
            }
        }
        return null
    }

    private fun parseWeaponDamageFromHtml(html: String): List<DamageRow> {
        if (html.isBlank()) return emptyList()
        val document = Jsoup.parse(html)
        val headline = document.getElementById("武器伤害") ?: return emptyList()
        val sectionStart = headline.parent()?.takeIf { it.tagName().matches(Regex("h[1-6]")) } ?: headline
        val sectionElements = generateSequence(sectionStart.nextElementSibling()) { it.nextElementSibling() }
            .takeWhile { !it.tagName().matches(Regex("h[1-6]")) }
            .toList()

        return sectionElements
            .filter { it.tagName() == "table" && it.hasClass("klbqtable") }
            .flatMap { table -> parseDamageTable(table) }
            .distinctBy { it.distance }
    }

    private fun parseDamageTable(table: Element): List<DamageRow> {
        val caption = table.selectFirst("caption")?.text()?.trim().orEmpty()
        val rows = table.select("tr")

        if (rows.isEmpty()) {
            return caption
                .takeIf { it.startsWith("补充：") }
                ?.removePrefix("补充：")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { listOf(DamageRow("补充", it, "", "")) }
                ?: emptyList()
        }

        val hasDistanceHeader = rows.any { row ->
            row.select("th").any { cell -> cell.text().trim().matches(Regex("\\d+米")) }
        }

        return if (hasDistanceHeader) {
            parseDistanceDamageTable(table, caption)
        } else {
            parseSimpleDamageTable(table, caption)
        }
    }

    private fun parseDistanceDamageTable(table: Element, caption: String): List<DamageRow> {
        val captionPrefix = when {
            caption.contains("移动端") -> "移动端"
            caption.isNotBlank() -> caption
            else -> ""
        }

        return table.select("tr").mapNotNull { row ->
            val header = row.selectFirst("th")?.text()?.trim().orEmpty()
            val valueCells = row.select("td")
            if (!header.matches(Regex("\\d+米")) || valueCells.size < 3) return@mapNotNull null

            val distanceLabel = if (captionPrefix.isNotBlank()) "$captionPrefix·$header" else header
            DamageRow(
                distance = distanceLabel,
                head = valueCells.getOrNull(0)?.text()?.trim().orEmpty(),
                upper = valueCells.getOrNull(1)?.text()?.trim().orEmpty(),
                lower = valueCells.getOrNull(2)?.text()?.trim().orEmpty()
            )
        }
    }

    private fun parseSimpleDamageTable(table: Element, caption: String): List<DamageRow> {
        val tablePrefix = caption
            .removePrefix("射击目标为靶场人形靶")
            .trim()
            .ifBlank { caption }

        val parsedRows = table.select("tr").flatMap { row ->
            val cells = row.select("> th, > td")
            if (cells.size < 2) return@flatMap emptyList()

            val header = cells.firstOrNull()?.text()?.trim().orEmpty()
            val valueCell = cells.drop(1).firstOrNull() ?: return@flatMap emptyList()
            if (header.isBlank()) return@flatMap emptyList()

            when {
                // 近战武器 / 特殊副武器嵌套列表结构
                valueCell.select("> dl > dt").isNotEmpty() && valueCell.select("> ul > li").isNotEmpty() -> {
                    val expandedRows = mutableListOf<DamageRow>()
                    var currentGroup = ""

                    valueCell.children().forEach { child ->
                        when {
                            child.tagName().equals("dl", ignoreCase = true) -> {
                                currentGroup = child.selectFirst("dt")
                                    ?.text()
                                    ?.trim()
                                    ?.removeSuffix("：")
                                    .orEmpty()
                            }

                            child.tagName().equals("ul", ignoreCase = true) -> {
                                child.select("> li")
                                    .map { it.text().trim() }
                                    .filter { it.isNotBlank() }
                                    .forEach { itemText ->
                                        val parts = itemText.split('：', ':', limit = 2)
                                        val actionName = parts.getOrNull(0)?.trim().orEmpty()
                                        val damageValue = parts.getOrNull(1)?.trim().orEmpty()
                                        val rowLabel = buildString {
                                            append(header)
                                            if (currentGroup.isNotBlank()) append("·").append(currentGroup)
                                            if (actionName.isNotBlank()) append("·").append(actionName)
                                        }
                                        val rowValue = damageValue.ifBlank { itemText }
                                        if (rowLabel.isNotBlank() && rowValue.isNotBlank()) {
                                            expandedRows += DamageRow(rowLabel, rowValue, "", "")
                                        }
                                    }
                            }
                        }
                    }

                    expandedRows
                }

                else -> {
                    val text = valueCell.text().trim()
                    if (text.isBlank()) {
                        emptyList()
                    } else {
                        val label = buildString {
                            if (tablePrefix.isNotBlank()) append(tablePrefix).append("·")
                            append(header)
                        }
                        listOf(DamageRow(label, text, "", ""))
                    }
                }
            }
        }

        if (parsedRows.isNotEmpty()) return parsedRows

        return caption
            .takeIf { it.startsWith("补充：") }
            ?.removePrefix("补充：")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { listOf(DamageRow("补充", it, "", "")) }
            ?: emptyList()
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
                ?.let { it as? JsonArray }
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
            val url = "$API?action=parse&page=$encoded&prop=text&format=json"
            val body = WikiEngine.safeGet(url) ?: return emptyMap()
            val json = SharedJson.parseToJsonElement(body).jsonObject
            val html = json["parse"]?.jsonObject?.get("text")
                ?.jsonObject?.get("*")?.jsonPrimitive?.content ?: return emptyMap()
            parseCooldownTable(html, weaponName)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * 解析冷却时间表 HTML，提取指定道具在各模式下的冷却时间。
     */
    private fun parseCooldownTable(html: String, weaponName: String): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        if (html.isBlank()) return result
        val document = Jsoup.parse(html)
        document.select("table.klbqtable").forEach { table ->
            val modeName = table.selectFirst("caption")?.text()?.trim().orEmpty()
            if (modeName.isBlank()) return@forEach

            table.select("tr").drop(1).forEach { row ->
                val cells = row.select("> th, > td")
                    .map { it.text().trim() }
                    .filter { it.isNotBlank() }
                if (cells.size >= 2 && cells[0] == weaponName) {
                    cells[1].toIntOrNull()?.let { result[modeName] = it }
                }
            }
        }
        return result
    }

}
