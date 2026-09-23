package com.nekolaska.calabiyau.feature.weapon.detail

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.concurrent.ConcurrentHashMap
import util.buildParseUrl
import util.buildWikiUrl
import util.wikiPathEncode

/**
 * 武器详情 API（Android）。
 *
 * 通过 MediaWiki parse API 获取武器页面的 wikitext，
 * 解析 `{{武器|...}}` 和 `{{武器伤害|...}}` 模板参数。
 */
object WeaponDetailApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"

    private val detailCache = ConcurrentHashMap<String, WeaponDetail>()

    init {
        MemoryCacheRegistry.register("WeaponDetailApi") {
            detailCache.clear()
        }
    }

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
                if (!forceRefresh) {
                    detailCache[weaponName]?.let { return@withContext ApiResult.Success(it) }
                }

                val url = buildParseUrl(API, weaponName, "wikitext|text")

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

                detailCache[weaponName] = detail

                ApiResult.Success(
                    detail,
                    isOffline = result.isFromCache,
                    cacheAgeMs = result.ageMs
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ApiResult.Error("获取武器详情失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    internal fun parseWeaponWikitext(
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

        // 解析伤害数值模板。Wiki 已将 {{武器伤害}} 改名为 {{武器详细数据}}，
        // 距离伤害/基础伤害参数同时存在于主 {{武器}} 模板，读取时按 新模板 → 旧模板 → 主模板 回退。
        val damageContent = extractTemplate(wikitext, "武器伤害")
            ?: extractTemplate(wikitext, "武器详细数据")
        val damageParams = if (damageContent != null) parseTemplateParams(damageContent) else emptyMap()
        val allDamageParams: (String) -> String? = { key ->
            damageParams[key] ?: weaponParams[key]
        }

        // 构建伤害表
        val damageTable = mutableListOf<DamageRow>()

        // 1. 尝试从模板解析 (PC / 霰弹枪单值)。新页面距离参数可能带"伤害"后缀（如 10米头部伤害）。
        // 霰弹枪等武器没有"基础伤害"参数，但主模板直接带距离部位参数（10米头部等），同样进入本路径。
        val distances = listOf("10", "15", "20", "25", "30", "40", "50")
        val hasDistanceBodyParams = distances.any { d ->
            weaponParams.containsKey("${d}米头部") || weaponParams.containsKey("${d}米头部伤害")
        }
        if (damageParams.isNotEmpty() || weaponParams.containsKey("基础伤害") || hasDistanceBodyParams) {
            distances.forEach { d ->
                val head = allDamageParams("${d}米头部") ?: allDamageParams("${d}米头部伤害")
                val upper = allDamageParams("${d}米上肢") ?: allDamageParams("${d}米上肢伤害")
                val lower = allDamageParams("${d}米下肢") ?: allDamageParams("${d}米下肢伤害")
                if (head != null || upper != null || lower != null) {
                    damageTable.add(DamageRow("${d}米", head ?: "-", upper ?: "-", lower ?: "-"))
                    // 蓄力射击变体（霰弹枪拉栓：X米头部蓄力等）
                    val chargeHead = allDamageParams("${d}米头部蓄力")
                    val chargeUpper = allDamageParams("${d}米上肢蓄力")
                    val chargeLower = allDamageParams("${d}米下肢蓄力")
                    if (chargeHead != null || chargeUpper != null || chargeLower != null) {
                        damageTable.add(
                            DamageRow("${d}米·蓄力", chargeHead ?: "-", chargeUpper ?: "-", chargeLower ?: "-")
                        )
                    }
                    return@forEach
                }
                val pellet = allDamageParams("${d}米")
                if (!pellet.isNullOrBlank()) {
                    damageTable.add(DamageRow("${d}米", pellet, "", ""))
                }
            }
            distances.forEach { d ->
                val pellet = allDamageParams("移动端${d}米")
                if (!pellet.isNullOrBlank()) {
                    damageTable.add(DamageRow("移动端·${d}米", pellet, "", ""))
                    return@forEach
                }
                // 新一代页面移动端参数形态：10米头部移动端 / 10米上肢移动端 / 10米下肢移动端
                val mobileHead = allDamageParams("${d}米头部移动端")
                if (!mobileHead.isNullOrBlank()) {
                    damageTable.add(
                        DamageRow(
                            "移动端·${d}米",
                            mobileHead,
                            allDamageParams("${d}米上肢移动端") ?: "-",
                            allDamageParams("${d}米下肢移动端") ?: "-"
                        )
                    )
                }
            }
        }
        
        // 2. 尝试从 HTML 解析 (移动端/副武器/近战)
        val htmlDamages = parseWeaponDamageFromHtml(renderedHtml)
        if (htmlDamages.isNotEmpty()) {
            // 合并时去重，但由于移动端通常带有“移动端”前缀，故直接添加。
            // 谢幕曲等霰弹枪页面的"武器伤害"章节是性能参数键值表
            // （单发间隔/散布/换弹时间等），按黑名单排除以免污染伤害表；
            // 近战键值行（伤害/攻击距离/判定范围）与移动端行必须保留。
            val perfKeys = listOf(
                "单发间隔", "快速换弹", "空仓换弹", "拉栓时间",
                "散布", "衰减速度", "射速", "弹匣容量"
            )
            damageTable.addAll(
                htmlDamages.filter { h ->
                    perfKeys.none { k -> h.distance.contains(k) } &&
                        damageTable.none { it.distance == h.distance }
                }
            )
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
            baseDamage = allDamageParams("基础伤害") ?: "",
            headMultiplier = allDamageParams("头部倍率") ?: "",
            upperMultiplier = allDamageParams("上肢倍率") ?: "",
            lowerMultiplier = allDamageParams("下肢倍率") ?: "",
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
        // 依次尝试候选文件名（不同页面时代的命名格式不同），命中即返回
        for (fileName in extractWeaponImageFileNameCandidates(weaponName, wikitext)) {
            val cacheResult = OfflineCache.fetchWithCache(
                type = OfflineCache.Type.WEAPON_DETAIL,
                key = "image_$fileName",
                forceRefresh = forceRefresh
            ) { fetchImageUrl(fileName) }
            val url = cacheResult?.payload?.takeIf { it.isNotBlank() }
            if (url != null) return url
        }
        return null
    }

    /**
     * 候选文件名：显式"武器图片"参数优先；否则按模板类型合成。
     * 2026-09 起副武器/近战/战术道具图库采用「武器-<名>.png」前缀格式，
     * 主武器仍为「<名>-weapon.png」；两种格式互为回退候选。
     */
    private fun extractWeaponImageFileNameCandidates(weaponName: String, wikitext: String): List<String> {
        val templateNames = listOf("武器-近战武器", "武器-副武器", "武器-战术道具", "武器")
        for (tpl in templateNames) {
            val content = extractTemplate(wikitext, tpl) ?: continue
            val params = parseTemplateParams(content)
            val explicitImage = params["武器图片"].orEmpty()
            return if (explicitImage.isNotBlank()) {
                listOf(explicitImage)
            } else if (tpl == "武器") {
                listOf("${weaponName}-weapon.png", "武器-$weaponName.png")
            } else {
                listOf("武器-$weaponName.png", "${weaponName}-weapon.png")
            }
        }
        return emptyList()
    }

    /** Wiki 将"武器伤害"章节改名为"武器详细数据"，两个标题都要能定位。 */
    private val damageSectionHeadings = setOf("武器伤害", "武器详细数据")

    private fun parseWeaponDamageFromHtml(html: String): List<DamageRow> {
        if (html.isBlank()) return emptyList()
        val document = Jsoup.parse(html)
        val headline = damageSectionHeadings.firstNotNullOfOrNull { document.getElementById(it) }
            ?: document.select("span.mw-headline").firstOrNull { it.text().trim() in damageSectionHeadings }?.parent()
            ?: return emptyList()
        val sectionStart = headline.parent()?.takeIf { it.tagName().matches(Regex("h[1-6]")) } ?: headline
        val sectionElements = generateSequence(sectionStart.nextElementSibling()) { it.nextElementSibling() }
            .takeWhile { !it.tagName().matches(Regex("h[1-6]")) }
            .toList()

        return sectionElements
            .filter { it.tagName() == "table" && it.hasClass("klbqtable") }
            .flatMap { table -> parseDamageTable(table) }
    }

    private fun parseDamageTable(table: Element): List<DamageRow> {
        val caption = table.selectFirst("caption")?.text()?.trim().orEmpty()
        // 重焰等副武器页面的"武器部位伤害系数"表是倍率说明，不是伤害值，整体排除
        if (table.text().contains("武器部位伤害系数")) return emptyList()
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

        // 转置表（重焰等）：首行为距离列（10米/15米/20米），后续行首为部位名。
        // wiki 源码用 "|  | 10米 || 15米" 语法，距离列渲染为 td 而非 th
        val transposedHeader = rows.firstOrNull()?.select("> th, > td") ?: emptyList()
        if (transposedHeader.count { it.text().trim().matches(Regex("\\d+米")) } >= 2) {
            return parseTransposedDamageTable(table)
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

    /**
     * 转置伤害表（重焰等副武器）：
     * ```
     * |      | 10米 | 15米 | 20米 |
     * | 头部 | 85   | 49   | 36   |
     * | 上身 | 85   | 49   | 36   |
     * ```
     * 列为距离、行为部位，且部位名用"上身/下身"而非"上肢/下肢"。
     */
    private fun parseTransposedDamageTable(table: Element): List<DamageRow> {
        val rows = table.select("tr")
        if (rows.isEmpty()) return emptyList()

        val distances = (rows.firstOrNull() ?: return emptyList())
            .select("> th, > td")
            .map { it.text().trim() }
            .filter { it.matches(Regex("\\d+米")) }
        if (distances.isEmpty()) return emptyList()

        val valuesByPart = mutableMapOf<String, List<String>>()
        rows.drop(1).forEach { row ->
            val cells = row.select("> th, > td")
            if (cells.size < 2) return@forEach
            val part = normalizeBodyPart(cells.firstOrNull()?.text()?.trim().orEmpty())
            if (part.isBlank()) return@forEach
            valuesByPart[part] = cells.drop(1).map { it.text().trim() }
        }

        val headValues = valuesByPart["头部"]
        val upperValues = valuesByPart["上肢"] ?: valuesByPart["上身"]
        val lowerValues = valuesByPart["下肢"] ?: valuesByPart["下身"]
        if (headValues == null && upperValues == null && lowerValues == null) return emptyList()

        return distances.mapIndexed { index, distance ->
            DamageRow(
                distance = distance,
                head = headValues?.getOrNull(index).orEmpty().ifBlank { "-" },
                upper = upperValues?.getOrNull(index).orEmpty().ifBlank { "-" },
                lower = lowerValues?.getOrNull(index).orEmpty().ifBlank { "-" }
            )
        }
    }

    private fun normalizeBodyPart(raw: String): String = when (raw) {
        "上身" -> "上肢"
        "下身" -> "下肢"
        else -> raw
    }

    private fun parseDistanceDamageTable(table: Element, caption: String): List<DamageRow> {
        val isMobile = caption.contains("移动端")
        val prefix = if (isMobile) "移动端·" else ""

        return table.select("tr").mapNotNull { row ->
            val header = row.selectFirst("th")?.text()?.trim().orEmpty()
            val valueCells = row.select("td")
            if (!header.matches(Regex("\\d+米")) || valueCells.isEmpty()) return@mapNotNull null

            if (valueCells.size == 1) {
                // 移动端单值表（重焰等）：| 10米 | 85 —— 全弹丸总伤
                DamageRow(distance = "$prefix$header", head = valueCells[0].text().trim(), upper = "", lower = "")
            } else {
                DamageRow(
                    distance = "$prefix$header",
                    head = valueCells.getOrNull(0)?.text()?.trim().orEmpty(),
                    upper = valueCells.getOrNull(1)?.text()?.trim().orEmpty(),
                    lower = valueCells.getOrNull(2)?.text()?.trim().orEmpty()
                )
            }
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
                    val enc = pageTitle.wikiPathEncode()
                    pages.add(SubPage(pageTitle, displayName, "https://wiki.biligame.com/klbq/$enc"))
                }
            }
        }
        return pages
    }

    /** 获取图片 URL */
    private fun fetchImageUrl(fileName: String): String? {
        return try {
            val url = buildWikiUrl(API, "action" to "query", "titles" to "文件:$fileName", "prop" to "imageinfo", "iiprop" to "url", "redirects" to "1", "format" to "json")
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
            val url = buildParseUrl(API, "战术道具冷却时间表", "text")
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
